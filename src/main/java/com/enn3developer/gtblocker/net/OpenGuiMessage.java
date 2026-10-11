package com.enn3developer.gtblocker.net;

import com.enn3developer.gtblocker.GTBlocker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** Server to client: open the GTBlocker screen (sent by {@code /gtblocker}). */
public final class OpenGuiMessage extends NbtMessage {

    public static final class Handler implements IMessageHandler<OpenGuiMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenGuiMessage message, MessageContext ctx) {
            GTBlocker.proxy.openGui();
            return null;
        }
    }
}
