package com.enn3developer.gtblocker.command;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.data.HistoryEntry;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.server.BlockerServer;
import com.enn3developer.gtblocker.server.TimeParser;
import com.enn3developer.gtblocker.text.Chat;

/**
 * {@code /gtblocker} ({@code /gtb}). Usable by everyone at the vanilla level; the mod's own admin list gates every
 * change. Items are {@code hand} (held item, exact meta), {@code hand*} (held item, any meta) or
 * {@code modid:name[:meta|*]}.
 */
public final class CommandGTBlocker extends CommandBase {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    @Override
    public String getCommandName() {
        return "gtblocker";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return Collections.singletonList("gtb");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "gtblocker.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        BlockerServer server = BlockerServer.get();
        if (server == null) {
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof EntityPlayerMP player) {
                server.openGui(player);
                return;
            }
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender, server);
            case "history" -> history(sender, server);
            case "lock" -> {
                requireAdmin(sender, server);
                for (LockKey key : items(sender, rest)) {
                    sender.addChatMessage(server.lock(key, sender.getCommandSenderName())
                        .message());
                }
            }
            case "unlock" -> {
                requireAdmin(sender, server);
                sender.addChatMessage(server.unlockQuiet(items(sender, rest), sender.getCommandSenderName())
                    .message());
            }
            case "party" -> party(sender, server, rest);
            case "admin" -> admin(sender, server, rest);
            case "voice" -> {
                if (!(sender instanceof EntityPlayerMP player) || rest.length != 1) {
                    throw new WrongUsageException("gtblocker.usage.voice");
                }
                server.setVoice(player, rest[0].equalsIgnoreCase("join"));
            }
            default -> throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void party(ICommandSender sender, BlockerServer server, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            partyList(sender, server);
            return;
        }
        requireAdmin(sender, server);
        String actor = sender.getCommandSenderName();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "now" -> sender.addChatMessage(server.scheduleParty(items(sender, rest), 0, "", null, actor)
                .message());
            case "at" -> {
                if (rest.length < 2) {
                    throw new WrongUsageException("gtblocker.usage.party");
                }
                long at = TimeParser.parse(rest[0], System.currentTimeMillis(), ZoneId.systemDefault());
                if (at < 0) {
                    throw new WrongUsageException("gtblocker.err.bad_time", rest[0]);
                }
                List<LockKey> keys = items(sender, Arrays.copyOfRange(rest, 1, rest.length));
                sender.addChatMessage(server.scheduleParty(keys, at, "", null, actor)
                    .message());
            }
            case "start" -> sender.addChatMessage(server.startPartyNow(partyId(rest), actor)
                .message());
            case "cancel" -> sender.addChatMessage(server.cancelParty(partyId(rest), actor)
                .message());
            case "spot" -> {
                boolean clear = rest.length > 1 && rest[1].equalsIgnoreCase("clear");
                if (!clear && !(sender instanceof EntityPlayerMP)) {
                    throw new WrongUsageException("gtblocker.usage.party");
                }
                sender.addChatMessage(
                    server.setPartySpot(
                        partyId(rest),
                        clear ? null : BlockerServer.spotOf((EntityPlayerMP) sender))
                        .message());
            }
            default -> throw new WrongUsageException("gtblocker.usage.party");
        }
    }

    private void admin(ICommandSender sender, BlockerServer server, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Map<UUID, String> admins = server.admins();
            sender.addChatMessage(
                Chat.tr(
                    EnumChatFormatting.GOLD,
                    "gtblocker.cmd.admins",
                    admins.size(),
                    admins.isEmpty() ? "-" : String.join(", ", admins.values())));
            return;
        }
        if (args.length != 2) {
            throw new WrongUsageException("gtblocker.usage.admin");
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            // addAdmin/removeAdmin apply the console-bootstrap rule themselves.
            case "add" -> sender.addChatMessage(server.addAdmin(args[1], sender)
                .message());
            case "remove" -> sender.addChatMessage(server.removeAdmin(args[1], sender)
                .message());
            default -> throw new WrongUsageException("gtblocker.usage.admin");
        }
    }

    private static void list(ICommandSender sender, BlockerServer server) {
        sender.addChatMessage(Chat.tr(EnumChatFormatting.GOLD, "gtblocker.cmd.locks", server.locks()
            .size()));
        for (LockKey key : server.locks()) {
            sender.addChatMessage(
                new ChatComponentText(" - ").appendSibling(Chat.item(key))
                    .appendSibling(new ChatComponentText(" " + EnumChatFormatting.DARK_GRAY + key.serialize())));
        }
    }

    private static void partyList(ICommandSender sender, BlockerServer server) {
        List<Party> parties = server.parties();
        sender.addChatMessage(Chat.tr(EnumChatFormatting.GOLD, "gtblocker.cmd.parties", parties.size()));
        for (Party party : parties) {
            IChatComponent line = new ChatComponentText(
                " #" + party.id() + " " + TIME.format(Instant.ofEpochMilli(party.revealAt())) + " ");
            line.appendSibling(Chat.items(party.items()));
            sender.addChatMessage(line);
        }
    }

    private static void history(ICommandSender sender, BlockerServer server) {
        List<HistoryEntry> history = server.history();
        sender.addChatMessage(Chat.tr(EnumChatFormatting.GOLD, "gtblocker.cmd.history", history.size()));
        for (HistoryEntry entry : history.subList(Math.max(0, history.size() - 10), history.size())) {
            IChatComponent line = new ChatComponentText(" " + TIME.format(Instant.ofEpochMilli(entry.unlockedAt())) + " ");
            line.appendSibling(Chat.item(entry.key()));
            line.appendSibling(new ChatComponentText(" (" + entry.unlockedBy() + ")"));
            if (entry.firstCraftBy() != null) {
                line.appendSibling(new ChatComponentText(" " + EnumChatFormatting.YELLOW + "1st: " + entry.firstCraftBy()));
            }
            sender.addChatMessage(line);
        }
    }

    private static void requireAdmin(ICommandSender sender, BlockerServer server) {
        if (sender instanceof EntityPlayerMP player && server.isAdmin(player)) {
            return;
        }
        throw new WrongUsageException(
            BlockerServer.isConsole(sender) ? "gtblocker.err.console_bootstrap_only" : "gtblocker.err.not_admin");
    }

    private static int partyId(String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("gtblocker.usage.party");
        }
        try {
            return Integer.parseInt(args[0].replace("#", ""));
        } catch (NumberFormatException e) {
            throw new WrongUsageException("gtblocker.usage.party");
        }
    }

    private static List<LockKey> items(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException("gtblocker.err.no_items");
        }
        List<LockKey> keys = new ArrayList<>();
        for (String arg : args) {
            LockKey key = item(sender, arg);
            if (key == null) {
                throw new WrongUsageException("gtblocker.err.bad_item", arg);
            }
            keys.add(key);
        }
        return keys;
    }

    private static @Nullable LockKey item(ICommandSender sender, String arg) {
        if (arg.equalsIgnoreCase("hand") || arg.equalsIgnoreCase("hand*")) {
            if (!(sender instanceof EntityPlayerMP player)) {
                return null;
            }
            ItemStack held = player.getHeldItem();
            return LockKey.of(held, arg.endsWith("*"));
        }
        return LockKey.parse(arg);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "gui",
                "list",
                "history",
                "lock",
                "unlock",
                "party",
                "admin",
                "voice");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "lock", "unlock" -> getListOfStringsMatchingLastWord(args, "hand", "hand*");
                case "party" -> getListOfStringsMatchingLastWord(args, "list", "now", "at", "start", "cancel", "spot");
                case "admin" -> getListOfStringsMatchingLastWord(args, "list", "add", "remove");
                case "voice" -> getListOfStringsMatchingLastWord(args, "join", "leave");
                default -> null;
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add")) {
            return getListOfStringsMatchingLastWord(
                args,
                net.minecraft.server.MinecraftServer.getServer()
                    .getAllUsernames());
        }
        return null;
    }
}
