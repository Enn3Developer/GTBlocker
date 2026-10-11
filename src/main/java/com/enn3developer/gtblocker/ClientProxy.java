package com.enn3developer.gtblocker;

import com.enn3developer.gtblocker.lock.Locks;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // preInit runs on the client main thread; IRecipe.matches calls from it read the client mirror.
        Locks.setClientThread(Thread.currentThread());
    }
}
