package dev.mitryp.telebridge.application.mc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.mitryp.telebridge.TelebridgeMod;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TelebridgeMod.MODID)
public final class TglinkCommand {
    private final LinkRepository links;

    public TglinkCommand(LinkRepository links) {
        this.links = links;
    }

    @SubscribeEvent
    public void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("tglink")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("telegram_username", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                    String raw = StringArgumentType.getString(ctx, "telegram_username").trim();
                                    if (raw.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Provide a Telegram username, e.g., my_username"));
                                        return 0;
                                    }

                                    if (links.isLinked(raw)) {
                                        ctx.getSource().sendFailure(Component.literal("That Telegram username is already linked and cannot be overwritten."));
                                        return 0;
                                    }

                                    links.link(raw, sp.getGameProfile().getName());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Linked Telegram " + raw + " → MC name " + sp.getGameProfile().getName()), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String owner = links.findTgByMc(sp.getGameProfile().getName());
                            if (owner == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal("No Telegram username linked. Use /tglink <username>"), false);
                            } else {
                                ctx.getSource().sendSuccess(() -> Component.literal("Linked @" + owner + " → " + sp.getGameProfile().getName()), false);
                            }
                            return 1;
                        })
        );
    }
}
