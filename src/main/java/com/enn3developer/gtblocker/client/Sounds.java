package com.enn3developer.gtblocker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

/** Sounds played "in the player's head": no position, no falloff, volume up to 1. */
public final class Sounds {

    private Sounds() {}

    public static void play(String name, float volume, float pitch) {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(new UiSound(new ResourceLocation(name), volume, pitch));
    }

    /** Note block pitch for {@code note} 0..24 (12 = unpitched sample), as vanilla note blocks compute it. */
    public static float notePitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    private static final class UiSound extends PositionedSound {

        UiSound(ResourceLocation location, float volume, float pitch) {
            super(location);
            this.volume = volume;
            this.field_147663_c = pitch;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }
}
