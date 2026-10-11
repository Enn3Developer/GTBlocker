package com.enn3developer.gtblocker.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.text.Chat;
import com.gtnewhorizon.gtnhlib.client.title.TitleAPI;
import com.gtnewhorizon.gtnhlib.client.title.TitleEffectSystem;
import com.gtnewhorizon.gtnhlib.client.title.TitleParticleSystem;

/**
 * Big centered titles through GTNHLib's TitleAPI: the reveal sequence (one beat per item), the countdown numbers
 * and the first-craft cheer. TitleAPI shows one title at a time, so reveals are a queue of timed beats.
 */
public final class TitleShow {

    private static final int MAX_ITEM_BEATS = 8;
    /** Longer messages stay in chat; on screen they would run off the edges at title scale. */
    private static final int MAX_TITLE_MESSAGE = 40;

    private static final Deque<Beat> QUEUE = new ArrayDeque<>();
    private static int wait;

    private TitleShow() {}

    /** True while a reveal or cheer is on screen, so the countdown leaves the title alone. */
    public static boolean busy() {
        return wait > 0 || !QUEUE.isEmpty();
    }

    public static void stop() {
        QUEUE.clear();
        wait = 0;
    }

    /** Client tick: runs the next beat once the current one has had its time. */
    static void tick() {
        if (wait > 0) {
            wait--;
            return;
        }
        Beat next = QUEUE.poll();
        if (next != null) {
            next.show.run();
            wait = next.ticks;
        }
    }

    public static void reveal(List<LockKey> keys, String message) {
        if (!Config.revealTitle) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (LockKey key : keys) {
            ItemStack stack = key.displayStack();
            if (stack != null) {
                stacks.add(stack);
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        int effect = Config.revealScreenEffect ? TitleEffectSystem.EFFECT_WARP : TitleEffectSystem.EFFECT_NONE;
        QUEUE.clear();
        wait = 0;
        if (stacks.size() == 1) {
            ItemStack stack = stacks.get(0);
            QUEUE.add(
                new Beat(
                    98,
                    () -> show(
                        heading("gtblocker.title.unlocked"),
                        name(stack),
                        stack,
                        TitleAPI.ICON_ANIM_TADA,
                        TitleParticleSystem.PARTICLE_ITEM_CONFETTI,
                        effect,
                        8,
                        70,
                        20)));
        } else {
            IChatComponent subtitle = !message.isEmpty() && message.length() <= MAX_TITLE_MESSAGE
                ? colored(new ChatComponentText(message), EnumChatFormatting.YELLOW)
                : Chat.tr(EnumChatFormatting.YELLOW, "gtblocker.title.party_count", stacks.size());
            ItemStack first = stacks.get(0);
            QUEUE.add(
                new Beat(
                    56,
                    () -> show(
                        heading("gtblocker.title.party"),
                        subtitle,
                        first,
                        TitleAPI.ICON_ANIM_SLAM,
                        TitleParticleSystem.PARTICLE_FIREWORK,
                        effect,
                        6,
                        44,
                        6)));
            int[] animations = { TitleAPI.ICON_ANIM_POP, TitleAPI.ICON_ANIM_BOUNCE, TitleAPI.ICON_ANIM_SPIN };
            int shown = Math.min(stacks.size(), MAX_ITEM_BEATS);
            for (int i = 0; i < shown; i++) {
                ItemStack stack = stacks.get(i);
                int animation = animations[i % animations.length];
                float pitch = 0.8f + 0.8f * i / Math.max(1, shown - 1);
                QUEUE.add(new Beat(40, () -> {
                    show(
                        heading("gtblocker.title.unlocked"),
                        name(stack),
                        stack,
                        animation,
                        TitleParticleSystem.PARTICLE_ITEM_CONFETTI,
                        TitleEffectSystem.EFFECT_NONE,
                        4,
                        30,
                        6);
                    if (Config.fanfare) {
                        Sounds.play("random.orb", 0.6f, pitch);
                    }
                }));
            }
            if (stacks.size() > shown) {
                int more = stacks.size() - shown;
                QUEUE.add(
                    new Beat(
                        56,
                        () -> show(
                            Chat.tr(EnumChatFormatting.GOLD, "gtblocker.title.more", more),
                            null,
                            null,
                            TitleAPI.ICON_ANIM_NONE,
                            TitleParticleSystem.PARTICLE_SPARKLE,
                            TitleEffectSystem.EFFECT_NONE,
                            4,
                            36,
                            16)));
            }
        }
        tick();
    }

    /** Queued after whatever is showing, so a first craft right after a reveal doesn't cut it short. */
    public static void firstCraft(ItemStack stack) {
        QUEUE.add(new Beat(70, () -> {
            show(
                heading("gtblocker.title.first_craft"),
                name(stack),
                stack,
                TitleAPI.ICON_ANIM_BOUNCE,
                TitleParticleSystem.PARTICLE_SPARKLE,
                TitleEffectSystem.EFFECT_NONE,
                5,
                50,
                15);
            if (Config.fanfare) {
                Sounds.play("random.levelup", 0.7f, 1.0f);
            }
        }));
        if (wait == 0) {
            tick();
        }
    }

    /** One second of the final countdown. */
    public static void countdown(int seconds) {
        if (busy()) {
            return;
        }
        IChatComponent number = new ChatComponentText(Integer.toString(seconds));
        number.getChatStyle()
            .setColor(seconds > 3 ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED)
            .setBold(true);
        show(
            number,
            Chat.tr(EnumChatFormatting.GOLD, "gtblocker.title.countdown"),
            null,
            TitleAPI.ICON_ANIM_NONE,
            TitleParticleSystem.PARTICLE_NONE,
            TitleEffectSystem.EFFECT_NONE,
            0,
            14,
            6);
        if (seconds <= 3) {
            TitleAPI.setTitleScale(6.0f);
        }
    }

    private static void show(IChatComponent title, @Nullable IChatComponent subtitle, @Nullable ItemStack icon,
        int animation, int particles, int effect, int fadeIn, int stay, int fadeOut) {
        TitleAPI.setTimes(fadeIn, stay, fadeOut);
        TitleAPI.setIcon(icon);
        TitleAPI.setIconScale(0);
        TitleAPI.setIconAnimation(animation);
        TitleAPI.setTitleScale(0);
        TitleAPI.setSubtitleScale(0);
        TitleAPI.setParticleEffect(particles);
        TitleAPI.setConfettiIcon(icon);
        TitleAPI.setParticleCount(-1);
        TitleAPI.setEffectTier(effect);
        TitleAPI.setSubtitle(subtitle);
        // Last: a new title object is what makes the renderer spawn this beat's particles and effect.
        TitleAPI.setTitle(title);
    }

    private static IChatComponent heading(String key) {
        IChatComponent heading = Chat.tr(EnumChatFormatting.GOLD, key);
        heading.getChatStyle()
            .setBold(true);
        return heading;
    }

    private static IChatComponent name(ItemStack stack) {
        return colored(new ChatComponentText(stack.getDisplayName()), EnumChatFormatting.AQUA);
    }

    private static IChatComponent colored(IChatComponent component, EnumChatFormatting color) {
        component.getChatStyle()
            .setColor(color);
        return component;
    }

    private record Beat(int ticks, Runnable show) {}
}
