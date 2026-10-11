package com.enn3developer.gtblocker.text;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party times as typed by admins: {@code now}, a relative {@code +45m}/{@code 90s}/{@code 2h}/{@code 1h30m}, or a
 * wall-clock {@code 21:30} (in the given zone; tomorrow if that time already passed today).
 */
public final class TimeParser {

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2})[:.](\\d{2})");
    private static final Pattern RELATIVE = Pattern.compile("\\+?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private TimeParser() {}

    /** Epoch millis, 0 for "now", or -1 if the text isn't a time. */
    public static long parse(String text, long now, ZoneId zone) {
        String input = text.trim()
            .toLowerCase(Locale.ROOT);
        if (input.isEmpty() || input.equals("now")) {
            return 0;
        }
        Matcher clock = CLOCK.matcher(input);
        if (clock.matches()) {
            int hour = Integer.parseInt(clock.group(1));
            int minute = Integer.parseInt(clock.group(2));
            if (hour > 23 || minute > 59) {
                return -1;
            }
            ZonedDateTime today = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone);
            LocalDate date = today.toLocalDate();
            ZonedDateTime target = LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(zone);
            if (!target.toInstant()
                .isAfter(today.toInstant())) {
                target = target.plusDays(1);
            }
            return target.toInstant()
                .toEpochMilli();
        }
        Matcher relative = RELATIVE.matcher(input);
        if (relative.matches() && (relative.group(1) != null || relative.group(2) != null
            || relative.group(3) != null)) {
            long seconds = number(relative.group(1)) * 3600 + number(relative.group(2)) * 60
                + number(relative.group(3));
            return seconds > 0 ? now + seconds * 1000 : 0;
        }
        return -1;
    }

    private static long number(String group) {
        return group == null ? 0 : Long.parseLong(group);
    }
}
