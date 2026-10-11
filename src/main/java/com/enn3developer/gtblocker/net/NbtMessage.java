package com.enn3developer.gtblocker.net;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Every GTBlocker packet is one NBT compound: easy to extend, and its size is bounded by FML's NBT reader. */
public abstract class NbtMessage implements IMessage {

    protected NBTTagCompound tag = new NBTTagCompound();

    public NBTTagCompound tag() {
        return tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound read = ByteBufUtils.readTag(buf);
        tag = read != null ? read : new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, tag);
    }
}
