package dev.mitryp.telebridge.domain.models;

public final class TelebridgeConfig {
    public final boolean telegramEnabled;
    public final String telegramBotToken;
    public final String telegramChatId;
    public final boolean telegramUseMarkdownV2;
    public final boolean serviceChat, serviceJoinQuit, serviceDeaths, serviceStartStop;
    public final boolean inboundEnabled;
    public final int inboundPollSeconds;
    public final String inboundCmdPrefix;

    private TelebridgeConfig(boolean telegramEnabled, String bot, String chat, boolean mdv2,
                             boolean serviceChat, boolean serviceJoinQuit, boolean serviceDeaths, boolean serviceStartStop,
                             boolean inboundEnabled, int inboundPollSeconds, String inboundCmdPrefix) {
        this.telegramEnabled = telegramEnabled;
        this.telegramBotToken = bot;
        this.telegramChatId = chat;
        this.telegramUseMarkdownV2 = mdv2;
        this.serviceChat = serviceChat;
        this.serviceJoinQuit = serviceJoinQuit;
        this.serviceDeaths = serviceDeaths;
        this.serviceStartStop = serviceStartStop;
        this.inboundEnabled = inboundEnabled;
        this.inboundPollSeconds = inboundPollSeconds;
        this.inboundCmdPrefix = inboundCmdPrefix;
    }

    static TelebridgeConfig fromSpec() {
        // SAFE to call only inside ModConfigEvent callbacks
        return new TelebridgeConfig(
                TelebridgeSpec.TELEGRAM_ENABLED.get(),
                TelebridgeSpec.TELEGRAM_BOT_TOKEN.get(),
                TelebridgeSpec.TELEGRAM_CHAT_ID.get(),
                TelebridgeSpec.TELEGRAM_USE_MD_V2.get(),
                TelebridgeSpec.SERVICE_CHAT.get(),
                TelebridgeSpec.SERVICE_JOIN_QUIT.get(),
                TelebridgeSpec.SERVICE_DEATHS.get(),
                TelebridgeSpec.SERVICE_START_STOP.get(),
                TelebridgeSpec.INBOUND_ENABLED.get(),
                TelebridgeSpec.INBOUND_POLL_SECONDS.get(),
                TelebridgeSpec.INBOUND_CMD_PREFIX.get()
        );
    }

    public static TelebridgeConfig defaults() {
        // Mirror the defaults used when declaring the spec
        return new TelebridgeConfig(
                false, "PUT_YOUR_BOT_TOKEN_HERE", "PUT_YOUR_CHAT_ID_HERE", true,
                true, true, true, true,
                false, 20, "/"
        );
    }

    public boolean hasOutbound() {
        return telegramEnabled && !telegramBotToken.isBlank() && !telegramChatId.isBlank();
    }
}
