package com.enn3developer.gtblocker.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.lock.LockKey;

/**
 * The party countdown: a line at the top of the screen with the items coming up, from {@link Config#hudLeadMinutes}
 * before the reveal, then big numbers and ticks for the last ten seconds. Drawn with plain GL calls, all state
 * saved and restored, so nothing leaks into whatever renders after the HUD.
 */
public final class CountdownHud {

    private static final int MAX_ICONS = 8;
    private static final int FINAL_SECONDS = 10;
    private static final RenderItem ITEMS = new RenderItem();

    private static int lastPartyId;
    private static int lastSecond = -1;

    private CountdownHud() {}

    /** Client tick: the final countdown's numbers and sounds. */
    static void tick() {
        Party party = ClientState.nextParty();
        if (party == null || !Config.countdownHud) {
            lastSecond = -1;
            return;
        }
        long left = party.revealAt() - ClientState.serverNow();
        int second = (int) ((left + 999) / 1000);
        if (party.id() != lastPartyId) {
            lastPartyId = party.id();
            lastSecond = -1;
        }
        if (second < 1 || second > FINAL_SECONDS || second == lastSecond) {
            return;
        }
        lastSecond = second;
        TitleShow.countdown(second);
        if (Config.fanfare) {
            if (second <= 3) {
                Sounds.play("note.pling", 0.8f, Sounds.notePitch(18 - second * 2));
            } else {
                Sounds.play("note.hat", 0.6f, Sounds.notePitch(12));
            }
        }
    }

    static void render(ScaledResolution resolution) {
        Party party = ClientState.nextParty();
        if (party == null || !Config.countdownHud) {
            return;
        }
        long left = party.revealAt() - ClientState.serverNow();
        if (left <= 0 || left > Config.hudLeadMinutes * 60_000L) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        boolean urgent = left <= 60_000;
        boolean blink = urgent && (System.currentTimeMillis() / 500) % 2 == 0;
        EnumChatFormatting color = blink ? EnumChatFormatting.YELLOW : EnumChatFormatting.GOLD;
        String text = color + "✦ " + I18n.format("gtblocker.hud.countdown", Times.countdown(left)) + " ✦";

        int centerX = resolution.getScaledWidth() / 2;
        // Below the boss bar when there is one.
        int y = BossStatus.bossName != null && BossStatus.statusBarTime > 0 ? 22 : 3;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, 0xFFFFFF);
            drawIcons(mc, party.items(), centerX, y + 11);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void drawIcons(Minecraft mc, List<LockKey> keys, int centerX, int y) {
        int shown = Math.min(keys.size(), MAX_ICONS);
        String more = keys.size() > shown ? "+" + (keys.size() - shown) : "";
        FontRenderer font = mc.fontRenderer;
        int width = shown * 17 + (more.isEmpty() ? 0 : font.getStringWidth(more) + 2);
        int x = centerX - width / 2;

        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < shown; i++) {
            ItemStack stack = keys.get(i)
                .displayStack();
            if (stack != null) {
                ITEMS.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x + i * 17, y);
            }
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        if (!more.isEmpty()) {
            font.drawStringWithShadow(more, x + shown * 17 + 2, y + 4, 0xFFFFFF);
        }
    }
}
