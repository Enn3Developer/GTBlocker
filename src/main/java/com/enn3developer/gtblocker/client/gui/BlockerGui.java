package com.enn3developer.gtblocker.client.gui;

import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.UISettings;
import com.enn3developer.gtblocker.client.ClientState;

/** Opens the GTBlocker screen. Client main thread only. */
public final class BlockerGui {

    private BlockerGui() {}

    public static void open() {
        if (!ClientState.synced()) {
            return;
        }
        UISettings settings = new UISettings();
        // A client-only container screen, so NEI shows up next to it and can drop items into it.
        settings.customContainer(ModularContainer::new);
        settings.getRecipeViewerSettings()
            .enable();
        ClientGUI.open(new BlockerScreen(), settings);
    }
}
