// Project: dev.mitryp.telebridge — SOLID + KISS refactor
// Notes:
//  - Keep classes small with single responsibilities.
//  - Decouple MC/Forge event glue from Telegram I/O and persistence.
//  - Use simple dependency wiring inside the mod ctor (no DI framework).
//  - Preserve existing features: chat out -> Telegram, /say + /online from Telegram -> MC,
//    join/quit/death/start/stop service messages, MarkdownV2 escaping, /tglink command.
//  - Threading: a tiny Executor for outbound HTTP; a dedicated poller thread for inbound.
//  - Storage: JSON file for TG↔MC links via LinkRepository.
//
// Files below should be placed under src/main/java/dev/mitryp/telebridge/ with matching names.

// =====================================================================================
// TelebridgeMod.java — entry point and wiring
// =====================================================================================
package dev.mitryp.telebridge;

import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.application.mc.ForgeMinecraftBridge;
import dev.mitryp.telebridge.application.mc.commands.TgUnlinkCommand;
import dev.mitryp.telebridge.application.mc.commands.TglinkCommand;
import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.application.telegram.InboundCommandRouter;
import dev.mitryp.telebridge.application.telegram.TelegramHttpGateway;
import dev.mitryp.telebridge.application.telegram.TelegramPoller;
import dev.mitryp.telebridge.application.telegram.commands.OnlineCommand;
import dev.mitryp.telebridge.application.telegram.commands.SayCommand;
import dev.mitryp.telebridge.data.config.TelebridgeConfigHolder;
import dev.mitryp.telebridge.data.repositories.JsonLinkRepository;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelebridgeSpec;
import dev.mitryp.telebridge.utils.TelebridgePaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Mod(TelebridgeMod.MODID)
public class TelebridgeMod {
    public static final String MODID = "telebridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ExecutorService httpExec;
    private final TelegramGateway telegram;
    private final MinecraftBridge mc;
    private final LinkRepository links;
    private final NameResolver nameResolver;
    private final InboundCommandRouter router;
    private final TelegramPoller poller;

    public TelebridgeMod(FMLJavaModLoadingContext context) {
        // Load Forge config
        context.registerConfig(ModConfig.Type.COMMON, TelebridgeSpec.SPEC);

        // Core services
        this.httpExec = new ThreadPoolExecutor(
                1, 3, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256),
                r -> {
                    Thread t = new Thread(r, "TeleBridge-HTTP");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        this.telegram = new TelegramHttpGateway(TelebridgeConfigHolder::get, httpExec);
        this.mc = new ForgeMinecraftBridge();
        this.links = new JsonLinkRepository(TelebridgePaths.linksFile());
        this.nameResolver = new NameResolver(links);

        // Commands available to Telegram
        this.router = new InboundCommandRouter()
                .register("say", new SayCommand(mc, nameResolver))
                .register("online", new OnlineCommand(mc, telegram));

        // Inbound poller (Telegram -> MC)
        this.poller = new TelegramPoller(telegram, router);

        // Event bus
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new TglinkCommand(links));
        MinecraftForge.EVENT_BUS.register(new TgUnlinkCommand(links));

        LOGGER.info("[TeleBridge] Loaded. Telegram bridge {}.",
                TelebridgeConfigHolder.get().telegramEnabled ? "ENABLED" : "DISABLED");
    }

    /* ===================== Forge event handlers ===================== */
    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (!(cfg.telegramEnabled && cfg.serviceChat && cfg.hasOutbound())) return;

        String line = "<" + e.getPlayer().getName().getString() + "> " + e.getMessage().getString();
        telegram.sendService(line);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceJoinQuit && cfg.hasOutbound()) {
            telegram.sendService("> " + e.getEntity().getName().getString() + " joined the game");
        }
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceJoinQuit && cfg.hasOutbound()) {
            telegram.sendService("> " + e.getEntity().getName().getString() + " left the game");
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (!(cfg.telegramEnabled && cfg.serviceDeaths && cfg.hasOutbound())) return;
        if (!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        String deathMsg = sp.getCombatTracker().getDeathMessage().getString();
        if (deathMsg.isBlank()) {
            String cause = e.getSource() != null ? e.getSource().getMsgId() : "unknown";
            deathMsg = sp.getName().getString() + " died (" + cause + ")";
        }
        telegram.sendService("> " + deathMsg);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceStartStop && cfg.hasOutbound()) {
            telegram.sendService("> Server starting");
        }
        if (cfg.inboundEnabled) {
            poller.start();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceStartStop && cfg.hasOutbound()) {
            telegram.sendService("> Server stopping");
        }
        poller.stop();
        httpExec.shutdownNow();
    }
}
