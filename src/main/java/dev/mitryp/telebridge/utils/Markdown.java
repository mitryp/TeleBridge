package dev.mitryp.telebridge.utils;

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
     * Escape as MarkdownV2 but preserve a leading "> " so Telegram renders it as a quote.
     */
    public static String escapeV2ServiceAware(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.startsWith("> ")) {
            return "> " + escapeV2(s.substring(2));
        }
        return escapeV2(s);
    }
}
