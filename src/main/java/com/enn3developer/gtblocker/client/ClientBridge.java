package com.enn3developer.gtblocker.client;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.GTBlocker;

/** Entry points for packets, already on the client main thread. A bad packet is logged, never fatal. */
public final class ClientBridge {

    private ClientBridge() {}

    public static void state(NBTTagCompound tag) {
        try {
            ClientState.apply(tag);
        } catch (RuntimeException e) {
            GTBlocker.LOG.error("Could not apply the GTBlocker state from the server", e);
        }
    }

    public static void event(String type, NBTTagCompound data) {
        try {
            Announcer.handle(type, data);
        } catch (RuntimeException e) {
            GTBlocker.LOG.error("Could not handle GTBlocker event {}", type, e);
        }
    }
}
