package com.enn3developer.gtblocker.mixins.late.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.compat.gt.GTHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/**
 * {@code process()} has two paths that never search the recipe map: a multiblock locked to one recipe
 * ({@code SingleRecipeCheck}) and the CRIB pattern cache. Both, like the normal path, go through
 * {@code validateAndCalculateRecipe}, which asks {@code validateRecipe} first; answering "locked" there stops all
 * three, and the machine GUI says why. Wrapping the call site also covers subclasses that override
 * {@code validateRecipe} without calling super.
 */
@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class ProcessingLogicMixin {

    @Shadow
    protected IVoidable machine;

    @WrapOperation(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;validateRecipe(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;"))
    private CheckRecipeResult gtblocker$refuseLockedRecipe(ProcessingLogic self, GTRecipe recipe,
        Operation<CheckRecipeResult> original) {
        return GTHooks.isLocked(recipe) ? GTHooks.LOCKED : original.call(self, recipe);
    }

    @Inject(method = "applyRecipe", at = @At("RETURN"))
    private void gtblocker$reportStart(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult result, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (cir.getReturnValue()
            .wasSuccessful()) {
            GTHooks.onRecipeStarted(machine, recipe.mOutputs);
        }
    }
}
