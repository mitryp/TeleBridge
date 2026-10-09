package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.data.config.TelebridgeConfigHolder;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class InboundCommandRouter {
    private final Map<String, TelegramCommand> map = new HashMap<>();
    private final PendingPrompts prompts;

    public InboundCommandRouter(PendingPrompts prompts) {
        this.prompts = prompts;
    }

    public InboundCommandRouter register(String name, TelegramCommand handler) {
        map.put(name.toLowerCase(Locale.ROOT), handler);
        return this;
    }

    public void route(TelegramInboundMessage in, String botUsername) {
        var cfg = TelebridgeConfigHolder.get();
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

        TelegramCommand h = map.get(cmd);
        if (h != null) h.handle(args, in);
    }
}
