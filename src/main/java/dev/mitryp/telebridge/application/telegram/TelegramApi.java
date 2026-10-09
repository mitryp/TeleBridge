package dev.mitryp.telebridge.application.telegram;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class TelegramApi implements BotApi {
    private static final Gson GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConfigProvider cfg;

    public TelegramApi(ConfigProvider cfg) {
        this.cfg = cfg;
    }

    @Override
    public JsonElement call(String method, Map<String, String> params, Duration timeout) throws IOException, InterruptedException {
        String token = cfg.get().telegramBotToken;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            String msg = String.valueOf(e);
            throw new IOException(method + ": " + (token.isEmpty() ? msg : msg.replace(token, "<token>")));
        }

        JsonObject body;
        try {
            body = GSON.fromJson(res.body(), JsonObject.class);
        } catch (JsonParseException e) {
            body = null;
        }
        if (body != null && body.has("ok") && body.get("ok").getAsBoolean()) return body.get("result");

        String description = body != null && body.has("description")
                ? body.get("description").getAsString()
                : "HTTP " + res.statusCode();
        int retryAfter = 0;
        if (body != null && body.has("parameters")) {
            JsonObject p = body.getAsJsonObject("parameters");
            if (p.has("retry_after")) retryAfter = p.get("retry_after").getAsInt();
        }
        throw new TelegramApiException(method, res.statusCode(), description, retryAfter);
    }

    private static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static final class TelegramApiException extends IOException {
        public final int status;
        public final int retryAfterSeconds;

        TelegramApiException(String method, int status, String description, int retryAfterSeconds) {
            super(method + " failed (" + status + "): " + description);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        boolean retryable() {
            return retryAfterSeconds > 0 || status >= 500;
        }
    }
}
