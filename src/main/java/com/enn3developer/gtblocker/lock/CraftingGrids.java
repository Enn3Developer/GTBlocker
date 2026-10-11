package com.enn3developer.gtblocker.lock;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.GTBlocker;

/**
 * Grids only re-match when their contents change, so after the lock table changes a player at a filled crafting
 * table would see no result for a fresh unlock, or keep a now-locked one. Re-running the match fixes both: on the
 * server the container syncs the result slot itself, on the client it also records the lock icon for the grid.
 */
public final class CraftingGrids {

    private CraftingGrids() {}

    /** Re-matches the grids of the player's open screen and of their own inventory. */
    public static void refresh(EntityPlayer player) {
        refresh(player.openContainer);
        if (player.openContainer != player.inventoryContainer) {
            refresh(player.inventoryContainer);
        }
    }

    private static void refresh(@Nullable Container container) {
        if (container == null) {
            return;
        }
        Set<InventoryCrafting> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object object : container.inventorySlots) {
            if (((Slot) object).inventory instanceof InventoryCrafting grid && grids.add(grid)) {
                try {
                    container.onCraftMatrixChanged(grid);
                } catch (RuntimeException e) {
                    GTBlocker.LOG.debug("Could not refresh crafting grid of {}", container.getClass(), e);
                }
            }
        }
    }
}
