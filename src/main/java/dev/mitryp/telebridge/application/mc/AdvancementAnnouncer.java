package dev.mitryp.telebridge.application.mc;

import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import net.minecraft.advancements.DisplayInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Collects each player's advancements over a short window and posts them as one message. Server thread only. */
public final class AdvancementAnnouncer {
    private static final long WINDOW_MS = 5000;
    private static final int MAX_LISTED = 15;

    private record Batch(long startedAt, List<DisplayInfo> earned) {
    }

    private final TelegramGateway telegram;
    private final Map<String, Batch> byPlayer = new LinkedHashMap<>();

    public AdvancementAnnouncer(TelegramGateway telegram) {
        this.telegram = telegram;
    }

    public void add(String player, DisplayInfo display) {
        byPlayer.computeIfAbsent(player, p -> new Batch(System.currentTimeMillis(), new ArrayList<>()))
                .earned.add(display);
    }

    public void flushDue() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Batch>> it = byPlayer.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().startedAt < WINDOW_MS) continue;
            telegram.sendService(format(e.getKey(), e.getValue().earned));
            it.remove();
        }
    }

    public void flush(String player) {
        Batch b = byPlayer.remove(player);
        if (b != null) telegram.sendService(format(player, b.earned));
    }

    public void flushAll() {
        byPlayer.forEach((player, b) -> telegram.sendService(format(player, b.earned)));
        byPlayer.clear();
    }

    private static String format(String player, List<DisplayInfo> earned) {
        if (earned.size() == 1) {
            DisplayInfo d = earned.get(0);
            return "> " + player + " " + verb(d) + " " + title(d);
        }
        String listed = earned.stream().limit(MAX_LISTED).map(AdvancementAnnouncer::title).collect(Collectors.joining(", "));
        if (earned.size() > MAX_LISTED) listed += " and " + (earned.size() - MAX_LISTED) + " more";
        return "> " + player + " earned " + earned.size() + " advancements: " + listed;
    }

    private static String verb(DisplayInfo d) {
        return switch (d.getFrame()) {
            case TASK -> "has made the advancement";
            case GOAL -> "has reached the goal";
            case CHALLENGE -> "has completed the challenge";
        };
    }

    private static String title(DisplayInfo d) {
        return "[" + d.getTitle().getString() + "]";
    }
}
