package com.enn3developer.gtblocker;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.enn3developer.gtblocker.client.ClientBridge;
import com.enn3developer.gtblocker.client.ClientEvents;
import com.enn3developer.gtblocker.client.RecipeLinkCommand;
import com.enn3developer.gtblocker.client.nei.NeiCompat;
import com.enn3developer.gtblocker.lock.Locks;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // preInit runs on the client main thread; IRecipe.matches calls from it read the client mirror.
        Locks.setClientThread(Thread.currentThread());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        ClientRegistry.registerKeyBinding(ClientEvents.OPEN_GUI);
        ClientCommandHandler.instance.registerCommand(new RecipeLinkCommand());
        if (Loader.isModLoaded("NotEnoughItems")) {
            NeiCompat.register();
        }
    }

    // Packets arrive on the network thread; everything client-side happens on the main thread, in arrival order.

    @Override
    public void onState(NBTTagCompound tag) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClientBridge.state(tag));
    }

    @Override
    public void onEvent(String type, NBTTagCompound data) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClientBridge.event(type, data));
    }

    @Override
    public void openGui() {
        Minecraft.getMinecraft()
            .func_152344_a(ClientBridge::openGui);
    }
}
