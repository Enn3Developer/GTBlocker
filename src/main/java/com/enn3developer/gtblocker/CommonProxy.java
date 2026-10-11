package com.enn3developer.gtblocker;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.command.CommandGTBlocker;
import com.enn3developer.gtblocker.core.RecipeMatchTransformer;
import com.enn3developer.gtblocker.net.Network;
import com.enn3developer.gtblocker.server.BlockerServer;
import com.enn3developer.gtblocker.server.ServerEvents;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.load(new File(event.getModConfigurationDirectory(), "gtblocker.cfg"));
        Network.register();
    }

    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerEvents());
    }

    public void postInit(FMLPostInitializationEvent event) {
        GTBlocker.LOG.info(
            "Gated IRecipe.matches in {} recipe classes",
            RecipeMatchTransformer.patchedClasses()
                .size());
    }

    public void serverStarting(FMLServerStartingEvent event) {
        BlockerServer.start(event.getServer());
        event.registerServerCommand(new CommandGTBlocker());
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        BlockerServer.stop();
    }

    /** Server to client snapshot; only the client proxy does anything with it. Called on the Netty thread. */
    public void onState(NBTTagCompound tag) {}

    /** Server to client event; only the client proxy does anything with it. Called on the Netty thread. */
    public void onEvent(String type, NBTTagCompound data) {}

    public void openGui() {}
}
