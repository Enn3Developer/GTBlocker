package com.enn3developer.gtblocker.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.data.Spot;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.net.Codec;
import com.enn3developer.gtblocker.text.Chat;

/**
 * Turns server events into chat lines and effects. The server only sends facts; wording, item names and times are
 * produced here, in the player's own language and time zone.
 */
public final class Announcer {

    private Announcer() {}

    static void handle(String type, NBTTagCompound data) {
        switch (type) {
            case "locked" -> {
                if (!byMe(data)) {
                    say(EnumChatFormatting.GRAY, "gtblocker.chat.locked", data.getString("by"), items(data));
                }
            }
            case "unlocked" -> {
                if (!byMe(data)) {
                    say(EnumChatFormatting.GREEN, "gtblocker.chat.unlocked", data.getString("by"), items(data));
                }
            }
            case "party_scheduled" -> partyScheduled(Codec.readParty(data.getCompoundTag("party")));
            case "party_starting" -> {
                Party party = ClientState.party(data.getInteger("id"));
                say(
                    EnumChatFormatting.GOLD,
                    "gtblocker.chat.party_starting",
                    data.getString("by"),
                    data.getInteger("id"),
                    party == null ? "" : Chat.items(party.items()));
                ping();
            }
            case "party_reminder" -> {
                int minutes = data.getInteger("minutes");
                Party party = ClientState.party(data.getInteger("id"));
                IChatComponent items = party == null ? new ChatComponentText("") : Chat.items(party.items());
                if (minutes == 1) {
                    say(EnumChatFormatting.GOLD, "gtblocker.chat.reminder_one", data.getInteger("id"), items);
                } else {
                    say(EnumChatFormatting.GOLD, "gtblocker.chat.reminder", data.getInteger("id"), minutes, items);
                }
                ping();
            }
            case "party_cancelled" -> say(
                EnumChatFormatting.GRAY,
                "gtblocker.chat.party_cancelled",
                data.getInteger("id"),
                data.getString("by"));
            case "reveal" -> reveal(data);
            case "first_craft" -> firstCraft(data);
            case "voice" -> voice(data.getString("state"));
            case "away" -> say(EnumChatFormatting.AQUA, "gtblocker.chat.away", items(data));
            default -> {}
        }
    }

    private static void partyScheduled(Party party) {
        say(EnumChatFormatting.GOLD, "gtblocker.chat.party_scheduled", party.id(), Chat.items(party.items()));
        long left = party.revealAt() - ClientState.serverNow();
        if (left <= ClientState.countdownSeconds() * 1000L + 1000) {
            line(EnumChatFormatting.YELLOW, "gtblocker.chat.party_when_now", Math.max(1, (left + 999) / 1000));
        } else {
            line(
                EnumChatFormatting.YELLOW,
                "gtblocker.chat.party_when",
                Times.clock(party.revealAt()),
                Times.until(party.revealAt()));
        }
        if (!party.message()
            .isEmpty()) {
            line(EnumChatFormatting.WHITE, "gtblocker.chat.party_message", party.message(), party.createdBy());
        }
        if (party.spot() != null) {
            spot(party.spot());
        }
        ping();
    }

    private static void reveal(NBTTagCompound data) {
        List<LockKey> items = Codec.readKeys(data, "items");
        String message = data.getString("msg");
        Spot spot = Codec.readSpot(data, "spot");

        send(new ChatComponentText(""));
        IChatComponent headline = Chat.tr(EnumChatFormatting.GOLD, "gtblocker.chat.reveal", Chat.items(items));
        headline.getChatStyle()
            .setBold(true);
        send(badge(EnumChatFormatting.GOLD).appendSibling(headline));
        if (!message.isEmpty()) {
            line(EnumChatFormatting.WHITE, "gtblocker.chat.party_message", message, data.getString("by"));
        }
        if (spot != null) {
            spot(spot);
        }
        line(EnumChatFormatting.GRAY, "gtblocker.chat.reveal_hint");
        send(new ChatComponentText(""));

        TitleShow.reveal(items, message);
        Fanfare.play();
        FireworkShow.start(spot, data.getLong("seed"));
    }

    private static void firstCraft(NBTTagCompound data) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(data.getCompoundTag("stack"));
        LockKey key = LockKey.parse(data.getString("item"));
        IChatComponent item = stack != null ? Chat.item(stack)
            : key != null ? Chat.item(key) : new ChatComponentText(data.getString("item"));
        String player = data.getString("player");
        if (player.equals(myName())) {
            say(EnumChatFormatting.YELLOW, "gtblocker.chat.first_craft_you", item);
            if (stack != null && Config.revealTitle) {
                TitleShow.firstCraft(stack);
            }
        } else {
            say(EnumChatFormatting.YELLOW, "gtblocker.chat.first_craft", player, item);
        }
    }

    private static void voice(String state) {
        switch (state) {
            case "joined" -> send(
                badge(EnumChatFormatting.LIGHT_PURPLE)
                    .appendSibling(Chat.tr(EnumChatFormatting.LIGHT_PURPLE, "gtblocker.chat.voice_joined"))
                    .appendSibling(new ChatComponentText(" "))
                    .appendSibling(button("gtblocker.chat.voice_leave", "/gtblocker voice leave")));
            case "left" -> send(
                badge(EnumChatFormatting.LIGHT_PURPLE)
                    .appendSibling(Chat.tr(EnumChatFormatting.GRAY, "gtblocker.chat.voice_left"))
                    .appendSibling(new ChatComponentText(" "))
                    .appendSibling(button("gtblocker.chat.voice_join", "/gtblocker voice join")));
            case "ended" -> say(EnumChatFormatting.GRAY, "gtblocker.chat.voice_ended");
            default -> {}
        }
    }

    private static void spot(Spot spot) {
        line(
            EnumChatFormatting.YELLOW,
            "gtblocker.chat.party_spot",
            spot.x(),
            spot.y(),
            spot.z(),
            dimensionName(spot.dim()));
    }

    private static String dimensionName(int dim) {
        switch (dim) {
            case 0:
                return "Overworld";
            case -1:
                return "Nether";
            case 1:
                return "The End";
            default:
                break;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && mc.theWorld.provider.dimensionId == dim) {
            return mc.theWorld.provider.getDimensionName();
        }
        return "DIM" + dim;
    }

    private static IChatComponent items(NBTTagCompound data) {
        return Chat.items(Codec.readKeys(data, "items"));
    }

    private static boolean byMe(NBTTagCompound data) {
        return data.getString("by")
            .equals(myName());
    }

    private static @Nullable String myName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : mc.thePlayer.getCommandSenderName();
    }

    private static void ping() {
        if (Config.fanfare) {
            Sounds.play("note.pling", 0.5f, Sounds.notePitch(18));
        }
    }

    private static IChatComponent badge(EnumChatFormatting color) {
        IChatComponent badge = new ChatComponentText("✦ ");
        badge.getChatStyle()
            .setColor(color);
        return badge;
    }

    private static IChatComponent button(String key, String command) {
        IChatComponent button = Chat.tr(EnumChatFormatting.AQUA, key);
        button.setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(command))));
        return button;
    }

    /** A headline: badge plus text. */
    private static void say(EnumChatFormatting color, String key, Object... args) {
        send(
            badge(color).appendSibling(Chat.tr(color, key, args)));
    }

    /** A follow-up line, indented under a headline. */
    private static void line(EnumChatFormatting color, String key, Object... args) {
        send(
            new ChatComponentText("  ").appendSibling(Chat.tr(color, key, args)));
    }

    private static void send(IChatComponent component) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(component);
        }
    }
}
