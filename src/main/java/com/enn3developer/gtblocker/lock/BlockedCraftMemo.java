package com.enn3developer.gtblocker.lock;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Client-side memory of "this grid would craft a locked item", so the GUI overlay can draw a lock on the empty
 * result slot without re-running the recipe search every frame. Written by {@link LockHooks} when it blocks a match
 * on the client thread; an entry only counts while the grid still holds exactly what it held when it was recorded.
 * Client main thread only, so no synchronization.
 */
public final class BlockedCraftMemo {

    private static final Map<InventoryCrafting, Entry> MEMO = new WeakHashMap<>();

    private BlockedCraftMemo() {}

    static void record(InventoryCrafting inventory, @Nullable ItemStack output) {
        if (output != null) {
            MEMO.put(inventory, new Entry(output.copy(), contentsHash(inventory)));
        }
    }

    /** The locked output this grid currently matches, or null. */
    public static @Nullable ItemStack blockedOutput(InventoryCrafting inventory) {
        Entry entry = MEMO.get(inventory);
        if (entry == null) {
            return null;
        }
        if (entry.contentsHash != contentsHash(inventory)) {
            MEMO.remove(inventory);
            return null;
        }
        return entry.output;
    }

    public static void clear() {
        MEMO.clear();
    }

    private static int contentsHash(InventoryCrafting inventory) {
        int hash = 1;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            int slotHash = 0;
            if (stack != null) {
                Item item = stack.getItem();
                slotHash = System.identityHashCode(item) * 31 + stack.getItemDamage();
                slotHash = slotHash * 31 + stack.stackSize;
            }
            hash = hash * 37 + slotHash;
        }
        return hash;
    }

    private record Entry(ItemStack output, int contentsHash) {}
}
