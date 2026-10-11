package com.enn3developer.gtblocker.client.gui;

import java.util.function.BooleanSupplier;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

/** Shared widget building blocks for the GTBlocker screen. */
final class Ui {

    static final int ROW = 16;
    static final int GRAY = 0xFFA0A0A0;
    static final int GOLD = 0xFFFFAA00;
    static final int GREEN = 0xFF55FF55;
    static final int AQUA = 0xFF55FFFF;

    private Ui() {}

    /** A button that runs {@code action} on left click. */
    static ButtonWidget<?> button(IKey label, Runnable action) {
        return new ButtonWidget<>().height(ROW)
            .overlay(label)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) {
                    return false;
                }
                action.run();
                return true;
            });
    }

    /** A button that greys out its label and ignores clicks while {@code active} is false. */
    static ButtonWidget<?> button(String langKey, BooleanSupplier active, Runnable action) {
        IKey label = IKey.dynamic(
            () -> (active.getAsBoolean() ? "" : EnumChatFormatting.DARK_GRAY.toString())
                + net.minecraft.client.resources.I18n.format(langKey));
        return button(label, () -> {
            if (active.getAsBoolean()) {
                action.run();
            }
        });
    }

    static TextWidget<?> text(IKey key) {
        return new TextWidget<>(key).textAlign(Alignment.CenterLeft);
    }

    static TextWidget<?> hint(String langKey, Object... args) {
        return new TextWidget<>(IKey.lang(langKey, args)).color(GRAY)
            .textAlign(Alignment.TopLeft);
    }

    /** Vertical list with a small gap between rows. */
    static ListWidget<IWidget, ?> list() {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.childSeparator(
            IDrawable.EMPTY.asIcon()
                .size(1, 3));
        return list;
    }
}
