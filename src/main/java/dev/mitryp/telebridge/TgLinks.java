package dev.mitryp.telebridge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Telebridge.MODID)
public class TgLinks {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final Map<String, String> tgToMc = new HashMap<>(); // key=@username (lowercase, no '@'), val=mcName
    private static final Path file = Path.of("config", "telebridge-links.json");

    static {
        load();
    }

    public static String resolveMcFromTg(String tgUserOrNull) {
        if (tgUserOrNull == null) return null;
        return tgToMc.get(tgUserOrNull.toLowerCase(Locale.ROOT));
    }

    private static void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                    Map<String, String> m = GSON.fromJson(r, MAP_TYPE);
                    if (m != null) tgToMc.putAll(m);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(tgToMc, MAP_TYPE, w);
        } catch (IOException ignored) {
        }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("tglink")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<net.minecraft.commands.CommandSourceStack, String>argument("telegram_username",
                                        com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                    String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "telegram_username").trim();
                                    String norm = raw.startsWith("@") ? raw.substring(1) : raw;
                                    if (norm.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Provide a Telegram username, e.g., @example"));
                                        return 0;
                                    }
                                    tgToMc.put(norm.toLowerCase(Locale.ROOT), sp.getGameProfile().getName());
                                    save();
                                    ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Linked Telegram @" + norm + " → MC name " + sp.getGameProfile().getName()),
                                            false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            // show current link if any
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String owner = tgToMc.entrySet().stream()
                                    .filter(e2 -> e2.getValue().equals(sp.getGameProfile().getName()))
                                    .map(Map.Entry::getKey).findFirst().orElse(null);
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
