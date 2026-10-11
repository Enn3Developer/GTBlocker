package com.enn3developer.gtblocker.server;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.compat.voice.PartyVoice;
import com.enn3developer.gtblocker.data.BlockerState;
import com.enn3developer.gtblocker.data.DataStore;
import com.enn3developer.gtblocker.data.HistoryEntry;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.data.Spot;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.lock.LockTable;
import com.enn3developer.gtblocker.lock.Locks;
import com.enn3developer.gtblocker.net.Codec;
import com.enn3developer.gtblocker.net.Network;
import com.enn3developer.gtblocker.net.OpenGuiMessage;
import com.enn3developer.gtblocker.net.StateMessage;
import com.enn3developer.gtblocker.text.Chat;
import com.mojang.authlib.GameProfile;

/**
 * Server side of GTBlocker for one server run: owns the persisted state, applies admin actions, runs the party
 * scheduler and broadcasts changes. Every method runs on the server thread; packets from the Netty thread come in
 * through {@link #enqueue}.
 * <p>
 * Permission model: admins are this mod's own UUID list, OP grants nothing. The console (and RCON) may only add the
 * first admin, while the list is empty; after that admins manage everything, including each other. In
 * singleplayer/LAN the host is always an admin, since there is no console to bootstrap from.
 */
public final class BlockerServer {

    private static final int HISTORY_SENT = 200;
    private static final int MAX_MESSAGE = 120;
    private static final int MAX_PARTY_ITEMS = 64;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    private static volatile BlockerServer instance;

    private final MinecraftServer server;
    private final DataStore store;
    private final BlockerState state;
    private final Queue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Set<Integer>> remindersSent = new HashMap<>();
    private final Random random = new Random();
    private final @Nullable PartyVoice voice;
    private final Set<UUID> voiceOptOut = new HashSet<>();
    private boolean voiceOpen;
    private long voiceOpenUntil;
    private LockTable awaitingFirstCraft = LockTable.EMPTY;

    private BlockerServer(MinecraftServer server, DataStore store, BlockerState state) {
        this.server = server;
        this.store = store;
        this.state = state;
        this.voice = PartyVoice.create();
    }

    public static @Nullable BlockerServer get() {
        return instance;
    }

    /** Loads the world's state and makes its locks live. Called from {@code FMLServerStartingEvent}. */
    public static void start(MinecraftServer server) {
        File worldDir = server.worldServerForDimension(0)
            .getSaveHandler()
            .getWorldDirectory();
        DataStore store = new DataStore(worldDir);
        BlockerState state;
        try {
            state = store.load();
        } catch (IOException e) {
            // Refusing to start beats running with an empty lock list and then overwriting the real one on save.
            throw new IllegalStateException(
                "GTBlocker: cannot read " + store.path() + ", fix or remove it (it is plain JSON)",
                e);
        }
        BlockerServer blocker = new BlockerServer(server, store, state);
        ServerStatsImport.importOnce(state);
        blocker.awakenSchedule();
        blocker.applyLocks();
        blocker.save();
        instance = blocker;
        GTBlocker.LOG.info(
            "Loaded {} locks, {} parties, {} admins from {}",
            state.locks.size(),
            state.parties.size(),
            state.admins.size(),
            store.path());
        if (state.admins.isEmpty() && server.isDedicatedServer()) {
            GTBlocker.LOG.warn("No GTBlocker admins yet: run 'gtblocker admin add <player>' from the console");
        }
    }

    public static void stop() {
        BlockerServer blocker = instance;
        instance = null;
        if (blocker != null) {
            blocker.shutdown();
        }
        Locks.setServer(LockTable.EMPTY);
    }

    private void shutdown() {
        long now = now();
        for (EntityPlayerMP player : players()) {
            state.lastSeen.put(player.getUniqueID(), now);
        }
        if (voice != null && voiceOpen) {
            voice.leaveAll();
        }
        save();
    }

    public void enqueue(Runnable task) {
        inbox.add(task);
    }

    // region permissions

