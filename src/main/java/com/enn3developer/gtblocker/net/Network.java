package com.enn3developer.gtblocker.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.GTBlocker;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class Network {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(GTBlocker.MODID);

    private Network() {}

    public static void register() {
        CHANNEL.registerMessage(StateMessage.Handler.class, StateMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(EventMessage.Handler.class, EventMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(OpenGuiMessage.Handler.class, OpenGuiMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(ActionMessage.Handler.class, ActionMessage.class, 3, Side.SERVER);
    }

    public static void sendEvent(EntityPlayerMP player, String type, NBTTagCompound data) {
        CHANNEL.sendTo(new EventMessage(type, data), player);
    }

    public static void broadcastEvent(String type, NBTTagCompound data) {
        CHANNEL.sendToAll(new EventMessage(type, data));
    }

    public static void sendAction(NBTTagCompound action) {
        CHANNEL.sendToServer(new ActionMessage(action));
    }
}
