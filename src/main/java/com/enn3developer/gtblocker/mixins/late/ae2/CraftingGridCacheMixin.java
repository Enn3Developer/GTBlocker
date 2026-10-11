package com.enn3developer.gtblocker.mixins.late.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enn3developer.gtblocker.compat.ae2.AE2Hooks;
import com.enn3developer.gtblocker.lock.LockTable;
import com.enn3developer.gtblocker.lock.Locks;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;

/**
 * The crafting planner asks the grid which patterns can make each item. Hiding patterns with a locked output here
 * makes the job calculation report the item as missing, so a job never starts and never strands inputs in a GT
 * machine or Molecular Assembler that would refuse them. The item-stack overload delegates to this one. Runs on
 * AE2's crafting pool thread, so it reads the server table explicitly.
 * <p>
 * Terminals get their "craftable" entries from {@code getAvailableItems}, built per request, so locked items simply
 * aren't offered for crafting; a terminal opened after an unlock offers them again.
 */
@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class CraftingGridCacheMixin {

    @Inject(
        method = "getCraftingFor(Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/crafting/ICraftingPatternDetails;ILnet/minecraft/world/World;)Lcom/google/common/collect/ImmutableCollection;",
        at = @At("RETURN"),
        cancellable = true)
    private void gtblocker$hideLockedPatterns(IAEStack<?> whatToCraft, ICraftingPatternDetails details, int slotIndex,
        World world, CallbackInfoReturnable<ImmutableCollection<ICraftingPatternDetails>> cir) {
        LockTable table = Locks.server();
        ImmutableCollection<ICraftingPatternDetails> patterns = cir.getReturnValue();
        if (table.isEmpty() || patterns == null || patterns.isEmpty()) {
            return;
        }
        List<ICraftingPatternDetails> allowed = null;
        for (ICraftingPatternDetails pattern : patterns) {
            boolean locked = AE2Hooks.outputsLocked(pattern, table);
            if (locked && allowed == null) {
                allowed = new ArrayList<>(patterns.size());
                for (ICraftingPatternDetails earlier : patterns) {
                    if (earlier == pattern) {
                        break;
                    }
                    allowed.add(earlier);
                }
            } else if (!locked && allowed != null) {
                allowed.add(pattern);
            }
        }
        if (allowed != null) {
            cir.setReturnValue(ImmutableList.copyOf(allowed));
        }
    }

    @WrapOperation(
        method = "getAvailableItems(Lappeng/api/storage/data/IItemList;ILjava/util/Optional;)Lappeng/api/storage/data/IItemList;",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IItemList;addCrafting(Lappeng/api/storage/data/IAEStack;)V"))
    @SuppressWarnings("rawtypes")
    private void gtblocker$hideLockedCraftables(IItemList list, IAEStack stack, Operation<Void> original) {
        if (stack instanceof IAEItemStack item && Locks.server()
            .isLocked(item.getItem(), item.getItemDamage())) {
            return;
        }
        original.call(list, stack);
    }
}
