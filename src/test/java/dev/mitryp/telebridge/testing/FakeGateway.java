package dev.mitryp.telebridge.testing;

import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Records outbound calls; prompts are "sent" immediately with ids starting at 500. */
public final class FakeGateway implements TelegramGateway {
    public record Reply(String text, Integer replyTo) {
    }

    public record Prompt(int id, String text, String placeholder, Integer replyTo) {
    }

    public final List<String> service = new CopyOnWriteArrayList<>();
    public final List<Reply> replies = new CopyOnWriteArrayList<>();
    public final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    public final List<Integer> deleted = new CopyOnWriteArrayList<>();
    private int nextPromptId = 500;

    @Override
    public void sendService(String plainText) {
        service.add(plainText);
    }

    @Override
    public void sendReply(String plainText, Integer replyMessageId, Integer threadId) {
        replies.add(new Reply(plainText, replyMessageId));
    }

    @Override
    public void sendPrompt(String plainText, String placeholder, Integer replyMessageId, Integer threadId, IntConsumer onSent) {
        int id = nextPromptId++;
        prompts.add(new Prompt(id, plainText, placeholder, replyMessageId));
        onSent.accept(id);
    }

    @Override
    public void delete(int messageId) {
        deleted.add(messageId);
    }

    @Override
    public String botUsername() {
        return Fixtures.BOT_USERNAME;
    }

    @Override
    public void pollOnce(Consumer<TelegramInboundMessage> consumer) {
        throw new UnsupportedOperationException();
    }

    public String lastReply() {
        return replies.isEmpty() ? null : replies.get(replies.size() - 1).text();
    }
}
