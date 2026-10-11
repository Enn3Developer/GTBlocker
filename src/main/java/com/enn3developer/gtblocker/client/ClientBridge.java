package com.enn3developer.gtblocker.client;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.client.gui.BlockerGui;

/**
 * Client entry points, already on the main thread. A failure is logged, never fatal and never silent: tasks
 * scheduled through {@code Minecraft.func_152344_a} run as futures that swallow exceptions, and a throw from a key or
 * tick handler would take the whole client down. {@link LinkageError} too, for a mismatched MUI2 or NEI.
 */
public final class ClientBridge {

    private ClientBridge() {}

    public static void state(NBTTagCompound tag) {
        try {
            ClientState.apply(tag);
        } catch (RuntimeException | LinkageError e) {
            GTBlocker.LOG.error("Could not apply the GTBlocker state from the server", e);
        }
    }

    public static void event(String type, NBTTagCompound data) {
        try {
            Announcer.handle(type, data);
        } catch (RuntimeException | LinkageError e) {
            GTBlocker.LOG.error("Could not handle GTBlocker event {}", type, e);
        }
    }

    public static void openGui() {
        try {
            BlockerGui.open();
        } catch (RuntimeException | LinkageError e) {
            GTBlocker.LOG.error("Could not open the GTBlocker screen", e);
        }
    }
}
