package dev.cactusguard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats durations like 30m, 2h, 7d, 1d6h30m. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdwy])", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL = Pattern.compile("(?:\\d+[smhdwy])+", Pattern.CASE_INSENSITIVE);

    private Durations() {}

    public static boolean isDuration(String s) { return s != null && FULL.matcher(s).matches(); }

    /** @return milliseconds, or -1 if the input is not a valid duration. */
    public static long parse(String s) {
        if (!isDuration(s)) return -1;
        long total = 0;
        Matcher m = PART.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += n * switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> 31_536_000_000L; // y
            };
        }
        return total;
    }

    public static String format(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
