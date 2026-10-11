package com.enn3developer.gtblocker.mixins.late.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.lock.LockHooks;
import com.enn3developer.gtblocker.lock.Locks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;

/**
 * Crafting patterns: decoding always sees the real recipe, assembling refuses locked outputs.
 * <p>
 * Decoding runs {@code IRecipe.matches}, which the recipe gate would fail for a locked output. The pattern would then
 * be dropped from its interface and only come back when AE2 happens to decode it again, which it doesn't do on an
 * unlock. So decoding is ungated, and the lock is enforced where crafting happens: here at assembly, and in the
 * planner ({@link CraftingGridCacheMixin}).
 */
@Mixin(value = PatternHelper.class, remap = false)
public abstract class PatternHelperMixin {

    @Shadow
    @Final
    private IAEItemStack[] outputs;

    @Shadow
    @Final
    private boolean isCrafting;

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;findMatchingRecipe(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Lnet/minecraft/item/crafting/IRecipe;"))
    private IRecipe gtblocker$decodeUngated(InventoryCrafting grid, World world, Operation<IRecipe> original) {
        return LockHooks.withoutGate(() -> original.call(grid, world));
    }

    // A decoded pattern caches its recipe and accepted slot contents, so a Molecular Assembler crafts without calling
    // IRecipe.matches again.
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
