package dev.mitryp.telebridge;

import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.application.mc.AdvancementAnnouncer;
import dev.mitryp.telebridge.application.mc.ForgeMinecraftBridge;
import dev.mitryp.telebridge.application.mc.commands.TgUnlinkCommand;
import dev.mitryp.telebridge.application.mc.commands.TglinkCommand;
import dev.mitryp.telebridge.application.services.LinkCodes;
import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.application.telegram.CommandMenu;
import dev.mitryp.telebridge.application.telegram.InboundCommandRouter;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.application.telegram.TelegramApi;
import dev.mitryp.telebridge.application.telegram.TelegramHttpGateway;
import dev.mitryp.telebridge.application.telegram.TelegramPoller;
import dev.mitryp.telebridge.application.telegram.TelegramSender;
import dev.mitryp.telebridge.application.telegram.commands.LinkCommand;
import dev.mitryp.telebridge.application.telegram.commands.OnlineCommand;
import dev.mitryp.telebridge.application.telegram.commands.RestartCommand;
import dev.mitryp.telebridge.application.telegram.commands.SayCommand;
import dev.mitryp.telebridge.application.telegram.commands.ServerCommand;
import dev.mitryp.telebridge.application.telegram.commands.TpsCommand;
import dev.mitryp.telebridge.application.telegram.commands.UnlinkCommand;
import dev.mitryp.telebridge.data.config.TelebridgeConfigHolder;
import dev.mitryp.telebridge.data.repositories.JsonLinkRepository;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelebridgeSpec;
import dev.mitryp.telebridge.utils.TelebridgePaths;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.time.Duration;

@Mod(TelebridgeMod.MODID)
public class TelebridgeMod {
    public static final String MODID = "telebridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TelegramSender sender;
    private final TelegramGateway telegram;
    private final MinecraftBridge mc;
    private final LinkRepository links;
    private final NameResolver nameResolver;
    private final InboundCommandRouter router;
    private final TelegramPoller poller;
    private final AdvancementAnnouncer advancements;

    public TelebridgeMod(FMLJavaModLoadingContext context) {
        // Load Forge config
        context.registerConfig(ModConfig.Type.COMMON, TelebridgeSpec.SPEC);

        // Core services
        TelegramApi api = new TelegramApi(TelebridgeConfigHolder::get);
        this.sender = new TelegramSender(api, TelebridgeConfigHolder::get);
        this.telegram = new TelegramHttpGateway(TelebridgeConfigHolder::get, api, sender);
        this.mc = new ForgeMinecraftBridge();
        this.advancements = new AdvancementAnnouncer(telegram);
        this.links = new JsonLinkRepository(TelebridgePaths.linksFile());
        this.nameResolver = new NameResolver(links);
        LinkCodes linkCodes = new LinkCodes();
        PendingPrompts prompts = new PendingPrompts(telegram);

        // Commands available to Telegram
        this.router = new InboundCommandRouter(prompts, telegram, TelebridgeConfigHolder::get)
                .register("say", "Send a message to the Minecraft in-game chat", new SayCommand(mc, nameResolver, prompts))
                .register("online", "Displays the current player list on the server", new OnlineCommand(mc, telegram))
                .register("link", "Link your Telegram account to your Minecraft player (get a code with /tglink in game)",
                        new LinkCommand(links, linkCodes, mc, telegram, prompts))
                .register("unlink", "Unlink your Telegram account from your Minecraft player", new UnlinkCommand(links, telegram))
                .registerAdmin("tps", "Admin: server TPS and tick time", new TpsCommand(mc, telegram))
                .registerAdmin("kick", "Admin: kick a player (/kick <player> [reason])", ServerCommand.kick(mc, telegram))
                .registerAdmin("keyauth", "Admin: /keyauth list, /keyauth reset <player>", ServerCommand.keyauth(mc, telegram))
                .registerAdmin("save", "Admin: save the world", ServerCommand.save(mc, telegram))
                .registerAdmin("restart", "Admin: restart the server (/restart confirm)", new RestartCommand(mc, telegram));

        // Inbound poller (Telegram -> MC)
        this.poller = new TelegramPoller(telegram, router, new CommandMenu(api, TelebridgeConfigHolder::get, router), TelebridgeConfigHolder::get);

        // Event bus
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new TglinkCommand(links, linkCodes));
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
        String name = e.getEntity().getName().getString();
        advancements.flush(name);
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceJoinQuit && cfg.hasOutbound()) {
            String reason = e.getEntity() instanceof ServerPlayer sp ? disconnectReason(sp) : null;
            telegram.sendService("> " + name + " left the game" + (reason == null ? "" : " (" + reason + ")"));
        }
    }

    /** Null for an ordinary quit. */
    private static String disconnectReason(ServerPlayer sp) {
        if (sp.connection == null) return null;
        Component reason = sp.connection.connection.getDisconnectedReason();
        if (reason == null) return null;
        if (reason.getContents() instanceof TranslatableContents t && t.getKey().equals("disconnect.disconnected")) return null;
        return reason.getString();
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent e) {
        var cfg = TelebridgeConfigHolder.get();
        if (!(cfg.telegramEnabled && cfg.serviceAdvancements && cfg.hasOutbound())) return;
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        DisplayInfo display = e.getAdvancement().getDisplay();
        if (display == null || !display.shouldAnnounceChat()) return;
        if (!sp.serverLevel().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) return;
        advancements.add(sp.getName().getString(), display);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) advancements.flushDue();
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
        sender.start();
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceStartStop && cfg.hasOutbound()) {
            telegram.sendService("> Server starting");
        }
        poller.start();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        advancements.flushAll();
        var cfg = TelebridgeConfigHolder.get();
        if (cfg.telegramEnabled && cfg.serviceStartStop && cfg.hasOutbound()) {
            telegram.sendService("> Server stopping");
        }
    }

    // Players are disconnected after ServerStoppingEvent, so their leave messages are queued only by now.
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent e) {
        poller.stop();
        sender.stop(Duration.ofSeconds(5));
    }
}
