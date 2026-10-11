package com.enn3developer.gtblocker.lock;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.registry.GameData;

/**
 * One locked output: an item registry name plus a metadata value, or {@link #ANY_META} for every metadata of that
 * item. Registry names (never numeric ids) are what gets persisted and synced, so locks survive id shuffles.
 */
public record LockKey(String itemId, int meta) {

    public static final int ANY_META = OreDictionary.WILDCARD_VALUE;

    public boolean anyMeta() {
        return meta == ANY_META;
    }

    /** The key locking exactly {@code stack} (or every meta of its item), or null for unregistered items. */
    public static @Nullable LockKey of(@Nullable ItemStack stack, boolean anyMeta) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String name = GameData.getItemRegistry()
            .getNameForObject(stack.getItem());
        if (name == null) {
            return null;
        }
        return new LockKey(name, anyMeta ? ANY_META : stack.getItemDamage());
    }

    /** The registered item, or null if no loaded mod provides it (the lock then stays inactive). */
    public @Nullable Item item() {
        Object item = GameData.getItemRegistry()
            .getObject(itemId);
        return item instanceof Item i ? i : null;
    }

    /** A stack to render or name this key with (meta 0 for any-meta keys), or null if the item is missing. */
    public @Nullable ItemStack displayStack() {
        Item item = item();
        return item == null ? null : new ItemStack(item, 1, anyMeta() ? 0 : meta);
    }

    /** {@code modid:name:meta}, or {@code modid:name:*} for any meta; the inverse of {@link #parse}. */
    public String serialize() {
        return itemId + ":" + (anyMeta() ? "*" : Integer.toString(meta));
    }

    /**
     * Parses {@code modid:name}, {@code modid:name:meta} or {@code modid:name:*}. A bare {@code modid:name} means
     * any meta, matching the ServerStatistics draft's format. Returns null for anything else.
     */
    public static @Nullable LockKey parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        int first = trimmed.indexOf(':');
        if (first <= 0 || first == trimmed.length() - 1) {
            return null;
        }
        int last = trimmed.lastIndexOf(':');
        if (last == first) {
            return new LockKey(trimmed, ANY_META);
        }
        String suffix = trimmed.substring(last + 1);
        String id = trimmed.substring(0, last);
        if (suffix.equals("*")) {
            return new LockKey(id, ANY_META);
        }
        try {
            int meta = Integer.parseInt(suffix);
            return meta < 0 || meta > Short.MAX_VALUE ? null : new LockKey(id, meta);
        } catch (NumberFormatException e) {
            // Not a meta suffix: the colon belongs to the registry name.
            return new LockKey(trimmed, ANY_META);
        }
    }

    @Override
    public String toString() {
        return serialize();
    }
}
