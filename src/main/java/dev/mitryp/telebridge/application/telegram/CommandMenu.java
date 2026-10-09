package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes the Telegram command menu: public commands for everyone, plus admin commands
 * for each admin in the bridged chat. Re-publishes when the token, chat or admin list changes.
 */
public final class CommandMenu {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> BROAD_SCOPES = List.of("default", "all_private_chats", "all_group_chats", "all_chat_administrators");

    private final TelegramApi api;
    private final ConfigProvider cfg;
    private final InboundCommandRouter router;
    private String publishedFor;
    private Set<Long> publishedAdmins = Set.of();

    public CommandMenu(TelegramApi api, ConfigProvider cfg, InboundCommandRouter router) {
        this.api = api;
        this.cfg = cfg;
        this.router = router;
    }

    public void publishIfChanged() throws InterruptedException {
        var c = cfg.get();
        String key = c.telegramBotToken + "|" + c.telegramChatId + "|" + c.adminUserIds;
        if (key.equals(publishedFor)) return;
        // Marked before publishing so a persistent failure is logged once, not on every poll.
        publishedFor = key;

        String publicMenu = toJson(router.menu(false));
        String adminMenu = toJson(router.menu(true));
        for (String type : BROAD_SCOPES) {
            JsonObject scope = new JsonObject();
            scope.addProperty("type", type);
            call("setMyCommands", Map.of("scope", scope.toString(), "commands", publicMenu));
        }
        for (long admin : c.adminUserIds) {
            call("setMyCommands", Map.of("scope", memberScope(c.telegramChatId, admin), "commands", adminMenu));
        }
        Set<Long> removed = new HashSet<>(publishedAdmins);
        removed.removeAll(c.adminUserIds);
        for (long former : removed) {
            call("deleteMyCommands", Map.of("scope", memberScope(c.telegramChatId, former)));
        }
        publishedAdmins = c.adminUserIds;
    }

    private void call(String method, Map<String, String> params) throws InterruptedException {
        try {
            api.call(method, params, TIMEOUT);
        } catch (IOException e) {
            LOGGER.warn("[TeleBridge] Could not publish the command menu ({}): {}", params.get("scope"), e.getMessage());
        }
    }

    private static String memberScope(String chatId, long userId) {
        JsonObject scope = new JsonObject();
        scope.addProperty("type", "chat_member");
        try {
            scope.addProperty("chat_id", Long.parseLong(chatId));
        } catch (NumberFormatException e) {
            scope.addProperty("chat_id", chatId);
        }
        scope.addProperty("user_id", userId);
        return scope.toString();
    }

    private static String toJson(List<InboundCommandRouter.MenuEntry> entries) {
        JsonArray arr = new JsonArray();
        for (var e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("command", e.command());
            o.addProperty("description", e.description());
            arr.add(o);
        }
        return arr.toString();
    }
}
