package com.enn3developer.gtblocker.net;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.GTBlocker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** Server to client: the full lock/party/history/admin snapshot, sent on login and after every change. */
public final class StateMessage extends NbtMessage {

    public StateMessage() {}

    public StateMessage(NBTTagCompound tag) {
        this.tag = tag;
    }

    public static final class Handler implements IMessageHandler<StateMessage, IMessage> {

        @Override
        public IMessage onMessage(StateMessage message, MessageContext ctx) {
            GTBlocker.proxy.onState(message.tag);
            return null;
        }
    }
}
