package com.enn3developer.gtblocker.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.client.Times;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.LockKey;

/**
 * One item in a slot frame: the real item tooltip plus the lock's details, NEI's recipe/usage keys while hovered,
 * and an optional click handler (selection in the Locks tab).
 */
final class ItemCell extends Widget<ItemCell> implements Interactable, RecipeViewerIngredientProvider {

    private static final int SELECTED = 0x804080FF;
    private static final int PARTY_MARK = 0xFFFFAA00;

    private final LockKey key;
    private final @Nullable ItemStack stack;
    private final BooleanSupplier selected;
    private final @Nullable Consumer<Integer> onClick;

    ItemCell(LockKey key, BooleanSupplier selected, @Nullable Consumer<Integer> onClick) {
        this.key = key;
        this.stack = key.displayStack();
        this.selected = selected;
        this.onClick = onClick;
        size(18);
        background(GuiTextures.SLOT_ITEM);
        tooltipDynamic(tooltip -> {
            if (stack != null) {
                tooltip.addFromItem(stack);
            } else {
                tooltip.addLine(EnumChatFormatting.RED + key.itemId());
            }
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + key.serialize());
            Party party = ClientState.partyFor(key);
            if (party != null) {
                tooltip.addLine(
                    EnumChatFormatting.GOLD + net.minecraft.client.resources.I18n.format(
                        "gtblocker.gui.in_party",
                        party.id(),
                        Times.clock(party.revealAt())));
            }
        });
        tooltipAutoUpdate(true);
    }

    LockKey key() {
        return key;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (stack != null) {
            GuiDraw.drawItem(stack, 1, 1, 16, 16, context.getCurrentDrawingZ());
        }
        if (key.anyMeta()) {
            GuiDraw.drawScaledAlignedTextInBox(
                "*",
                1,
                1,
                16,
                16,
                com.cleanroommc.modularui.utils.Alignment.TopLeft,
                1.0f);
        }
        if (ClientState.partyFor(key) != null) {
            GuiDraw.drawRect(13, 1, 4, 4, PARTY_MARK);
        }
        if (selected.getAsBoolean()) {
            GuiDraw.drawRect(1, 1, 16, 16, SELECTED);
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (onClick == null) {
            return Result.IGNORE;
        }
        onClick.accept(mouseButton);
        Interactable.playButtonClickSound();
        return Result.SUCCESS;
    }

    @Override
    public @Nullable ItemStack getStackForRecipeViewer() {
        return stack;
    }
}
