package com.enn3developer.gtblocker.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** FML bus listener, registered once; forwards to the running {@link BlockerServer} if there is one. */
public final class ServerEvents {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        BlockerServer server = BlockerServer.get();
        if (event.phase == TickEvent.Phase.END && server != null) {
            server.tick();
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BlockerServer server = BlockerServer.get();
        if (server != null && event.player instanceof EntityPlayerMP player) {
            server.onLogin(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BlockerServer server = BlockerServer.get();
        if (server != null && event.player instanceof EntityPlayerMP player) {
            server.onLogout(player);
        }
    }

    /** Hand crafts only: AE2's Molecular Assembler fires this too, but with a fake player, which doesn't count. */
    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        BlockerServer server = BlockerServer.get();
        if (server != null && event.player instanceof EntityPlayerMP player && !(player instanceof FakePlayer)) {
            server.onCrafted(player.getCommandSenderName(), event.crafting);
        }
    }
}
