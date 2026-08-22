package de.justsimplenetworks.antivpn.security;

/**
 * Strips characters that could be used for log injection / log forging
 * (CRLF-based fake log line injection, ANSI escape sequences, other control
 * characters) from any value that ultimately originates from a player or an
 * external provider response before it is written to a log line.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    private static final int MAX_LENGTH = 256;

    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(Math.min(input.length(), MAX_LENGTH));
        int limit = Math.min(input.length(), MAX_LENGTH);
        for (int i = 0; i < limit; i++) {
            char c = input.charAt(i);
            if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else if (c == '\t') {
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7F) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        if (input.length() > MAX_LENGTH) {
            sb.append("...(truncated)");
        }
        return sb.toString();
    }
}
