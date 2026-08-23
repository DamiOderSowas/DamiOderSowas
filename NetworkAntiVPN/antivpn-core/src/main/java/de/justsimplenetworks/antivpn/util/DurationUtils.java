package de.justsimplenetworks.antivpn.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses short human-friendly duration strings used throughout admin commands
 * and config, e.g. {@code 30s}, {@code 10m}, {@code 1h}, {@code 1d}, {@code 7d}.
 * Multiple segments may be combined, e.g. {@code 1d12h30m}.
 */
public final class DurationUtils {

    private DurationUtils() {
    }

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a duration string such as {@code "30m"} or {@code "1d12h"}.
     *
     * @throws IllegalArgumentException if the string is empty, malformed, or resolves to zero/negative
     */
    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Duration string must not be empty");
        }
        String trimmed = text.trim();
        Matcher matcher = SEGMENT.matcher(trimmed);
        Duration total = Duration.ZERO;
        int matchedChars = 0;
        while (matcher.find()) {
            if (matcher.start() != matchedChars) {
                throw new IllegalArgumentException("Invalid duration string: " + text);
            }
            long amount = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            total = total.plus(switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(amount * 7L);
                default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
            });
            matchedChars = matcher.end();
        }
        if (matchedChars != trimmed.length() || matchedChars == 0) {
            throw new IllegalArgumentException("Invalid duration string: " + text);
        }
        if (total.isZero() || total.isNegative()) {
            throw new IllegalArgumentException("Duration must be positive: " + text);
        }
        return total;
    }

    /** Formats a duration back into the short form (e.g. {@code 1d2h3m4s}), omitting zero components. */
    public static String format(Duration duration) {
        if (duration.isZero()) {
            return "0s";
        }
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append('d');
        if (hours > 0) sb.append(hours).append('h');
        if (minutes > 0) sb.append(minutes).append('m');
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append('s');
        return sb.toString();
    }
}
