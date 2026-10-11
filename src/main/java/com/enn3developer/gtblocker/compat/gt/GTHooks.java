package com.enn3developer.gtblocker.compat.gt;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.lock.Locks;
import com.enn3developer.gtblocker.server.BlockerServer;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;

/** GregTech-typed helpers for the GT mixins. Only ever loaded from them, so only when GregTech is present. */
public final class GTHooks {

    /** Shown in the machine GUI as {@code GT5U.gui.text.recipe_result.gtblocker_locked}. */
    public static final CheckRecipeResult LOCKED = SimpleCheckRecipeResult.ofFailure("gtblocker_locked");

    private GTHooks() {}

    /** A recipe is blocked if any of its item outputs is locked, byproducts and chanced outputs included. */
    public static boolean isLocked(GTRecipe recipe) {
        return Locks.current()
            .anyLocked(recipe.mOutputs);
    }

    /** A machine just started a recipe: feed its owner and outputs to the first-craft race. */
    public static void onRecipeStarted(@Nullable Object machine, @Nullable ItemStack[] outputs) {
        BlockerServer server = BlockerServer.get();
        if (server == null || !server.awaitsFirstCraft() || outputs == null
            || !(machine instanceof IMetaTileEntity metaTileEntity)) {
            return;
        }
        IGregTechTileEntity base = metaTileEntity.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) {
            return;
        }
        String owner = base.getOwnerName();
        if (owner != null && !owner.isEmpty()) {
            server.onCrafted(owner, outputs);
        }
    }
}
