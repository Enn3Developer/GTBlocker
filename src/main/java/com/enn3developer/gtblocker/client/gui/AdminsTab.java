package com.enn3developer.gtblocker.client.gui;

import net.minecraft.client.resources.I18n;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.enn3developer.gtblocker.client.ClientState;

/** The mod's own admin list. Admins add and remove admins, themselves included. */
final class AdminsTab {

    private final ParentWidget<?> area = new ParentWidget<>();
    private String name = "";

    IWidget build() {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(3);
        if (ClientState.isAdmin()) {
            column.child(
                Flow.row()
                    .widthRel(1f)
                    .height(Ui.ROW)
                    .childPadding(3)
                    .child(
                        new TextFieldWidget().expanded()
                            .height(Ui.ROW)
                            .value(new StringValue.Dynamic(() -> name, text -> name = text))
                            .autoUpdateOnChange(true)
                            .setMaxLength(36)
                            .hintText(I18n.format("gtblocker.gui.player_hint")))
                    .child(
                        Ui.button(
                            "gtblocker.gui.add",
                            () -> !name.trim()
                                .isEmpty(),
                            () -> {
                                Actions.addAdmin(name.trim());
                                name = "";
                            })
                            .width(60)));
        }
        column.child(
            area.widthRel(1f)
                .expanded());
        column.child(
            Ui.hint(ClientState.isAdmin() ? "gtblocker.gui.admins_hint" : "gtblocker.gui.admins_hint_player")
                .widthRel(1f)
                .height(18)
                .scale(0.8f));
        return column;
    }

    void refresh() {
        area.removeAll();
        if (ClientState.admins()
            .isEmpty()) {
            area.child(
                new TextWidget<>(IKey.lang("gtblocker.gui.no_admins")).color(Ui.GRAY)
                    .sizeRel(1f)
                    .textAlign(Alignment.Center));
        } else {
            ListWidget<IWidget, ?> list = Ui.list()
                .sizeRel(1f);
            for (String admin : ClientState.admins()) {
                Flow row = Flow.row()
                    .widthRel(1f)
                    .height(Ui.ROW)
                    .childPadding(3)
                    .child(
                        new TextWidget<>(IKey.str(admin)).expanded()
                            .textAlign(Alignment.CenterLeft));
                if (ClientState.isAdmin()) {
                    row.child(
                        Ui.button(IKey.lang("gtblocker.gui.remove"), () -> Actions.removeAdmin(admin))
                            .width(60));
                }
                list.child(row);
            }
            area.child(list);
        }
        area.scheduleResize();
    }
}
