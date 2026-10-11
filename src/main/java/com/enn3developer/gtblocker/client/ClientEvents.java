package com.enn3developer.gtblocker.client;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.lock.LockTable;
import com.enn3developer.gtblocker.lock.Locks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Client listeners, registered on both the Forge and the FML bus. */
public final class ClientEvents {

    /** Opens the GTBlocker screen; unbound by default. */
    public static final KeyBinding OPEN_GUI = new KeyBinding("key.gtblocker.open", 0, "key.categories.gtblocker");

    private static final Queue<Runnable> NEXT_TICK = new ArrayDeque<>();

    /**
     * Runs {@code task} at the end of this client tick, after the open screen's update. For screen switches requested
     * from inside a screen, which must not happen while that screen is still iterating its widgets.
     */
    public static void runAfterTick(Runnable task) {
        NEXT_TICK.add(task);
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        LockTable table = Locks.client();
        if (!Config.lockTooltip || table.isEmpty()) {
            return;
        }
        LockKey key = table.keyFor(event.itemStack);
        if (key == null) {
            return;
        }
        event.toolTip.add(EnumChatFormatting.RED + "✖ " + I18n.format("gtblocker.tooltip.locked"));
        Party party = ClientState.partyFor(key);
        if (party != null) {
            event.toolTip.add(
                EnumChatFormatting.GOLD + "✦ "
                    + I18n.format(
                        "gtblocker.tooltip.unlocks",
                        Times.clock(party.revealAt()),
                        Times.until(party.revealAt())));
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            CountdownHud.render(event.resolution);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        while ((task = NEXT_TICK.poll()) != null) {
            task.run();
        }
        CountdownHud.tick();
        TitleShow.tick();
        Fanfare.tick();
        FireworkShow.tick();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (OPEN_GUI.isPressed() && Minecraft.getMinecraft().currentScreen == null && ClientState.synced()) {
            ClientBridge.openGui();
        }
    }

    // Both fire on the network thread.

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(ClientEvents::resetSession);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(ClientEvents::resetSession);
    }

    private static void resetSession() {
        ClientState.reset();
        TitleShow.stop();
        Fanfare.stop();
        FireworkShow.stop();
    }
}
