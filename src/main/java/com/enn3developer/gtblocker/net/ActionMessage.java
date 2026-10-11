package com.enn3developer.gtblocker.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.server.BlockerServer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: a GUI action. Arrives on the Netty thread, so it is queued onto the server tick; the server
 * re-checks the sender's permission for every action, whatever the client GUI showed.
 */
public final class ActionMessage extends NbtMessage {

    public ActionMessage() {}

    public ActionMessage(NBTTagCompound tag) {
        this.tag = tag;
    }

    public static final class Handler implements IMessageHandler<ActionMessage, IMessage> {

        @Override
        public IMessage onMessage(ActionMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            BlockerServer server = BlockerServer.get();
            if (server != null) {
                server.enqueue(() -> server.handleAction(player, message.tag));
            }
            return null;
        }
    }
}