    public boolean isAdmin(EntityPlayerMP player) {
        return state.admins.containsKey(player.getUniqueID()) || isSingleplayerHost(player);
    }

    private boolean isSingleplayerHost(EntityPlayerMP player) {
        return !server.isDedicatedServer() && player.getCommandSenderName()
            .equals(server.getServerOwner());
    }

    public static boolean isConsole(ICommandSender sender) {
        return sender instanceof MinecraftServer || sender instanceof RConConsoleSource;
    }

    // endregion

    // region actions

    public Result lock(LockKey key, String actor) {
        if (key.item() == null) {
            return Result.err("gtblocker.err.unknown_item", key.serialize());
        }
        if (state.locks.contains(key)) {
            return Result.err("gtblocker.err.already_locked", Chat.item(key));
        }
        state.locks.add(key);
        commit();
        NBTTagCompound event = new NBTTagCompound();
        event.setTag("items", Codec.writeKeys(List.of(key)));
        event.setString("by", actor);
        Network.broadcastEvent("locked", event);
        GTBlocker.LOG.info("{} locked {}", actor, key);
        return Result.ok("gtblocker.ok.locked", Chat.item(key));
    }

    /** Swaps a lock for another key of the same item (exact meta vs any meta) without touching history. */
    public Result relock(LockKey from, LockKey to, String actor) {
        if (!state.locks.contains(from)) {
            return Result.err("gtblocker.err.not_locked", Chat.item(from));
        }
        if (!from.itemId()
            .equals(to.itemId()) || state.locks.contains(to)) {
            return Result.err("gtblocker.err.bad_relock");
        }
        List<LockKey> reordered = new ArrayList<>(state.locks);
        reordered.set(reordered.indexOf(from), to);
        state.locks.clear();
        state.locks.addAll(reordered);
        replaceInParties(from, to);
        commit();
        GTBlocker.LOG.info("{} changed lock {} to {}", actor, from, to);
        return Result.ok("gtblocker.ok.relocked", Chat.item(to));
    }

    /** Unlocks right away, with no celebration and no first-craft race. */
    public Result unlockQuiet(Collection<LockKey> keys, String actor) {
        List<LockKey> removed = keys.stream()
            .filter(state.locks::contains)
            .distinct()
            .collect(Collectors.toList());
        if (removed.isEmpty()) {
            return Result.err("gtblocker.err.nothing_to_unlock");
        }
        state.locks.removeAll(removed);
        removed.forEach(this::dropFromParties);
        long now = now();
        removed.forEach(key -> state.history.add(new HistoryEntry(key, now, actor, 0, null, 0)));
        commit();
        NBTTagCompound event = new NBTTagCompound();
        event.setTag("items", Codec.writeKeys(removed));
        event.setString("by", actor);
        Network.broadcastEvent("unlocked", event);
        GTBlocker.LOG.info("{} quietly unlocked {}", actor, removed);
        return Result.ok("gtblocker.ok.unlocked", Chat.items(removed));
    }

    /**
     * Schedules a party. A time at or before "now + countdown" means a live party with the standard countdown.
     * Only currently locked items that aren't already in another party can join.
     */
    public Result scheduleParty(Collection<LockKey> keys, long revealAt, String message, @Nullable Spot spot,
        String actor) {
        List<LockKey> items = new ArrayList<>();
        for (LockKey key : new LinkedHashSet<>(keys)) {
            if (!state.locks.contains(key)) {
                return Result.err("gtblocker.err.not_locked", Chat.item(key));
            }
            Party other = partyOf(key);
            if (other != null) {
                return Result.err("gtblocker.err.in_party", Chat.item(key), other.id());
            }
            items.add(key);
        }
        if (items.isEmpty()) {
            return Result.err("gtblocker.err.party_empty");
        }
        if (items.size() > MAX_PARTY_ITEMS) {
            return Result.err("gtblocker.err.party_too_big", MAX_PARTY_ITEMS);
        }
        long earliest = now() + Config.partyCountdownSeconds * 1000L;
        Party party = new Party(
            state.nextPartyId++,
            List.copyOf(items),
            Math.max(revealAt, earliest),
            cleanMessage(message),
            spot,
            actor);
        state.parties.add(party);
        skipPassedReminders(party);
        commit();
        NBTTagCompound event = new NBTTagCompound();
        event.setTag("party", Codec.writeParty(party));
        Network.broadcastEvent("party_scheduled", event);
        GTBlocker.LOG.info(
            "{} scheduled party #{} for {}: {}",
            actor,
            party.id(),
            LOG_TIME.format(Instant.ofEpochMilli(party.revealAt())),
            party.items());
        return Result.ok("gtblocker.ok.party_scheduled", party.id());
    }

