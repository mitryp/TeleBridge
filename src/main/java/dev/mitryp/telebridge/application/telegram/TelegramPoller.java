package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramPoller {
    private final TelegramGateway tg;
    private final InboundCommandRouter router;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public TelegramPoller(TelegramGateway tg, InboundCommandRouter router) {
        this.tg = tg;
        this.router = router;
    }

    public void start() {
        if (running.getAndSet(true)) return;
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
        while (running.get()) {
            try {
                tg.pollOnce(router::route);
            } catch (InterruptedException ie) { /* stopping */ } catch (Exception ex) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}