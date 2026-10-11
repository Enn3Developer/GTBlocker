package com.enn3developer.gtblocker.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;

/** Party times for display: wall clock in the player's own time zone, plus "time left" on the server's clock. */
public final class Times {

    private Times() {}

    /** {@code 21:30} today, {@code Sat 21:30} within a week either way, {@code 2026-10-24 21:30} beyond. */
    public static String clock(long epochMillis) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis)
            .atZone(zone);
        LocalDate today = LocalDate.now(zone);
        long days = time.toLocalDate()
            .toEpochDay() - today.toEpochDay();
        String pattern = days == 0 ? "HH:mm" : Math.abs(days) < 7 ? "EEE HH:mm" : "yyyy-MM-dd HH:mm";
        return DateTimeFormatter.ofPattern(pattern, locale())
            .format(time);
    }

    /** {@code 2h 15m}, {@code 4m 05s}, {@code 42s}; never negative. */
    public static String until(long serverMillis) {
        return duration(serverMillis - ClientState.serverNow());
    }

    public static String duration(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        long secs = seconds % 60;
        if (hours >= 24) {
            return hours / 24 + "d " + hours % 24 + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, secs);
        }
        return secs + "s";
    }

    /** {@code 4:05} style, for the HUD. */
    public static String countdown(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** The game's language as a Java locale, for day names. */
    private static Locale locale() {
        Language language = Minecraft.getMinecraft()
            .getLanguageManager()
            .getCurrentLanguage();
        if (language == null) {
            return Locale.getDefault();
        }
        String[] parts = language.getLanguageCode()
            .split("_");
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }
}
