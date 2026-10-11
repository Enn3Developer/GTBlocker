package com.enn3developer.gtblocker.text;

import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.enn3developer.gtblocker.lock.LockKey;

/**
 * Chat building blocks shared by server feedback and client chat lines. Text goes out as translation keys, so the
 * reader's client renders it in their own language (the dedicated server has the English file for its console).
 */
public final class Chat {

    private Chat() {}

    public static IChatComponent tr(EnumChatFormatting color, String key, Object... args) {
        IChatComponent component = new ChatComponentTranslation(key, args);
        component.getChatStyle()
            .setColor(color);
        return component;
    }

    public static IChatComponent ok(String key, Object... args) {
        return tr(EnumChatFormatting.GREEN, key, args);
    }

    public static IChatComponent err(String key, Object... args) {
        return tr(EnumChatFormatting.RED, key, args);
    }

    /** {@code [Item Name]} with the real item tooltip on hover, or the raw key if the item isn't loaded. */
    public static IChatComponent item(LockKey key) {
        ItemStack stack = key.displayStack();
        if (stack == null) {
            return new ChatComponentText(key.serialize());
        }
        IChatComponent component = item(stack);
        if (key.anyMeta()) {
            component.appendSibling(new ChatComponentText(" (*)"));
        }
        return component;
    }

    public static IChatComponent item(ItemStack stack) {
        IChatComponent component = new ChatComponentText("[" + stack.getDisplayName() + "]");
        ItemStack single = stack.copy();
        single.stackSize = 1;
        component.setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_ITEM,
                        new ChatComponentText(
                            single.writeToNBT(new NBTTagCompound())
                                .toString()))));
        return component;
    }

    /** Comma-separated item list. */
    public static IChatComponent items(Iterable<LockKey> keys) {
        IChatComponent list = new ChatComponentText("");
        boolean first = true;
        for (LockKey key : keys) {
            if (!first) {
                list.appendSibling(new ChatComponentText(", "));
            }
            list.appendSibling(item(key));
            first = false;
        }
        return list;
    }
}
