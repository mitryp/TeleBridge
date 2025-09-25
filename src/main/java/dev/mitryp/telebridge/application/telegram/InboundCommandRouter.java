package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.data.config.TelebridgeConfigHolder;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.HashMap;
import java.util.Map;

public final class InboundCommandRouter {
    private final Map<String, TelegramCommand> map = new HashMap<>();

    public InboundCommandRouter register(String name, TelegramCommand handler) {
        map.put(name.toLowerCase(), handler);
        return this;
    }

    public void route(TelegramInboundMessage in) {
        var cfg = TelebridgeConfigHolder.get();
        String prefix = (cfg.inboundCmdPrefix == null || cfg.inboundCmdPrefix.isBlank()) ? "/" : cfg.inboundCmdPrefix;
        String text = in.text;
        if (text == null || text.isBlank() || !text.startsWith(prefix)) return;

        String body = text.substring(prefix.length()).trim();
        int sp = body.indexOf(' ');
        String cmd = (sp == -1 ? body : body.substring(0, sp)).toLowerCase();
        String args = (sp == -1 ? "" : body.substring(sp + 1)).trim();

        TelegramCommand h = map.get(cmd);
        if (h != null) h.handle(args, in);
    }
}

