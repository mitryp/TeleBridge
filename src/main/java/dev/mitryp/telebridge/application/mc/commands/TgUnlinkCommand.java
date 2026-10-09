package dev.mitryp.telebridge.application.mc.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.models.TelegramLink;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TgUnlinkCommand {
    private final LinkRepository links;

    public TgUnlinkCommand(LinkRepository links) {
        this.links = links;
    }

    @SubscribeEvent
    public void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("tg_unlink")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            TelegramLink removed = links.unlinkByMc(sp.getGameProfile().getName());

                            ctx.getSource().sendSuccess(() -> Component.literal(removed == null
                                    ? "You have no Telegram account linked."
                                    : "Unlinked Telegram " + removed.tgLabel() + "."), false);
                            return 1;
                        })
        );
    }
}
