package dev.mitryp.telebridge.domain.interfaces;

import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

public interface TelegramCommand {
    void handle(String args, TelegramInboundMessage in);
}
