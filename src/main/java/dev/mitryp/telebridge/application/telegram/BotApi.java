package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public interface BotApi {
    /** Calls a Bot API method and returns its {@code result}. */
    JsonElement call(String method, Map<String, String> params, Duration timeout) throws IOException, InterruptedException;
}
