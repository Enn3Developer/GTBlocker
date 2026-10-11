package com.enn3developer.gtblocker.client.nei;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.client.Times;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.BlockedCraftMemo;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.lock.Locks;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * NEI integration: recipe lookups for chat links, and the lock on an empty crafting result. NEI's per-slot draw hook
 * runs inside the GUI's own slot pass, so the lock sits under tooltips and the held item like a real slot overlay.
 * Only loaded when NEI is.
 */
public final class NeiCompat implements IContainerDrawHandler, IContainerTooltipHandler {

    private static final RenderItem ITEMS = new RenderItem();
    private static @Nullable Field craftMatrix;

    private NeiCompat() {}

    public static void register() {
        NeiCompat compat = new NeiCompat();
        GuiContainerManager.addDrawHandler(compat);
        GuiContainerManager.addTooltipHandler(compat);
        try {
            craftMatrix = ReflectionHelper.findField(SlotCrafting.class, "field_75239_a", "craftMatrix");
        } catch (RuntimeException e) {
            GTBlocker.LOG.warn("SlotCrafting.craftMatrix not found; lock icons only where the grid is unambiguous");
        }
    }

    public static void showRecipes(ItemStack stack) {
        GuiCraftingRecipe.openRecipeGui("item", stack);
    }

    @Override
    public void renderSlotOverlay(GuiContainer gui, Slot slot) {
        if (!Config.lockIcon || slot.getHasStack()
            || Locks.client()
                .isEmpty()) {
            return;
        }
        ItemStack blocked = blockedOutput(gui, slot);
        if (blocked == null) {
            return;
        }
        int x = slot.xDisplayPosition;
        int y = slot.yDisplayPosition;
        Minecraft mc = Minecraft.getMinecraft();
        ITEMS.zLevel = 100.0f;
        ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), blocked, x, y);
        NEIClientUtils.gl2DRenderContext(() -> {
            // Greyed-out result with a padlock in the corner.
            Gui.drawRect(x, y, x + 16, y + 16, 0x90202020);
            int lockX = x + 7;
            int lockY = y + 6;
            int shackle = 0xFFC0C0C0;
            Gui.drawRect(lockX + 2, lockY, lockX + 7, lockY + 1, shackle);
            Gui.drawRect(lockX + 1, lockY + 1, lockX + 2, lockY + 4, shackle);
            Gui.drawRect(lockX + 7, lockY + 1, lockX + 8, lockY + 4, shackle);
            Gui.drawRect(lockX, lockY + 4, lockX + 9, lockY + 10, 0xFFE0A000);
            Gui.drawRect(lockX + 4, lockY + 6, lockX + 5, lockY + 8, 0xFF402000);
        });
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!currenttip.isEmpty() || Locks.client()
            .isEmpty()) {
            return currenttip;
        }
        Slot slot = GuiContainerManager.getSlotMouseOver(gui);
        if (slot == null || slot.getHasStack()) {
            return currenttip;
        }
        ItemStack blocked = blockedOutput(gui, slot);
        if (blocked == null) {
            return currenttip;
        }
        currenttip.add(EnumChatFormatting.RED + "✖ " + I18n.format("gtblocker.tooltip.locked"));
        currenttip.add(EnumChatFormatting.GRAY + I18n.format("gtblocker.tooltip.would_craft", blocked.getDisplayName()));
        LockKey key = Locks.client()
            .keyFor(blocked);
        Party party = key == null ? null : ClientState.partyFor(key);
        if (party != null) {
            currenttip.add(
                EnumChatFormatting.GOLD + "✦ "
                    + I18n.format(
                        "gtblocker.tooltip.unlocks",
                        Times.clock(party.revealAt()),
                        Times.until(party.revealAt())));
        }
        return currenttip;
    }

    private static @Nullable ItemStack blockedOutput(GuiContainer gui, Slot slot) {
        InventoryCrafting grid = gridFor(gui.inventorySlots, slot);
        return grid == null ? null : BlockedCraftMemo.blockedOutput(grid);
    }

    /** The grid feeding a result slot: SlotCrafting knows it; other result slots get the container's only grid. */
    private static @Nullable InventoryCrafting gridFor(Container container, Slot slot) {
        boolean resultSlot = slot instanceof SlotCrafting || slot.inventory instanceof InventoryCraftResult;
        if (!resultSlot) {
            return null;
        }
        if (slot instanceof SlotCrafting && craftMatrix != null) {
            try {
                if (craftMatrix.get(slot) instanceof InventoryCrafting grid) {
                    return grid;
                }
            } catch (IllegalAccessException ignored) {
                // findField made it accessible; fall through to the scan.
            }
        }
        InventoryCrafting found = null;
        for (Object object : container.inventorySlots) {
            IInventory inventory = ((Slot) object).inventory;
            if (inventory instanceof InventoryCrafting grid && grid != found) {
                if (found != null) {
                    return null;
                }
                found = grid;
            }
        }
        return found;
    }

    @Override
    public void onPreDraw(GuiContainer gui) {}

    @Override
    public void renderObjects(GuiContainer gui, int mousex, int mousey) {}

    @Override
    public void postRenderObjects(GuiContainer gui, int mousex, int mousey) {}

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}
}
