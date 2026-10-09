package dev.mitryp.telebridge.application.telegram;

import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramPoller {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long IDLE_MS = 5000;

    private final TelegramGateway tg;
    private final InboundCommandRouter router;
    private final ConfigProvider cfg;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long startedAt;
    private Thread thread;

    public TelegramPoller(TelegramGateway tg, InboundCommandRouter router, ConfigProvider cfg) {
        this.tg = tg;
        this.router = router;
        this.cfg = cfg;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        startedAt = Instant.now().getEpochSecond();
        thread = new Thread(this::loop, "TeleBridge-Poller");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        boolean failing = false;
        while (running.get()) {
            try {
                var c = cfg.get();
                if (!c.inboundEnabled || !c.hasOutbound()) {
                    Thread.sleep(IDLE_MS);
                    continue;
                }
                String bot = tg.botUsername();
                // Older messages were sent while the server was down, or were already handled before a restart.
                tg.pollOnce(in -> {
                    if (in.date >= startedAt) router.route(in, bot);
                });
                if (failing) {
                    LOGGER.info("[TeleBridge] Telegram polling recovered");
                    failing = false;
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!failing) {
                    LOGGER.warn("[TeleBridge] Telegram polling failed: {}", e.getMessage());
                    failing = true;
                }
                try {
                    Thread.sleep(IDLE_MS);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }
}
