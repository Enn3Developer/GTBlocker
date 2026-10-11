package com.enn3developer.gtblocker.compat.voice;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.GTBlocker;

import cpw.mods.fml.common.Loader;

/**
 * The "Unlock Party" voice group, seen from GTBlocker's side. The gtnh-voice types stay behind this interface so
 * nothing loads them unless the mod is installed.
 */
public interface PartyVoice {

    void join(UUID player);

    void leave(UUID player);

    void leaveAll();

    /** Registers the group with gtnh-voice for this server run, or returns null if voice is absent or disabled. */
    static @Nullable PartyVoice create() {
        if (!Config.partyVoice || !Loader.isModLoaded("gtnhvoice")) {
            return null;
        }
        try {
            return GtnhVoiceParty.register();
        } catch (Throwable t) {
            GTBlocker.LOG.warn("Could not register the party voice group with GTNH Voice", t);
            return null;
        }
    }
}
