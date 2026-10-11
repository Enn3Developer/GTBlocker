package com.enn3developer.gtblocker.mixins.late.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.lock.Locks;

import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;

/**
 * A decoded crafting pattern keeps its recipe and caches which slot contents it accepted, so a Molecular Assembler
 * can keep crafting without ever calling {@code IRecipe.matches} again. Refuse the output here once it's locked.
 */
@Mixin(value = PatternHelper.class, remap = false)
public abstract class PatternHelperMixin {

    @Shadow
    @Final
    private IAEItemStack[] outputs;

    @Shadow
    @Final
    private boolean isCrafting;

    @Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)
    private void gtblocker$refuseLockedOutput(InventoryCrafting craftingInv, World world,
        CallbackInfoReturnable<ItemStack> cir) {
        if (isCrafting && outputs != null
            && outputs.length > 0
            && outputs[0] != null
            && Locks.server()
                .isLocked(outputs[0].getItem(), outputs[0].getItemDamage())) {
            cir.setReturnValue(null);
        }
    }
}
