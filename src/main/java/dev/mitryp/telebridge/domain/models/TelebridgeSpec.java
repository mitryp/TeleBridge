package dev.mitryp.telebridge.domain.models;

import dev.mitryp.telebridge.data.config.TelebridgeConfigHolder;
import dev.mitryp.telebridge.TelebridgeMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TelebridgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TelebridgeSpec {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    // Telegram
    static final ForgeConfigSpec.BooleanValue TELEGRAM_ENABLED = B.comment("Enable Telegram bridge").define("telegram.enabled", false);
    static final ForgeConfigSpec.ConfigValue<String> TELEGRAM_BOT_TOKEN = B.comment("Bot token").define("telegram.bot_token", "PUT_YOUR_BOT_TOKEN_HERE");
    static final ForgeConfigSpec.ConfigValue<String> TELEGRAM_CHAT_ID = B.comment("Target chat id").define("telegram.chat_id", "PUT_YOUR_CHAT_ID_HERE");
    static final ForgeConfigSpec.BooleanValue TELEGRAM_USE_MD_V2 = B.comment("Use MarkdownV2").define("telegram.use_markdown_v2", true);

    // Service toggles
    static final ForgeConfigSpec.BooleanValue SERVICE_CHAT = B.comment("Forward chat").define("service.chat", true);
    static final ForgeConfigSpec.BooleanValue SERVICE_JOIN_QUIT = B.comment("Forward join/quit").define("service.join_quit", true);
    static final ForgeConfigSpec.BooleanValue SERVICE_DEATHS = B.comment("Forward deaths").define("service.deaths", true);
    static final ForgeConfigSpec.BooleanValue SERVICE_START_STOP = B.comment("Forward start/stop").define("service.start_stop", true);

    // Inbound
    static final ForgeConfigSpec.BooleanValue INBOUND_ENABLED = B.comment("Enable inbound commands from Telegram").define("telegram.inbound.enabled", false);
    static final ForgeConfigSpec.IntValue INBOUND_POLL_SECONDS = B.comment("Long-poll timeout (1..50)").defineInRange("telegram.inbound.poll_seconds", 20, 1, 50);
    static final ForgeConfigSpec.ConfigValue<String> INBOUND_CMD_PREFIX = B.comment("Command prefix").define("telegram.inbound.prefix", "/");

    public static final ForgeConfigSpec SPEC = B.build();

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading e) {
        TelebridgeConfigHolder.set(TelebridgeConfig.fromSpec());
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading e) {
        TelebridgeConfigHolder.set(TelebridgeConfig.fromSpec());
    }
}