    /** Pulls a scheduled party forward to "now + countdown". */
    public Result startPartyNow(int id, String actor) {
        Party party = party(id);
        if (party == null) {
            return Result.err("gtblocker.err.no_party", id);
        }
        long soon = now() + Config.partyCountdownSeconds * 1000L;
        if (party.revealAt() > soon) {
            replaceParty(party, party.withRevealAt(soon));
            remindersSent.computeIfAbsent(id, i -> new HashSet<>())
                .addAll(intSet(Config.reminderMinutes));
            commit();
            NBTTagCompound event = new NBTTagCompound();
            event.setInteger("id", id);
            event.setString("by", actor);
            Network.broadcastEvent("party_starting", event);
        }
        GTBlocker.LOG.info("{} started party #{} now", actor, id);
        return Result.ok("gtblocker.ok.party_started", id);
    }

    public Result cancelParty(int id, String actor) {
        Party party = party(id);
        if (party == null) {
            return Result.err("gtblocker.err.no_party", id);
        }
        state.parties.remove(party);
        remindersSent.remove(id);
        commit();
        NBTTagCompound event = new NBTTagCompound();
        event.setInteger("id", id);
        event.setTag("items", Codec.writeKeys(party.items()));
        event.setString("by", actor);
        Network.broadcastEvent("party_cancelled", event);
        GTBlocker.LOG.info("{} cancelled party #{}", actor, id);
        return Result.ok("gtblocker.ok.party_cancelled", id);
    }

    public Result setPartySpot(int id, @Nullable Spot spot) {
        Party party = party(id);
        if (party == null) {
            return Result.err("gtblocker.err.no_party", id);
        }
        replaceParty(party, party.withSpot(spot));
        commit();
        return spot == null ? Result.ok("gtblocker.ok.spot_cleared", id)
            : Result.ok("gtblocker.ok.spot_set", id, spot.x(), spot.y(), spot.z());
    }

    /**
     * Adds an admin by name. The console may only do this while there are no admins; players must be admins.
     * Offline players work as long as the server knows their name (usercache or Mojang lookup).
     */
    public Result addAdmin(String name, ICommandSender actor) {
        if (!mayManageAdmins(actor, true)) {
            return Result.err(
                isConsole(actor) ? "gtblocker.err.console_bootstrap_only" : "gtblocker.err.not_admin");
        }
        GameProfile profile = server.func_152358_ax()
            .func_152655_a(name);
        if (profile == null || profile.getId() == null) {
            return Result.err("gtblocker.err.unknown_player", name);
        }
        if (state.admins.containsKey(profile.getId())) {
            return Result.err("gtblocker.err.already_admin", profile.getName());
        }
        state.admins.put(profile.getId(), profile.getName());
        commit();
        GTBlocker.LOG.info("{} made {} a GTBlocker admin", actor.getCommandSenderName(), profile.getName());
        EntityPlayerMP target = online(profile.getId());
        if (target != null && target != actor) {
            target.addChatMessage(Chat.ok("gtblocker.ok.you_are_admin", actor.getCommandSenderName()));
        }
        return Result.ok("gtblocker.ok.admin_added", profile.getName());
    }

