package com.enn3developer.gtblocker.net;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.GTBlocker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: something happened (a reveal, a reminder, a first craft...). The client turns events into chat
 * lines and effects itself, so item names and times come out in each player's own language and time zone.
 */
public final class EventMessage extends NbtMessage {

    public EventMessage() {}

    public EventMessage(String type, NBTTagCompound data) {
        tag.setString("type", type);
        tag.setTag("data", data);
    }

    public static final class Handler implements IMessageHandler<EventMessage, IMessage> {

        @Override
        public IMessage onMessage(EventMessage message, MessageContext ctx) {
            GTBlocker.proxy.onEvent(
                message.tag.getString("type"),
                message.tag.getCompoundTag("data"));
            return null;
        }
    }
}
