package com.enn3developer.gtblocker.lock;

import java.util.function.Supplier;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

import org.jetbrains.annotations.Nullable;

/**
 * Entry points called from patched bytecode. {@link #filterMatches} is spliced into every
 * {@code IRecipe.matches} by {@link com.enn3developer.gtblocker.core.RecipeMatchTransformer}, so its name and
 * descriptor are part of that contract.
 */
public final class LockHooks {

    // getCraftingResult is only consulted for recipes without a template output, and some of those call matches()
    // again from inside it. The guard turns that recursion into a plain "not locked" answer.
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Depth of withoutGate() calls on this thread; a counter so nested calls stay correct.
    private static final ThreadLocal<int[]> UNGATED = ThreadLocal.withInitial(() -> new int[1]);

    private LockHooks() {}

    /**
     * Runs {@code lookup} with recipe matching ungated on this thread. For code that only needs to know what a
     * recipe is, not to craft it: AE2 decoding a crafting pattern must still find the recipe while its output is
     * locked, or the pattern goes invalid and stays that way after the unlock. Crafting with it is gated elsewhere.
     */
    public static <T> T withoutGate(Supplier<T> lookup) {
        int[] depth = UNGATED.get();
        depth[0]++;
        try {
            return lookup.get();
        } finally {
            depth[0]--;
        }
    }

    /**
     * Runs at every {@code IRETURN} of a recipe's {@code matches}: a match whose output is locked becomes a
     * non-match. Only successful matches pay for the lookup, and an empty table costs one volatile read.
     */
    public static boolean filterMatches(boolean matched, Object recipe, InventoryCrafting inventory) {
        if (!matched) {
            return false;
        }
        LockTable table = Locks.current();
        if (table.isEmpty() || UNGATED.get()[0] > 0) {
            return true;
        }
        ItemStack output = outputOf((IRecipe) recipe, inventory);
        if (!table.isLocked(output)) {
            return true;
        }
        if (Locks.isClientThread()) {
            BlockedCraftMemo.record(inventory, output);
        }
        return false;
    }

    private static @Nullable ItemStack outputOf(IRecipe recipe, InventoryCrafting inventory) {
        ItemStack template = recipe.getRecipeOutput();
        if (template != null || RESOLVING.get()) {
            return template;
        }
        RESOLVING.set(Boolean.TRUE);
        try {
            return recipe.getCraftingResult(inventory);
        } catch (RuntimeException e) {
            // Dynamic recipes may not expect a result query here; treat them as unlockable rather than crash.
            return null;
        } finally {
            RESOLVING.set(Boolean.FALSE);
        }
    }
}