    public Result removeAdmin(String name, ICommandSender actor) {
        if (!mayManageAdmins(actor, false)) {
            return Result.err(
                isConsole(actor) ? "gtblocker.err.console_bootstrap_only" : "gtblocker.err.not_admin");
        }
        UUID match = state.admins.entrySet()
            .stream()
            .filter(
                entry -> entry.getValue()
                    .equalsIgnoreCase(name)
                    || entry.getKey()
                        .toString()
                        .equalsIgnoreCase(name))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        if (match == null) {
            return Result.err("gtblocker.err.not_an_admin", name);
        }
        String removedName = state.admins.remove(match);
        commit();
        GTBlocker.LOG.info("{} removed GTBlocker admin {}", actor.getCommandSenderName(), removedName);
        return Result.ok("gtblocker.ok.admin_removed", removedName);
    }

    private boolean mayManageAdmins(ICommandSender actor, boolean adding) {
        if (actor instanceof EntityPlayerMP player) {
            return isAdmin(player);
        }
        return adding && isConsole(actor) && state.admins.isEmpty();
    }

    public void setVoice(EntityPlayerMP player, boolean join) {
        if (voice == null || !voiceOpen) {
            player.addChatMessage(Chat.err("gtblocker.err.no_party_voice"));
            return;
        }
        UUID uuid = player.getUniqueID();
        NBTTagCompound event = new NBTTagCompound();
        if (join) {
            voiceOptOut.remove(uuid);
            voice.join(uuid);
            event.setString("state", "joined");
        } else {
            voiceOptOut.add(uuid);
            voice.leave(uuid);
            event.setString("state", "left");
        }
        Network.sendEvent(player, "voice", event);
        sendState(player);
    }

    /** Dispatches a GUI action. Every mutating action re-checks admin status here, whatever the GUI showed. */
    public void handleAction(EntityPlayerMP player, NBTTagCompound tag) {
        String action = tag.getString("a");
        if (action.equals("voice")) {
            setVoice(player, tag.getBoolean("join"));
            return;
        }
        if (!isAdmin(player)) {
            player.addChatMessage(Chat.err("gtblocker.err.not_admin"));
            return;
        }
        String actor = player.getCommandSenderName();
        Result result = switch (action) {
            case "lock" -> withKey(tag.getString("item"), key -> lock(key, actor));
            case "relock" -> withKey(
                tag.getString("from"),
                from -> withKey(tag.getString("to"), to -> relock(from, to, actor)));
            case "unlock" -> unlockQuiet(Codec.readKeys(tag, "items"), actor);
            case "party" -> scheduleParty(
                Codec.readKeys(tag, "items"),
                tag.getLong("at"),
                tag.getString("msg"),
                tag.getBoolean("spot") ? spotOf(player) : null,
                actor);
            case "party_start" -> startPartyNow(tag.getInteger("id"), actor);
            case "party_cancel" -> cancelParty(tag.getInteger("id"), actor);
            case "party_spot" -> setPartySpot(tag.getInteger("id"), tag.getBoolean("clear") ? null : spotOf(player));
            case "admin_add" -> addAdmin(tag.getString("name"), player);
            case "admin_remove" -> removeAdmin(tag.getString("name"), player);
            default -> null;
        };
        if (result != null) {
            player.addChatMessage(result.message());
        }
    }

    private static Result withKey(String text, java.util.function.Function<LockKey, Result> action) {
        LockKey key = LockKey.parse(text);
        return key == null ? Result.err("gtblocker.err.bad_item", text) : action.apply(key);
    }

