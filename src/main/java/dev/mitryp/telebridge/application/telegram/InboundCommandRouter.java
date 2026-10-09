package dev.mitryp.telebridge.application.telegram;

import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InboundCommandRouter {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record MenuEntry(String command, String description) {
    }

    private record Registered(TelegramCommand handler, String description, boolean adminOnly) {
    }

    private final Map<String, Registered> map = new LinkedHashMap<>();
    private final PendingPrompts prompts;
    private final TelegramGateway tg;
    private final ConfigProvider cfgProvider;

    public InboundCommandRouter(PendingPrompts prompts, TelegramGateway tg, ConfigProvider cfg) {
        this.prompts = prompts;
        this.tg = tg;
        this.cfgProvider = cfg;
    }

    public InboundCommandRouter register(String name, String description, TelegramCommand handler) {
        map.put(name.toLowerCase(Locale.ROOT), new Registered(handler, description, false));
        return this;
    }

    /** Registers a command only users in {@code telegram.admin.user_ids} may run. */
    public InboundCommandRouter registerAdmin(String name, String description, TelegramCommand handler) {
        map.put(name.toLowerCase(Locale.ROOT), new Registered(handler, description, true));
        return this;
    }

    public List<MenuEntry> menu(boolean includeAdmin) {
        return map.entrySet().stream()
                .filter(e -> includeAdmin || !e.getValue().adminOnly)
                .map(e -> new MenuEntry(e.getKey(), e.getValue().description))
                .toList();
    }

    public void route(TelegramInboundMessage in, String botUsername) {
        var cfg = cfgProvider.get();
        String prefix = (cfg.inboundCmdPrefix == null || cfg.inboundCmdPrefix.isBlank()) ? "/" : cfg.inboundCmdPrefix;
        String text = in.text;
        if (text == null || text.isBlank()) return;
        if (!text.startsWith(prefix)) {
            if (in.replyToMessageId != null) prompts.answer(in.replyToMessageId, in);
            return;
        }

        String[] parts = text.substring(prefix.length()).trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        // Commands picked from the menu in groups arrive as /cmd@bot_username.
        int at = cmd.indexOf('@');
        if (at >= 0) {
            if (!cmd.substring(at + 1).equalsIgnoreCase(botUsername)) return;
            cmd = cmd.substring(0, at);
        }

        Registered r = map.get(cmd);
        if (r == null) return;
        if (r.adminOnly) {
            if (!cfg.adminUserIds.contains(in.userId)) {
                tg.sendReply("This command is for server admins.", in.messageId, in.threadId);
                return;
            }
            LOGGER.info("[TeleBridge] Admin {} ({}) ran /{} {}", in.tgUsernameOrNull, in.userId, cmd, args);
        }
        r.handler.handle(args, in);
    }
}
