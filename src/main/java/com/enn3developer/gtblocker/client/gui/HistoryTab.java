package com.enn3developer.gtblocker.client.gui;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.client.Times;
import com.enn3developer.gtblocker.data.HistoryEntry;

/** Recent unlocks, newest first, with who unlocked them and who crafted them first. */
final class HistoryTab {

    private final ParentWidget<?> area = new ParentWidget<>();

    IWidget build() {
        return area.sizeRel(1f);
    }

    void refresh() {
        area.removeAll();
        List<HistoryEntry> history = ClientState.history();
        if (history.isEmpty()) {
            area.child(
                new TextWidget<>(IKey.lang("gtblocker.gui.no_history")).color(Ui.GRAY)
                    .sizeRel(1f)
                    .textAlign(Alignment.Center));
        } else {
            ListWidget<IWidget, ?> list = Ui.list()
                .sizeRel(1f);
            for (int i = history.size() - 1; i >= 0; i--) {
                list.child(row(history.get(i)));
            }
            area.child(list);
        }
        area.scheduleResize();
    }

    private static IWidget row(HistoryEntry entry) {
        ItemStack stack = entry.key()
            .displayStack();
        String name = stack == null ? entry.key()
            .serialize() : stack.getDisplayName();
        StringBuilder detail = new StringBuilder(Times.clock(entry.unlockedAt())).append(" · ")
            .append(entry.unlockedBy());
        if (entry.celebrated()) {
            detail.append(" · ")
                .append(I18n.format("gtblocker.gui.history_party", entry.partyId()));
        }
        if (entry.firstCraftBy() != null) {
            detail.append(" · ")
                .append(EnumChatFormatting.YELLOW)
                .append(I18n.format("gtblocker.gui.history_first", entry.firstCraftBy()));
        }
        return Flow.row()
            .widthRel(1f)
            .height(20)
            .childPadding(4)
            .child(new ItemCell(entry.key(), () -> false, null))
            .child(
                Flow.column()
                    .expanded()
                    .heightRel(1f)
                    .child(
                        new TextWidget<>(IKey.str(name)).color(Ui.AQUA)
                            .widthRel(1f)
                            .height(10)
                            .textAlign(Alignment.CenterLeft))
                    .child(
                        new TextWidget<>(IKey.str(detail.toString())).color(Ui.GRAY)
                            .scale(0.8f)
                            .widthRel(1f)
                            .height(9)
                            .textAlign(Alignment.CenterLeft)));
    }
}
