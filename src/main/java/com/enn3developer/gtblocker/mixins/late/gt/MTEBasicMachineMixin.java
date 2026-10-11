package com.enn3developer.gtblocker.mixins.late.gt;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.compat.gt.GTHooks;

import gregtech.api.metatileentity.implementations.MTEBasicMachine;

/**
 * Single-block machines don't use ProcessingLogic. Locked recipes already can't be found (see
 * {@link FindRecipeQueryMixin}); this only reports a successful start to the first-craft race.
 */
@Mixin(value = MTEBasicMachine.class, remap = false)
public abstract class MTEBasicMachineMixin {

    private static final int FOUND_AND_SUCCESSFULLY_USED_RECIPE = 2;

    @Shadow
    @Final
    public ItemStack[] mOutputItems;

    @Inject(method = "checkRecipe(Z)I", at = @At("RETURN"))
    private void gtblocker$reportStart(boolean skipOC, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == FOUND_AND_SUCCESSFULLY_USED_RECIPE) {
            GTHooks.onRecipeStarted(this, mOutputItems);
        }
    }
}
