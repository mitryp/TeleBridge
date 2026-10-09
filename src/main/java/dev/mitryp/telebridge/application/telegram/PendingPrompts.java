package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Force-reply prompts awaiting an answer from the user who triggered them. */
public final class PendingPrompts {
    private static final long TTL_SECONDS = 600;

    private record Pending(long userId, long expiresAt, Consumer<TelegramInboundMessage> onAnswer) {
    }

    private final TelegramGateway tg;
    private final Map<Integer, Pending> byPromptId = new HashMap<>();

    public PendingPrompts(TelegramGateway tg) {
        this.tg = tg;
    }

    /** Asks the sender of {@code in} for input; the prompt is deleted once they answer. */
    public void ask(TelegramInboundMessage in, String question, String placeholder, Consumer<TelegramInboundMessage> onAnswer) {
        tg.sendPrompt(question, placeholder, in.messageId, in.threadId,
                promptId -> register(promptId, in.userId, answer -> {
                    tg.delete(promptId);
                    onAnswer.accept(answer);
                }));
    }

    private synchronized void register(int promptId, long userId, Consumer<TelegramInboundMessage> onAnswer) {
        long now = Instant.now().getEpochSecond();
        byPromptId.values().removeIf(p -> p.expiresAt < now);
        byPromptId.put(promptId, new Pending(userId, now + TTL_SECONDS, onAnswer));
    }

    /** Passes {@code reply} to the prompt it answers, if that prompt is live and belongs to the replying user. */
    public void answer(int promptId, TelegramInboundMessage reply) {
        Pending p;
        synchronized (this) {
            p = byPromptId.get(promptId);
            if (p == null || p.userId != reply.userId || p.expiresAt < Instant.now().getEpochSecond()) return;
            byPromptId.remove(promptId);
        }
        p.onAnswer.accept(reply);
    }
}
