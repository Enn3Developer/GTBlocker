package com.enn3developer.gtblocker.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.lock.LockKey;

/** Every lock as an item grid; admins drop items from NEI onto it and act on a selection. */
final class LocksTab {

    private static final int COLUMNS = 14;

    private final BlockerView view;
    private final LockDropArea area = new LockDropArea();
    private String search = "";
    private String shownSearch = "";

    LocksTab(BlockerView view) {
        this.view = view;
    }

    IWidget build() {
        boolean admin = ClientState.isAdmin();
        Flow top = Flow.row()
            .widthRel(1f)
            .height(Ui.ROW)
            .childPadding(3);
        if (admin) {
            top.child(
                Ui.button("gtblocker.gui.lock_held", () -> heldKey() != null, () -> {
                    LockKey held = heldKey();
                    if (held != null) {
                        Actions.lock(held);
                    }
                })
                    .width(82));
        }
        top.child(
            new TextFieldWidget().expanded()
                .height(Ui.ROW)
                .value(new StringValue.Dynamic(() -> search, text -> search = text))
                .autoUpdateOnChange(true)
                .hintText(I18n.format("gtblocker.gui.search")));

        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(3)
            .child(top)
            .child(
                Ui.hint(admin ? "gtblocker.gui.locks_hint_admin" : "gtblocker.gui.locks_hint")
                    .widthRel(1f)
                    .height(9)
                    .scale(0.8f))
            .child(
                area.widthRel(1f)
                    .expanded());
        if (admin) {
            column.child(actions());
        }
        return column;
    }

    private IWidget actions() {
        return Flow.row()
            .widthRel(1f)
            .height(Ui.ROW)
            .childPadding(2)
            .child(Ui.button("gtblocker.gui.party_now", this::hasSelection, () -> {
                Actions.party(new ArrayList<>(view.selection), 0, "", false);
                view.selection.clear();
            })
                .expanded())
            .child(
                Ui.button("gtblocker.gui.schedule", this::hasSelection, () -> view.showTab(BlockerView.PARTIES_TAB))
                    .expanded())
            .child(Ui.button("gtblocker.gui.unlock", this::hasSelection, () -> {
                Actions.unlock(new ArrayList<>(view.selection));
                view.selection.clear();
            })
                .expanded())
            .child(Ui.button("gtblocker.gui.all_metas", () -> singleExact() != null, () -> {
                LockKey key = singleExact();
                if (key != null) {
                    Actions.relock(key, new LockKey(key.itemId(), LockKey.ANY_META));
                    view.selection.clear();
                }
            })
                .expanded())
            .child(
                Ui.button("gtblocker.gui.clear", this::hasSelection, view.selection::clear)
                    .expanded());
    }

    /** Rebuilds the grid when the search text changed. */
    void tick() {
        if (!search.equals(shownSearch)) {
            refresh();
        }
    }

    void refresh() {
        shownSearch = search;
        area.removeAll();
        List<LockKey> keys = filtered();
        if (keys.isEmpty()) {
            area.child(
                new TextWidget<>(
                    IKey.lang(
                        ClientState.locks()
                            .isEmpty() ? "gtblocker.gui.no_locks" : "gtblocker.gui.no_match"))
                                .color(Ui.GRAY)
                                .sizeRel(1f)
                                .textAlign(Alignment.Center));
        } else {
            boolean admin = ClientState.isAdmin();
            List<IWidget> cells = new ArrayList<>(keys.size());
            for (LockKey key : keys) {
                cells.add(
                    new ItemCell(key, () -> view.selection.contains(key), admin ? button -> toggle(key) : null));
            }
            area.child(
                new Grid().sizeRel(1f)
                    .scrollable()
                    .minColWidth(18)
                    .minRowHeight(18)
                    .gridOfWidthElements(COLUMNS, cells, (column, row, index, cell) -> cell));
        }
        area.scheduleResize();
    }

    private void toggle(LockKey key) {
        if (!view.selection.remove(key)) {
            view.selection.add(key);
        }
    }

    private boolean hasSelection() {
        return !view.selection.isEmpty();
    }

    private @Nullable LockKey singleExact() {
        if (view.selection.size() != 1) {
            return null;
        }
        LockKey key = view.selection.iterator()
            .next();
        return key.anyMeta() ? null : key;
    }

    private List<LockKey> filtered() {
        String needle = search.trim()
            .toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return ClientState.locks();
        }
        List<LockKey> matches = new ArrayList<>();
        for (LockKey key : ClientState.locks()) {
            ItemStack stack = key.displayStack();
            String name = stack == null ? "" : stack.getDisplayName();
            if (name.toLowerCase(Locale.ROOT)
                .contains(needle)
                || key.itemId()
                    .toLowerCase(Locale.ROOT)
                    .contains(needle)) {
                matches.add(key);
            }
        }
        return matches;
    }

    private static @Nullable LockKey heldKey() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : LockKey.of(mc.thePlayer.getHeldItem(), false);
    }
}
