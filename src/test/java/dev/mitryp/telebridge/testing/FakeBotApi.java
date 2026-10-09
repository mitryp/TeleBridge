package dev.mitryp.telebridge.testing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.mitryp.telebridge.application.telegram.BotApi;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records calls and answers them from a script; unscripted calls return {"message_id": n}. */
public final class FakeBotApi implements BotApi {
    public record Call(String method, Map<String, String> params) {
    }

    @FunctionalInterface
    public interface Response {
        JsonElement get() throws IOException;
    }

    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Deque<Response> script = new ArrayDeque<>();
    private int nextMessageId = 1;

    public synchronized FakeBotApi then(Response response) {
        script.add(response);
        return this;
    }

    @Override
    public synchronized JsonElement call(String method, Map<String, String> params, Duration timeout) throws IOException {
        calls.add(new Call(method, Map.copyOf(params)));
        Response next = script.poll();
        if (next != null) return next.get();
        JsonObject sent = new JsonObject();
        sent.addProperty("message_id", nextMessageId++);
        return sent;
    }

    public List<Call> calls(String method) {
        return calls.stream().filter(c -> c.method().equals(method)).toList();
    }
}
