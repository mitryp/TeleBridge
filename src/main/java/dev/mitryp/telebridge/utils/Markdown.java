package dev.mitryp.telebridge.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Markdown {
    private static final String SPECIALS = "_*[]()~`>#+-=|{}.!";

    public static String escapeV2(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length() * 2);
        for (char c : s.toCharArray()) {
            if (SPECIALS.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Escape as MarkdownV2 but preserve a leading "> " on each line so Telegram renders it as a quote.
     */
    public static String escapeV2ServiceAware(String s) {
        if (s == null || s.isEmpty()) return s;
        return Arrays.stream(s.split("\n", -1))
                .map(line -> line.startsWith("> ") ? "> " + escapeV2(line.substring(2)) : escapeV2(line))
                .collect(Collectors.joining("\n"));
    }
}
