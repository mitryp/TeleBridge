package dev.mitryp.telebridge.application.services;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** One-time codes proving that a Telegram user is also the Minecraft player who requested the code. */
public final class LinkCodes {
    private static final long TTL_SECONDS = 600;
    private static final int MAX_FAILURES = 5;

    private record Issued(String mcName, long expiresAt) {
    }

    private record Failures(int count, long windowEndsAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Issued> byCode = new HashMap<>();
    private final Map<Long, Failures> failures = new HashMap<>();

    /** Issues a fresh code for the player, replacing any earlier one. */
    public synchronized String issue(String mcName) {
        long now = Instant.now().getEpochSecond();
        byCode.values().removeIf(i -> i.expiresAt < now || i.mcName.equals(mcName));
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (byCode.containsKey(code));
        byCode.put(code, new Issued(mcName, now + TTL_SECONDS));
        return code;
    }

    /** Too many wrong codes recently; guards against guessing someone else's pending code. */
    public synchronized boolean lockedOut(long tgUserId) {
        Failures f = failures.get(tgUserId);
        return f != null && f.count >= MAX_FAILURES && f.windowEndsAt > Instant.now().getEpochSecond();
    }

    /** Returns the player the code was issued to, or null if it is wrong or expired. */
    public synchronized String redeem(long tgUserId, String code) {
        long now = Instant.now().getEpochSecond();
        Issued issued = byCode.remove(code.replaceAll("\\s", ""));
        if (issued != null && issued.expiresAt >= now) {
            failures.remove(tgUserId);
            return issued.mcName;
        }
        Failures f = failures.get(tgUserId);
        failures.put(tgUserId, f == null || f.windowEndsAt <= now
                ? new Failures(1, now + TTL_SECONDS)
                : new Failures(f.count + 1, f.windowEndsAt));
        return null;
    }
}
