package com.enn3developer.gtblocker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

@Mod(
    modid = GTBlocker.MODID,
    version = Tags.VERSION,
    name = "GTBlocker",
    acceptedMinecraftVersions = "[1.7.10]",
    // GTNHLib renders the reveal and MUI2 the admin GUI, both always present in GTNH. The rest are optional
    // integrations; "after" only orders init so their registries exist when ours look them up.
    dependencies = "required-after:gtnhlib;required-after:modularui2;after:gregtech;after:appliedenergistics2;"
        + "after:NotEnoughItems;after:gtnhvoice")
public class GTBlocker {

    public static final String MODID = "gtblocker";
    public static final Logger LOG = LogManager.getLogger("GTBlocker");

    @SidedProxy(
        clientSide = "com.enn3developer.gtblocker.ClientProxy",
        serverSide = "com.enn3developer.gtblocker.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        proxy.serverStopped(event);
    }
}
