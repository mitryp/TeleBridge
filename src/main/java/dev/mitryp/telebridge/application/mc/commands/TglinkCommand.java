package dev.mitryp.telebridge.application.mc.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mitryp.telebridge.application.services.LinkCodes;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.models.TelegramLink;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TglinkCommand {
    private final LinkRepository links;
    private final LinkCodes codes;

    public TglinkCommand(LinkRepository links, LinkCodes codes) {
        this.links = links;
        this.codes = codes;
    }

    @SubscribeEvent
    public void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("tglink")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String mcName = sp.getGameProfile().getName();

                            TelegramLink link = links.findByMc(mcName);
                            if (link != null) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Linked to Telegram " + link.tgLabel() + ". Use /tg_unlink to unlink."), false);
                                return 1;
                            }

                            String command = "/link " + codes.issue(mcName);
                            Component code = Component.literal(command).withStyle(s -> s
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
                            ctx.getSource().sendSuccess(() -> Component.literal("Send ")
                                    .append(code)
                                    .append(" in the Telegram chat within 10 minutes."), false);
                            return 1;
                        })
        );
    }
}
