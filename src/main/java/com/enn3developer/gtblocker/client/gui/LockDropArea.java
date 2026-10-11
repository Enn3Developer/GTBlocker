package com.enn3developer.gtblocker.client.gui;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.lock.LockKey;

/**
 * The Locks tab's item area. Dropping an item dragged from NEI anywhere on it locks that item: left click for that
 * exact metadata, right click for every metadata.
 */
final class LockDropArea extends ParentWidget<LockDropArea> implements RecipeViewerGhostIngredientSlot<ItemStack> {

    @Override
    public boolean canHover() {
        // Needed to receive drops: an empty parent without a background isn't hoverable by default.
        return true;
    }

    @Override
    public boolean handleDragAndDrop(@NotNull ItemStack draggedStack, int button) {
        if (!ClientState.isAdmin()) {
            return false;
        }
        LockKey key = LockKey.of(draggedStack, button == 1);
        if (key == null) {
            return false;
        }
        Actions.lock(key);
        return true;
    }
}
