package com.enn3developer.gtblocker.client.gui;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.client.ClientEvents;
import com.enn3developer.gtblocker.client.ClientState;
import com.enn3developer.gtblocker.lock.LockKey;

/**
 * The GTBlocker screen: Locks, Parties, History and Admins tabs over the client's copy of the server state.
 * Everyone can open it; non-admins get a read-only view (the server checks every action regardless). The lists
 * rebuild whenever a new state snapshot arrives, so the screen stays live while admins work on it together.
 */
final class BlockerScreen extends CustomModularScreen {

    static final int LOCKS_TAB = 0;
    static final int PARTIES_TAB = 1;
    static final int HISTORY_TAB = 2;
    static final int ADMINS_TAB = 3;

    private static final int WIDTH = 284;
    private static final int HEIGHT = 236;

    /** Locked items picked in the Locks tab; the Parties tab schedules them. */
    final Set<LockKey> selection = new LinkedHashSet<>();

    private final PagedWidget.Controller tabs = new PagedWidget.Controller();
    private final LocksTab locks = new LocksTab(this);
    private final PartiesTab parties = new PartiesTab(this);
    private final HistoryTab history = new HistoryTab();
    private final AdminsTab admins = new AdminsTab();
    private boolean builtAsAdmin;
    private boolean leaving;
    private int seenVersion = -1;

    BlockerScreen() {
        super(GTBlocker.MODID);
    }

    @Override
    public @NotNull ModularPanel buildUI(ModularGuiContext context) {
        builtAsAdmin = ClientState.isAdmin();
        ModularPanel panel = ModularPanel.defaultPanel("gtblocker")
            .size(WIDTH, HEIGHT);
        panel.child(
            Flow.column()
                .sizeRel(1f)
                .padding(7)
                .childPadding(4)
                .child(header())
                .child(
                    Flow.row()
                        .widthRel(1f)
                        .height(Ui.ROW)
                        .child(tab(LOCKS_TAB, IKey.lang("gtblocker.gui.tab.locks", () -> new Object[] { ClientState.locks().size() })))
                        .child(tab(PARTIES_TAB, IKey.lang("gtblocker.gui.tab.parties", () -> new Object[] { ClientState.parties().size() })))
                        .child(tab(HISTORY_TAB, IKey.lang("gtblocker.gui.tab.history")))
                        .child(tab(ADMINS_TAB, IKey.lang("gtblocker.gui.tab.admins"))))
                .child(
                    new PagedWidget<>().widthRel(1f)
                        .expanded()
                        .controller(tabs)
                        .addPage(locks.build())
                        .addPage(parties.build())
                        .addPage(history.build())
                        .addPage(admins.build())));
        panel.onUpdateListener(p -> tick(), true);
        refresh();
        return panel;
    }

    void showTab(int tab) {
        tabs.setPage(tab);
    }

    private IWidget header() {
        return Flow.row()
            .widthRel(1f)
            .height(12)
            .childPadding(4)
            .child(
                new TextWidget<>(IKey.str("GTBlocker")).style(EnumChatFormatting.BOLD)
                    .color(Ui.GOLD)
                    .width(60)
                    .textAlign(Alignment.CenterLeft))
            .child(
                new TextWidget<>(
                    IKey.lang(() -> ClientState.isAdmin() ? "gtblocker.gui.role_admin" : "gtblocker.gui.role_player"))
                        .color(() -> ClientState.isAdmin() ? Ui.GREEN : Ui.GRAY)
                        .expanded()
                        .textAlign(Alignment.CenterLeft))
            .child(
                Ui.button(
                    IKey.lang(() -> ClientState.inVoice() ? "gtblocker.gui.voice_on" : "gtblocker.gui.voice_off"),
                    () -> Actions.voice(!ClientState.inVoice()))
                    .width(80)
                    .height(12)
                    .setEnabledIf(w -> ClientState.voiceOpen()));
    }

    private IWidget tab(int index, IKey label) {
        return new PageButton(index, tabs).expanded()
            .height(Ui.ROW)
            .overlay(label);
    }

    private void tick() {
        if (leaving) {
            return;
        }
        if (!ClientState.synced()) {
            leaving = true;
            ClientEvents.runAfterTick(() -> close(true));
            return;
        }
        if (ClientState.isAdmin() != builtAsAdmin) {
            // Promoted or demoted while looking at it: rebuild with the right controls.
            leaving = true;
            ClientEvents.runAfterTick(BlockerGui::open);
            return;
        }
        if (ClientState.version() != seenVersion) {
            refresh();
        }
        locks.tick();
    }

    private void refresh() {
        seenVersion = ClientState.version();
        selection.retainAll(ClientState.locks());
        locks.refresh();
        parties.refresh();
        history.refresh();
        admins.refresh();
    }
}
