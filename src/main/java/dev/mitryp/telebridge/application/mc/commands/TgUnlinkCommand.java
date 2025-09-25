package dev.mitryp.telebridge.application.mc.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mitryp.telebridge.TelebridgeMod;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TelebridgeMod.MODID)
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
                            String mc = sp.getGameProfile().getName();
                            String removed = links.unlinkByMc(mc);

                            if (removed == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal("You have no Telegram username linked."), false);
                                return 1;
                            }

                            ctx.getSource().sendSuccess(() -> Component.literal("Unlinked @" + removed + " from " + mc), false);

                            return 1;
                        })
        );
    }
}
