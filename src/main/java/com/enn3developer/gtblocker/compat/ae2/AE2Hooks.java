package com.enn3developer.gtblocker.compat.ae2;

import com.enn3developer.gtblocker.lock.LockTable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** AE2-typed helpers for the AE2 mixins. Only ever loaded from them, so only when AE2 is present. */
public final class AE2Hooks {

    private AE2Hooks() {}

    /** True if any item output of the pattern is locked; one locked byproduct blocks the whole pattern. */
    public static boolean outputsLocked(ICraftingPatternDetails pattern, LockTable table) {
        IAEStack<?>[] outputs = pattern.getAEOutputs();
        if (outputs == null) {
            return false;
        }
        for (IAEStack<?> output : outputs) {
            if (output instanceof IAEItemStack item && table.isLocked(item.getItem(), item.getItemDamage())) {
                return true;
            }
        }
        return false;
    }
}
