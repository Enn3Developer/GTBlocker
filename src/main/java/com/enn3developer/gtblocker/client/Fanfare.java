package com.enn3developer.gtblocker.client;

import java.util.ArrayList;
import java.util.List;

import com.enn3developer.gtblocker.Config;

/**
 * A two-second note block fanfare for reveals: a "ta-ta-ta TAAA" call, a climbing answer, and a final C major chord
 * held with a tremolo, as note blocks can't sustain. Plays through the jukebox/note block volume slider.
 */
public final class Fanfare {

    private static final String HARP = "note.harp";
    private static final String BASS = "note.bassattack";
    private static final String DRUM = "note.bd";
    private static final String SNARE = "note.snare";
    private static final String HAT = "note.hat";
    private static final String PLING = "note.pling";

    // Note block clicks: 6 = C4, 10 = E4, 13 = G4, 15 = A4, 17 = B4, 18 = C5, 20 = D5, 22 = E5.
    private static final List<Hit> SCORE = new ArrayList<>();
    private static final int LENGTH;

    static {
        // Call: ta-ta-ta TAAA
        hit(0, HARP, 13, 0.9f).hit(0, BASS, 6, 0.8f)
            .hit(0, DRUM, 12, 0.7f);
        hit(2, HARP, 13, 0.75f);
        hit(4, HARP, 13, 0.9f);
        hit(6, HARP, 18, 1.0f).hit(6, HARP, 13, 0.6f)
            .hit(6, HARP, 10, 0.6f)
            .hit(6, BASS, 6, 0.9f)
            .hit(6, DRUM, 12, 0.8f)
            .hit(6, SNARE, 12, 0.6f);
        hit(9, HARP, 18, 0.5f);
        hit(12, HARP, 18, 0.35f);
        hit(14, HARP, 17, 0.8f).hit(14, BASS, 13, 0.7f);
        // Answer: climbing to the top
        hit(16, HARP, 15, 0.9f).hit(16, BASS, 11, 0.8f)
            .hit(16, DRUM, 12, 0.7f);
        hit(18, HARP, 17, 0.9f);
        hit(20, HARP, 18, 0.9f).hit(20, BASS, 15, 0.7f);
        hit(22, HARP, 20, 0.9f).hit(22, BASS, 13, 0.8f)
            .hit(22, SNARE, 12, 0.6f);
        // Final chord, held with a tremolo and a sparkle on top
        hit(24, HARP, 22, 1.0f).hit(24, HARP, 18, 0.8f)
            .hit(24, HARP, 13, 0.7f)
            .hit(24, BASS, 6, 1.0f)
            .hit(24, DRUM, 12, 0.9f)
            .hit(24, SNARE, 12, 0.7f)
            .hit(24, HAT, 18, 0.5f);
        float volume = 0.7f;
        for (int tick = 26; tick <= 36; tick += 2) {
            hit(tick, HARP, 22, volume).hit(tick, HARP, 18, volume * 0.8f);
            volume *= 0.82f;
        }
        hit(28, PLING, 13, 0.5f);
        hit(30, PLING, 18, 0.5f);
        hit(32, PLING, 22, 0.55f);
        // Closing hit
        hit(40, HARP, 18, 0.9f).hit(40, HARP, 22, 0.8f)
            .hit(40, HARP, 13, 0.7f)
            .hit(40, HARP, 6, 0.7f)
            .hit(40, BASS, 6, 1.0f)
            .hit(40, DRUM, 12, 1.0f)
            .hit(40, SNARE, 12, 0.8f);
        LENGTH = SCORE.stream()
            .mapToInt(Hit::tick)
            .max()
            .orElse(0) + 1;
    }

    private static int position = -1;

    private Fanfare() {}

    public static void play() {
        if (Config.fanfare) {
            position = 0;
        }
    }

    public static void stop() {
        position = -1;
    }

    /** Client tick. */
    static void tick() {
        if (position < 0) {
            return;
        }
        for (Hit hit : SCORE) {
            if (hit.tick == position) {
                Sounds.play(hit.sound, hit.volume, Sounds.notePitch(hit.note));
            }
        }
        position = position + 1 < LENGTH ? position + 1 : -1;
    }

    private static Hit hit(int tick, String sound, int note, float volume) {
        Hit hit = new Hit(tick, sound, note, volume);
        SCORE.add(hit);
        return hit;
    }

    private record Hit(int tick, String sound, int note, float volume) {

        Hit hit(int at, String sound, int note, float volume) {
            return Fanfare.hit(at, sound, note, volume);
        }
    }
}
