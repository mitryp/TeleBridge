package dev.mitryp.telebridge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Telebridge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Telegram settings
    private static final ForgeConfigSpec.BooleanValue TELEGRAM_ENABLED = BUILDER
            .comment("Enable forwarding server chat to a Telegram group")
            .define("telegram.enabled", false);

    private static final ForgeConfigSpec.ConfigValue<String> TELEGRAM_BOT_TOKEN = BUILDER
            .comment("Telegram bot token from @BotFather")
            .define("telegram.bot_token", "PUT_YOUR_BOT_TOKEN_HERE");

    private static final ForgeConfigSpec.ConfigValue<String> TELEGRAM_CHAT_ID = BUILDER
            .comment("Target chat id (negative for groups, e.g., -1001234567890)")
            .define("telegram.chat_id", "PUT_YOUR_CHAT_ID_HERE");

    private static final ForgeConfigSpec.BooleanValue TELEGRAM_USE_MD_V2 = BUILDER
            .comment("Use Telegram MarkdownV2 formatting (recommended)")
            .define("telegram.use_markdown_v2", true);

    // Service-message toggles
    private static final ForgeConfigSpec.BooleanValue SERVICE_CHAT = BUILDER
            .comment("Forward normal chat messages").define("service.chat", true);
    private static final ForgeConfigSpec.BooleanValue SERVICE_JOIN_QUIT = BUILDER
            .comment("Forward join/quit messages").define("service.join_quit", true);
    private static final ForgeConfigSpec.BooleanValue SERVICE_DEATHS = BUILDER
            .comment("Forward player death messages").define("service.deaths", true);
    private static final ForgeConfigSpec.BooleanValue SERVICE_START_STOP = BUILDER
            .comment("Forward server start/stop").define("service.start_stop", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Resolved values
    public static boolean telegramEnabled;
    public static String telegramBotToken;
    public static String telegramChatId;
    public static boolean telegramUseMarkdownV2;

    public static boolean serviceChat;
    public static boolean serviceJoinQuit;
    public static boolean serviceDeaths;
    public static boolean serviceStartStop;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        telegramEnabled = TELEGRAM_ENABLED.get();
        telegramBotToken = TELEGRAM_BOT_TOKEN.get();
        telegramChatId = TELEGRAM_CHAT_ID.get();
        telegramUseMarkdownV2 = TELEGRAM_USE_MD_V2.get();

        serviceChat = SERVICE_CHAT.get();
        serviceJoinQuit = SERVICE_JOIN_QUIT.get();
        serviceDeaths = SERVICE_DEATHS.get();
        serviceStartStop = SERVICE_START_STOP.get();
    }
}
