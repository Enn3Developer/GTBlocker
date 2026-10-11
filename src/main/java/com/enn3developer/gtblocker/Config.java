package com.enn3developer.gtblocker;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

/**
 * {@code config/gtblocker.cfg}: tuning knobs only. Locks, admins and parties live in the world save (see
 * {@link com.enn3developer.gtblocker.data.DataStore}), so a pack update replacing {@code config/} only resets these.
 */
public final class Config {

    private static final String SERVER = "server";
    private static final String CLIENT = "client";

    // Server
    public static int partyCountdownSeconds = 10;
    public static int[] reminderMinutes = { 60, 30, 10, 5, 1 };
    public static boolean partyVoice = true;
    public static int voiceLeadMinutes = 5;
    public static int voiceAfterMinutes = 30;
    public static int historyLimit = 500;

    // Client
    public static boolean revealTitle = true;
    public static boolean revealScreenEffect = true;
    public static boolean fireworks = true;
    public static boolean fanfare = true;
    public static boolean countdownHud = true;
    public static int hudLeadMinutes = 15;
    public static boolean lockIcon = true;
    public static boolean lockTooltip = true;

    private Config() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);

        partyCountdownSeconds = config.getInt(
            "partyCountdownSeconds",
            SERVER,
            partyCountdownSeconds,
            3,
            300,
            "Countdown length when an admin starts a party right away.");
        int[] reminders = config.get(
            SERVER,
            "reminderMinutes",
            reminderMinutes,
            "Minutes before a scheduled party when everyone gets a chat reminder.")
            .getIntList();
        reminderMinutes = Arrays.stream(reminders)
            .filter(m -> m > 0)
            .sorted()
            .toArray();
        partyVoice = config.getBoolean(
            "partyVoice",
            SERVER,
            partyVoice,
            "With GTNH Voice installed, put everyone online into the Unlock Party voice group around a reveal.");
        voiceLeadMinutes = config
            .getInt("voiceLeadMinutes", SERVER, voiceLeadMinutes, 0, 120, "Minutes before a reveal the voice group opens.");
        voiceAfterMinutes = config
            .getInt("voiceAfterMinutes", SERVER, voiceAfterMinutes, 0, 240, "Minutes after a reveal the voice group stays open.");
        historyLimit = config.getInt("historyLimit", SERVER, historyLimit, 10, 100000, "Unlock history entries kept.");

        revealTitle = config.getBoolean("revealTitle", CLIENT, revealTitle, "Show the big UNLOCKED title on reveals.");
        revealScreenEffect = config.getBoolean(
            "revealScreenEffect",
            CLIENT,
            revealScreenEffect,
            "Full-screen warp effect on reveals. Turn off if motion or flashes bother you.");
        fireworks = config.getBoolean("fireworks", CLIENT, fireworks, "Firework shows on reveals.");
        fanfare = config.getBoolean("fanfare", CLIENT, fanfare, "Play the fanfare and countdown ticks.");
        countdownHud = config.getBoolean("countdownHud", CLIENT, countdownHud, "Show the party countdown on the HUD.");
        hudLeadMinutes = config.getInt(
            "hudLeadMinutes",
            CLIENT,
            hudLeadMinutes,
            1,
            1440,
            "Minutes before a reveal the HUD countdown appears.");
        lockIcon = config
            .getBoolean("lockIcon", CLIENT, lockIcon, "Draw a lock on an empty crafting result that is locked.");
        lockTooltip = config.getBoolean("lockTooltip", CLIENT, lockTooltip, "Mention locks in item tooltips.");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