    public static Spot spotOf(EntityPlayerMP player) {
        return new Spot(
            player.dimension,
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ));
    }

    public void openGui(EntityPlayerMP player) {
        sendState(player);
        Network.CHANNEL.sendTo(new OpenGuiMessage(), player);
    }

    // endregion

    // region queries (commands)

    public Set<LockKey> locks() {
        return state.locks;
    }

    public List<Party> parties() {
        return state.parties;
    }

    public List<HistoryEntry> history() {
        return state.history;
    }

    public Map<UUID, String> admins() {
        return state.admins;
    }

    // endregion

    // region lifecycle hooks

    public void tick() {
        Runnable task;
        while ((task = inbox.poll()) != null) {
            task.run();
        }
        long now = now();
        if (!state.parties.isEmpty()) {
            for (Party party : new ArrayList<>(state.parties)) {
                long left = party.revealAt() - now;
                if (left <= 0) {
                    reveal(party);
                    continue;
                }
                Set<Integer> sent = remindersSent.computeIfAbsent(party.id(), id -> new HashSet<>());
                for (int minutes : Config.reminderMinutes) {
                    if (left <= minutes * 60_000L && sent.add(minutes)) {
                        NBTTagCompound event = new NBTTagCompound();
                        event.setInteger("id", party.id());
                        event.setInteger("minutes", minutes);
                        Network.broadcastEvent("party_reminder", event);
                    }
                }
            }
        }
        updateVoice(now);
    }

    public void onLogin(EntityPlayerMP player) {
        UUID uuid = player.getUniqueID();
        String known = state.admins.get(uuid);
        if (known != null && !known.equals(player.getCommandSenderName())) {
            state.admins.put(uuid, player.getCommandSenderName());
        }
        Long lastSeen = state.lastSeen.get(uuid);
        state.lastSeen.put(uuid, now());
        save();
        sendState(player);
        if (lastSeen != null) {
            List<LockKey> missed = state.history.stream()
                .filter(entry -> entry.unlockedAt() > lastSeen)
                .map(HistoryEntry::key)
                .distinct()
                .collect(Collectors.toList());
            if (!missed.isEmpty()) {
                NBTTagCompound event = new NBTTagCompound();
                event.setTag("items", Codec.writeKeys(missed));
                Network.sendEvent(player, "away", event);
            }
        }
        if (voice != null && voiceOpen && !voiceOptOut.contains(uuid)) {
            voice.join(uuid);
            NBTTagCompound event = new NBTTagCompound();
            event.setString("state", "joined");
            Network.sendEvent(player, "voice", event);
        }
    }

    public void onLogout(EntityPlayerMP player) {
        state.lastSeen.put(player.getUniqueID(), now());
        if (voice != null && voiceOpen) {
            voice.leave(player.getUniqueID());
        }
        save();
    }

    /** Cheap pre-check for machine hooks, so they skip owner lookups while no unlock awaits its first craft. */
    public boolean awaitsFirstCraft() {
        return !awaitingFirstCraft.isEmpty();
    }

    /**
     * A real player crafted, or a player-owned machine started, something. The first such craft of an item a party
     * unlocked gets announced to everyone.
     */
    public void onCrafted(String playerName, @Nullable ItemStack... outputs) {
        if (awaitingFirstCraft.isEmpty() || outputs == null) {
            return;
        }
        for (ItemStack output : outputs) {
            LockKey key = awaitingFirstCraft.keyFor(output);
            if (key == null) {
                continue;
            }
            long now = now();
            for (int i = state.history.size() - 1; i >= 0; i--) {
                HistoryEntry entry = state.history.get(i);
                if (entry.key()
                    .equals(key) && entry.awaitingFirstCraft()) {
                    state.history.set(i, entry.withFirstCraft(playerName, now));
                    break;
                }
            }
            commit();
            NBTTagCompound event = new NBTTagCompound();
            event.setString("item", key.serialize());
            ItemStack single = output.copy();
            single.stackSize = 1;
            event.setTag("stack", single.writeToNBT(new NBTTagCompound()));
            event.setString("player", playerName);
            Network.broadcastEvent("first_craft", event);
            GTBlocker.LOG.info("First craft of {} after its unlock: {}", key, playerName);
            return;
        }
    }

    // endregion

    // region internals

    private void reveal(Party party) {
        state.parties.remove(party);
        remindersSent.remove(party.id());
        List<LockKey> unlocked = party.items()
            .stream()
            .filter(state.locks::contains)
            .collect(Collectors.toList());
        if (unlocked.isEmpty()) {
            commit();
            GTBlocker.LOG.info("Party #{} had nothing left to unlock", party.id());
            return;
        }
        state.locks.removeAll(unlocked);
        long now = now();
        unlocked.forEach(key -> state.history.add(new HistoryEntry(key, now, party.createdBy(), party.id(), null, 0)));
        // State first: by the time clients play the reveal, their mirror already lets them craft.
        commit();
        NBTTagCompound event = new NBTTagCompound();
        event.setInteger("id", party.id());
        event.setTag("items", Codec.writeKeys(unlocked));
        if (party.spot() != null) {
            event.setTag("spot", Codec.writeSpot(party.spot()));
        }
        event.setLong("seed", random.nextLong());
        event.setString("msg", party.message());
        event.setString("by", party.createdBy());
        Network.broadcastEvent("reveal", event);
        voiceOpenUntil = Math.max(voiceOpenUntil, now + Config.voiceAfterMinutes * 60_000L);
        GTBlocker.LOG.info("Party #{} revealed: {}", party.id(), unlocked);
    }

    private void updateVoice(long now) {
        if (voice == null) {
            return;
        }
        boolean shouldBeOpen = now < voiceOpenUntil;
        if (!shouldBeOpen) {
            long lead = Config.voiceLeadMinutes * 60_000L;
            for (Party party : state.parties) {
                if (party.revealAt() - now <= lead) {
                    shouldBeOpen = true;
                    break;
                }
            }
        }
        if (shouldBeOpen == voiceOpen) {
            return;
        }
        voiceOpen = shouldBeOpen;
        NBTTagCompound event = new NBTTagCompound();
        if (voiceOpen) {
            event.setString("state", "joined");
            for (EntityPlayerMP player : players()) {
                if (!voiceOptOut.contains(player.getUniqueID())) {
                    voice.join(player.getUniqueID());
                    Network.sendEvent(player, "voice", event);
                }
            }
        } else {
            voice.leaveAll();
            voiceOptOut.clear();
            event.setString("state", "ended");
            Network.broadcastEvent("voice", event);
        }
        broadcastState();
    }

    /** After a load: reminders already past are not re-sent, and overdue parties reveal on the first tick. */
    private void awakenSchedule() {
        state.parties.forEach(this::skipPassedReminders);
    }

    private void skipPassedReminders(Party party) {
        long left = party.revealAt() - now();
        Set<Integer> sent = remindersSent.computeIfAbsent(party.id(), id -> new HashSet<>());
        for (int minutes : Config.reminderMinutes) {
            if (minutes * 60_000L >= left) {
                sent.add(minutes);
            }
        }
    }

    private void commit() {
        applyLocks();
        int excess = state.history.size() - Config.historyLimit;
        if (excess > 0) {
            state.history.subList(0, excess)
                .clear();
        }
        save();
        broadcastState();
    }

    private void applyLocks() {
        LockTable previous = Locks.server();
        LockTable table = LockTable.of(state.locks);
        Locks.setServer(table);
        if (!table.keys()
            .equals(previous.keys())) {
            refreshCraftingGrids();
        }
        awaitingFirstCraft = LockTable.of(
            state.history.stream()
                .filter(HistoryEntry::awaitingFirstCraft)
                .map(HistoryEntry::key)
                .collect(Collectors.toList()));
    }

    /**
     * Grids only re-match when their contents change, so a player waiting at a filled crafting table would see no
     * result after the reveal (or keep a now-locked one). Re-running the match fixes both; the container syncs the
     * result slot itself.
     */
    private void refreshCraftingGrids() {
        for (EntityPlayerMP player : players()) {
            refreshGrids(player.openContainer);
            if (player.openContainer != player.inventoryContainer) {
                refreshGrids(player.inventoryContainer);
            }
        }
    }

    private static void refreshGrids(@Nullable Container container) {
        if (container == null) {
            return;
        }
        Set<InventoryCrafting> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object object : container.inventorySlots) {
            if (((Slot) object).inventory instanceof InventoryCrafting grid && grids.add(grid)) {
                try {
                    container.onCraftMatrixChanged(grid);
                } catch (RuntimeException e) {
                    GTBlocker.LOG.debug("Could not refresh crafting grid of {}", container.getClass(), e);
                }
            }
        }
    }

    private void save() {
        try {
            store.save(state);
        } catch (IOException e) {
            GTBlocker.LOG.error("Could not save {}", store.path(), e);
        }
    }

    private void broadcastState() {
        NBTTagCompound common = stateTag();
        for (EntityPlayerMP player : players()) {
            sendState(player, common);
        }
    }

    public void sendState(EntityPlayerMP player) {
        sendState(player, stateTag());
    }

    private void sendState(EntityPlayerMP player, NBTTagCompound common) {
        NBTTagCompound tag = (NBTTagCompound) common.copy();
        tag.setBoolean("admin", isAdmin(player));
        tag.setBoolean("voice", voice != null && voiceOpen && !voiceOptOut.contains(player.getUniqueID()));
        tag.setBoolean("voiceOpen", voice != null && voiceOpen);
        Network.CHANNEL.sendTo(new StateMessage(tag), player);
    }

    private NBTTagCompound stateTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("locks", Codec.writeKeys(state.locks));
        NBTTagList parties = new NBTTagList();
        state.parties.forEach(party -> parties.appendTag(Codec.writeParty(party)));
        tag.setTag("parties", parties);
        NBTTagList history = new NBTTagList();
        int from = Math.max(0, state.history.size() - HISTORY_SENT);
        state.history.subList(from, state.history.size())
            .forEach(entry -> history.appendTag(Codec.writeHistory(entry)));
        tag.setTag("history", history);
        NBTTagList admins = new NBTTagList();
        state.admins.forEach((uuid, name) -> {
            NBTTagCompound admin = new NBTTagCompound();
            admin.setString("uuid", uuid.toString());
            admin.setString("name", name);
            admins.appendTag(admin);
        });
        tag.setTag("admins", admins);
        tag.setLong("now", now());
        tag.setInteger("countdown", Config.partyCountdownSeconds);
        return tag;
    }

    private @Nullable Party party(int id) {
        for (Party party : state.parties) {
            if (party.id() == id) {
                return party;
            }
        }
        return null;
    }

    private @Nullable Party partyOf(LockKey key) {
        for (Party party : state.parties) {
            if (party.items()
                .contains(key)) {
                return party;
            }
        }
        return null;
    }

    private void replaceParty(Party old, Party updated) {
        state.parties.set(state.parties.indexOf(old), updated);
    }

    private void dropFromParties(LockKey key) {
        for (Party party : new ArrayList<>(state.parties)) {
            if (!party.items()
                .contains(key)) {
                continue;
            }
            List<LockKey> rest = new ArrayList<>(party.items());
            rest.remove(key);
            if (rest.isEmpty()) {
                state.parties.remove(party);
                remindersSent.remove(party.id());
            } else {
                replaceParty(party, party.withItems(rest));
            }
        }
    }

    private void replaceInParties(LockKey from, LockKey to) {
        for (Party party : new ArrayList<>(state.parties)) {
            int index = party.items()
                .indexOf(from);
            if (index >= 0) {
                List<LockKey> items = new ArrayList<>(party.items());
                items.set(index, to);
                replaceParty(party, party.withItems(items));
            }
        }
    }

    private static String cleanMessage(String message) {
        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(message == null ? "" : message)
            .trim();
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }

    private static Set<Integer> intSet(int[] values) {
        Set<Integer> set = new HashSet<>();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    private List<EntityPlayerMP> players() {
        return new ArrayList<>(server.getConfigurationManager().playerEntityList);
    }

    private @Nullable EntityPlayerMP online(UUID uuid) {
        for (EntityPlayerMP player : players()) {
            if (player.getUniqueID()
                .equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // endregion

    /** Outcome of an action, as the chat line sent back to whoever triggered it. */
    public record Result(boolean success, IChatComponent message) {

        static Result ok(String key, Object... args) {
            return new Result(true, Chat.ok(key, args));
        }

        static Result err(String key, Object... args) {
            return new Result(false, Chat.err(key, args));
        }
    }
}
