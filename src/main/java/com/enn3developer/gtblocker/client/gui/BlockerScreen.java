package com.enn3developer.gtblocker.client.gui;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.enn3developer.gtblocker.GTBlocker;

/**
 * The GTBlocker screen. MUI2 builds the main panel inside the {@code ModularScreen} constructor, before a subclass's
 * own fields are initialized, so everything the build touches lives in a {@link BlockerView} created first.
 */
final class BlockerScreen extends ModularScreen {

    final BlockerView view;

    BlockerScreen() {
        this(new BlockerView());
    }

    private BlockerScreen(BlockerView view) {
        super(GTBlocker.MODID, view::build);
        this.view = view;
    }
}
