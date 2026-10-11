package com.enn3developer.gtblocker.mixins.late.gt;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.compat.gt.GTHooks;
import com.enn3developer.gtblocker.lock.LockTable;
import com.enn3developer.gtblocker.lock.Locks;
import com.llamalad7.mixinextras.sugar.Local;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.MTEAssemblyLine;

/**
 * The Assembly Line reads its recipes off data sticks instead of a recipe map, so it gets its own filter: drop
 * locked recipes from the candidate list right after it is collected.
 */
@Mixin(value = MTEAssemblyLine.class, remap = false)
public abstract class MTEAssemblyLineMixin {

    @Inject(
        method = "checkProcessing",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;isEmpty()Z", ordinal = 0),
        cancellable = true)
    private void gtblocker$dropLockedRecipes(CallbackInfoReturnable<CheckRecipeResult> cir,
        @Local ArrayList<GTRecipe.RecipeAssemblyLine> availableRecipes) {
        LockTable table = Locks.current();
        if (table.isEmpty()) {
            return;
        }
        if (availableRecipes.removeIf(recipe -> table.isLocked(recipe.mOutput)) && availableRecipes.isEmpty()) {
            cir.setReturnValue(GTHooks.LOCKED);
        }
    }
}
