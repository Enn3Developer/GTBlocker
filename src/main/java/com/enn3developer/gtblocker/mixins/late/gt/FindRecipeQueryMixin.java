package com.enn3developer.gtblocker.mixins.late.gt;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.compat.gt.GTHooks;
import com.enn3developer.gtblocker.lock.Locks;

import gregtech.api.recipe.FindRecipeQuery;
import gregtech.api.util.GTRecipe;

/**
 * Every GregTech recipe lookup ends in {@code FindRecipeQuery.findAll} (it is the only runtime caller of the
 * backend's match stream), so filtering its result covers single blocks, ProcessingLogic multis and every addon
 * recipe map, custom backends included. Load-time collision checks are left alone.
 */
@Mixin(value = FindRecipeQuery.class, remap = false)
public abstract class FindRecipeQueryMixin {

    @Shadow
    private boolean forCollisionCheck;

    @Inject(method = "findAll", at = @At("RETURN"), cancellable = true)
    private void gtblocker$dropLockedRecipes(CallbackInfoReturnable<Stream<GTRecipe>> cir) {
        if (forCollisionCheck || Locks.current()
            .isEmpty()) {
            return;
        }
        cir.setReturnValue(
            cir.getReturnValue()
                .filter(recipe -> !GTHooks.isLocked(recipe)));
    }
}
