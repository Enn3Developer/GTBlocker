package com.enn3developer.gtblocker.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.data.HistoryEntry;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.BlockedCraftMemo;
import com.enn3developer.gtblocker.lock.CraftingGrids;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.lock.LockTable;
import com.enn3developer.gtblocker.lock.Locks;
import com.enn3developer.gtblocker.net.Codec;

/**
 * The client's copy of the server state, replaced wholesale by every snapshot. Client main thread only; the lock
 * table itself goes through {@link Locks#setClient} so the crafting hooks see it.
 */
public final class ClientState {

    private static List<LockKey> locks = List.of();
    private static List<Party> parties = List.of();
    private static Map<LockKey, Party> partyByKey = Map.of();
    private static List<HistoryEntry> history = List.of();
    private static List<String> admins = List.of();
    private static boolean synced;
    private static boolean admin;
    private static boolean voice;
    private static boolean voiceOpen;
    private static long clockOffset;
    private static int countdownSeconds = 10;
    private static int version;

    private ClientState() {}

    static void apply(NBTTagCompound tag) {
        List<LockKey> previous = locks;
        locks = List.copyOf(Codec.readKeys(tag, "locks"));
        Locks.setClient(LockTable.of(locks));
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (!locks.equals(previous)) {
            // Re-recorded by the refresh for grids still blocked; other entries would only go stale.
            BlockedCraftMemo.clear();
            if (player != null) {
                CraftingGrids.refresh(player);
            }
        }

        NBTTagList partyList = tag.getTagList("parties", Constants.NBT.TAG_COMPOUND);
        List<Party> newParties = new ArrayList<>();
        for (int i = 0; i < partyList.tagCount(); i++) {
            newParties.add(Codec.readParty(partyList.getCompoundTagAt(i)));
        }
        newParties.sort(Comparator.comparingLong(Party::revealAt));
        parties = List.copyOf(newParties);
        Map<LockKey, Party> byKey = new HashMap<>();
        for (Party party : parties) {
            party.items()
                .forEach(key -> byKey.putIfAbsent(key, party));
        }
        partyByKey = byKey;

        NBTTagList historyList = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        List<HistoryEntry> newHistory = new ArrayList<>();
        for (int i = 0; i < historyList.tagCount(); i++) {
            HistoryEntry entry = Codec.readHistory(historyList.getCompoundTagAt(i));
            if (entry != null) {
                newHistory.add(entry);
            }
        }
        history = List.copyOf(newHistory);

        NBTTagList adminList = tag.getTagList("admins", Constants.NBT.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < adminList.tagCount(); i++) {
            names.add(
                adminList.getCompoundTagAt(i)
                    .getString("name"));
        }
        admins = List.copyOf(names);

        admin = tag.getBoolean("admin");
        voice = tag.getBoolean("voice");
        voiceOpen = tag.getBoolean("voiceOpen");
        countdownSeconds = tag.getInteger("countdown");
        // Ignores latency; a few hundred milliseconds of skew doesn't matter for a countdown.
        clockOffset = tag.getLong("now") - System.currentTimeMillis();
        synced = true;
        version++;
    }

    static void reset() {
        locks = List.of();
        parties = List.of();
        partyByKey = Map.of();
        history = List.of();
        admins = List.of();
        synced = false;
        admin = false;
        voice = false;
        voiceOpen = false;
        clockOffset = 0;
        version++;
        Locks.setClient(LockTable.EMPTY);
        BlockedCraftMemo.clear();
    }

    /** True once the server sent a snapshot for this connection. */
    public static boolean synced() {
        return synced;
    }

    /** Bumped on every snapshot, so screens can tell when to rebuild. */
    public static int version() {
        return version;
    }

    public static List<LockKey> locks() {
        return locks;
    }

    /** Scheduled parties, soonest first. */
    public static List<Party> parties() {
        return parties;
    }

    /** The most recent unlocks, oldest first. */
    public static List<HistoryEntry> history() {
        return history;
    }

    public static List<String> admins() {
        return admins;
    }

    public static boolean isAdmin() {
        return admin;
    }

    /** True while this player is in the party voice group. */
    public static boolean inVoice() {
        return voice;
    }

    public static boolean voiceOpen() {
        return voiceOpen;
    }

    public static int countdownSeconds() {
        return countdownSeconds;
    }

    /** The server's wall clock, estimated from the last snapshot. Party times are on this clock. */
    public static long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    public static @Nullable Party party(int id) {
        for (Party party : parties) {
            if (party.id() == id) {
                return party;
            }
        }
        return null;
    }

    public static @Nullable Party partyFor(LockKey key) {
        return partyByKey.get(key);
    }

    public static @Nullable Party nextParty() {
        return parties.isEmpty() ? null : parties.get(0);
    }
}
