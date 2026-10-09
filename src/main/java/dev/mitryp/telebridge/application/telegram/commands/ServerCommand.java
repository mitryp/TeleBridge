package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.function.Function;
import java.util.regex.Pattern;

/** Runs a fixed Minecraft command built from validated arguments and replies with its output. */
public final class ServerCommand implements TelegramCommand {
    private static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final MinecraftBridge mc;
    private final TelegramGateway tg;
    private final String usage;
    private final Function<String, String> toCommand;

    /** {@code toCommand} maps the Telegram arguments to a Minecraft command, or to null when they are invalid. */
    public ServerCommand(MinecraftBridge mc, TelegramGateway tg, String usage, Function<String, String> toCommand) {
        this.mc = mc;
        this.tg = tg;
        this.usage = usage;
        this.toCommand = toCommand;
    }

    public static ServerCommand kick(MinecraftBridge mc, TelegramGateway tg) {
        return new ServerCommand(mc, tg, "/kick <player> [reason]", args -> {
            String[] parts = args.split("\\s+", 2);
            if (!PLAYER.matcher(parts[0]).matches()) return null;
            return "kick " + parts[0] + (parts.length > 1 ? " " + parts[1].replaceAll("\\s+", " ") : "");
        });
    }

    public static ServerCommand keyauth(MinecraftBridge mc, TelegramGateway tg) {
        return new ServerCommand(mc, tg, "/keyauth list | /keyauth reset <player>", args -> {
            String[] parts = args.split("\\s+");
            if (parts.length == 1 && parts[0].equals("list")) return "keyauth list";
            if (parts.length == 2 && parts[0].equals("reset") && PLAYER.matcher(parts[1]).matches()) return "keyauth reset " + parts[1];
            return null;
        });
    }

    public static ServerCommand save(MinecraftBridge mc, TelegramGateway tg) {
        return new ServerCommand(mc, tg, "/save", args -> args.isEmpty() ? "save-all" : null);
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        String command = toCommand.apply(args);
        if (command == null) {
            reply(in, "Usage: " + usage);
            return;
        }
        mc.runCommand(command, issuer(in)).whenComplete((lines, error) -> {
            if (error != null) reply(in, "Failed: " + error.getMessage());
            else reply(in, lines.isEmpty() ? "Done." : String.join("\n", lines));
        });
    }

    static String issuer(TelegramInboundMessage in) {
        return in.tgUsernameOrNull != null ? "@" + in.tgUsernameOrNull : in.displayName;
    }

    private void reply(TelegramInboundMessage in, String text) {
        tg.sendReply(text, in.messageId, in.threadId);
    }
}
