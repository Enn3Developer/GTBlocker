package com.enn3developer.gtblocker.client.gui;

import java.time.ZoneId;
import java.util.ArrayList;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.client.Times;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.data.Spot;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.text.TimeParser;

/** Scheduling form (admins) over the list of upcoming parties. */
final class PartiesTab {

    private static final int MAX_ICONS = 13;

    private final BlockerScreen screen;
    private final ParentWidget<?> area = new ParentWidget<>();
    private String time = "";
    private String message = "";
    private boolean atMySpot;

    PartiesTab(BlockerScreen screen) {
        this.screen = screen;
    }

    IWidget build() {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(3);
        if (ClientState.isAdmin()) {
            column.child(
                new TextWidget<>(IKey.dynamic(this::selectionLine)).widthRel(1f)
                    .height(10)
                    .textAlign(Alignment.CenterLeft));
            column.child(
                Flow.row()
                    .widthRel(1f)
                    .height(Ui.ROW)
                    .childPadding(3)
                    .child(
                        new TextFieldWidget().width(70)
                            .height(Ui.ROW)
                            .value(new StringValue.Dynamic(() -> time, text -> time = text))
                            .autoUpdateOnChange(true)
                            .setMaxLength(16)
                            .hintText(I18n.format("gtblocker.gui.time_hint")))
                    .child(
                        new TextFieldWidget().expanded()
                            .height(Ui.ROW)
                            .value(new StringValue.Dynamic(() -> message, text -> message = text))
                            .autoUpdateOnChange(true)
                            .setMaxLength(120)
                            .hintText(I18n.format("gtblocker.gui.message_hint"))));
            column.child(
                Flow.row()
                    .widthRel(1f)
                    .height(Ui.ROW)
                    .childPadding(3)
                    .child(
                        new ToggleButton().expanded()
                            .height(Ui.ROW)
                            .value(new BoolValue.Dynamic(() -> atMySpot, value -> atMySpot = value))
                            .overlay(
                                IKey.lang(() -> atMySpot ? "gtblocker.gui.spot_here" : "gtblocker.gui.spot_none")))
                    .child(
                        Ui.button("gtblocker.gui.schedule", this::canSchedule, this::schedule)
                            .width(90)));
            column.child(
                new TextWidget<>(IKey.dynamic(this::preview)).widthRel(1f)
                    .height(10)
                    .textAlign(Alignment.CenterLeft));
        }
        column.child(
            area.widthRel(1f)
                .expanded());
        return column;
    }

    void refresh() {
        area.removeAll();
        if (ClientState.parties()
            .isEmpty()) {
            area.child(
                new TextWidget<>(IKey.lang("gtblocker.gui.no_parties")).color(Ui.GRAY)
                    .sizeRel(1f)
                    .textAlign(Alignment.Center));
        } else {
            ListWidget<IWidget, ?> list = Ui.list()
                .sizeRel(1f);
            for (Party party : ClientState.parties()) {
                list.child(row(party));
            }
            area.child(list);
        }
        area.scheduleResize();
    }

    private IWidget row(Party party) {
        boolean admin = ClientState.isAdmin();
        Flow head = Flow.row()
            .widthRel(1f)
            .height(Ui.ROW)
            .childPadding(2)
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> EnumChatFormatting.GOLD + "#" + party.id() + "  " + EnumChatFormatting.WHITE
                            + Times.clock(party.revealAt()) + EnumChatFormatting.GRAY + "  ("
                            + Times.until(party.revealAt()) + ")")).expanded()
                                .textAlign(Alignment.CenterLeft));
        if (admin) {
            head.child(
                Ui.button(IKey.lang("gtblocker.gui.start"), () -> Actions.startParty(party.id()))
                    .width(38))
                .child(
                    Ui.button(IKey.lang("gtblocker.gui.spot_set"), () -> Actions.partySpot(party.id(), false))
                        .width(38))
                .child(
                    Ui.button(IKey.lang("gtblocker.gui.cancel"), () -> Actions.cancelParty(party.id()))
                        .width(38));
        }

        Flow icons = Flow.row()
            .widthRel(1f)
            .height(18)
            .childPadding(1);
        int shown = Math.min(party.items()
            .size(), MAX_ICONS);
        for (int i = 0; i < shown; i++) {
            LockKey key = party.items()
                .get(i);
            icons.child(new ItemCell(key, () -> false, null));
        }
        if (party.items()
            .size() > shown) {
            icons.child(
                new TextWidget<>(IKey.str("+" + (party.items()
                    .size() - shown))).width(20)
                        .height(18)
                        .textAlign(Alignment.CenterLeft));
        }

        Flow block = Flow.column()
            .widthRel(1f)
            .coverChildrenHeight()
            .childPadding(1)
            .child(head)
            .child(icons);
        String details = details(party);
        if (!details.isEmpty()) {
            block.child(
                new TextWidget<>(IKey.str(details)).widthRel(1f)
                    .scale(0.8f)
                    .color(Ui.GRAY)
                    .textAlign(Alignment.TopLeft));
        }
        return block;
    }

    private static String details(Party party) {
        StringBuilder text = new StringBuilder();
        if (!party.message()
            .isEmpty()) {
            text.append('"')
                .append(party.message())
                .append("\"  ");
        }
        Spot spot = party.spot();
        if (spot != null) {
            text.append(I18n.format("gtblocker.gui.spot_at", spot.x(), spot.y(), spot.z(), spot.dim()));
        }
        return text.toString()
            .trim();
    }

    private String selectionLine() {
        int count = screen.selection.size();
        return count == 0 ? EnumChatFormatting.GRAY + I18n.format("gtblocker.gui.select_first")
            : EnumChatFormatting.AQUA + I18n.format("gtblocker.gui.selected", count);
    }

    private long revealAt() {
        return TimeParser.parse(time, ClientState.serverNow(), ZoneId.systemDefault());
    }

    private String preview() {
        long at = revealAt();
        if (at < 0) {
            return EnumChatFormatting.RED + I18n.format("gtblocker.gui.bad_time");
        }
        if (at == 0) {
            return EnumChatFormatting.GRAY
                + I18n.format("gtblocker.gui.preview_now", ClientState.countdownSeconds());
        }
        return EnumChatFormatting.GRAY + I18n.format("gtblocker.gui.preview_at", Times.clock(at), Times.until(at));
    }

    private boolean canSchedule() {
        return !screen.selection.isEmpty() && revealAt() >= 0;
    }

    private void schedule() {
        Actions.party(new ArrayList<>(screen.selection), revealAt(), message.trim(), atMySpot);
        screen.selection.clear();
        time = "";
        message = "";
    }
}
