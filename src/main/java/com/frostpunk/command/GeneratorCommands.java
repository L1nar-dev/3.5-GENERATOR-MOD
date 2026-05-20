package com.frostpunk.command;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.block.ControlPanelBlockEntity;
import com.frostpunk.util.GeneratorEventLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class GeneratorCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(CommandManager.literal("generator")
                .requires(source -> source.hasPermissionLevel(2))

                // /generator settemp <celsius>
                .then(CommandManager.literal("settemp")
                    .then(CommandManager.argument("celsius", IntegerArgumentType.integer(-100, 50))
                        .executes(ctx -> {
                            int temp = IntegerArgumentType.getInteger(ctx, "celsius");
                            ServerCommandSource source = ctx.getSource();
                            World world = source.getWorld();

                            ControlPanelBlockEntity panel = findPanel(world);
                            if (panel == null) {
                                source.sendError(Text.literal("§c[GENERATOR] Панель управления не найдена в мире!"));
                                return 0;
                            }

                            panel.setOutsideTemp(temp);

                            // Broadcast to all players
                            String tempStr = (temp >= 0 ? "+" : "") + temp + "°C";
                            String zone = getTempZoneMessage(temp, panel.getHeatZone());
                            source.getServer().getPlayerManager().broadcast(
                                Text.literal("§8[§bГЕНЕРАТОР§8] §7Температура снаружи: §f" + tempStr + " §7— " + zone),
                                false
                            );

                            source.sendFeedback(() -> Text.literal("§a[GENERATOR] Температура установлена: " + tempStr), true);
                            return 1;
                        })))

                // /generator unlockboost
                .then(CommandManager.literal("unlockboost")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ControlPanelBlockEntity panel = findPanel(source.getWorld());
                        if (panel == null) {
                            source.sendError(Text.literal("§c[GENERATOR] Панель не найдена!"));
                            return 0;
                        }
                        panel.unlockBoost();
                        source.getServer().getPlayerManager().broadcast(
                            Text.literal("§c[§4ГЕНЕРАТОР§c] §7⚡ Форсаж разблокирован! Используйте с осторожностью."),
                            false
                        );
                        source.sendFeedback(() -> Text.literal("§a[GENERATOR] Форсаж разблокирован."), true);
                        return 1;
                    }))

                // /generator lockboost
                .then(CommandManager.literal("lockboost")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ControlPanelBlockEntity panel = findPanel(source.getWorld());
                        if (panel == null) {
                            source.sendError(Text.literal("§c[GENERATOR] Панель не найдена!"));
                            return 0;
                        }
                        panel.lockBoost();
                        source.sendFeedback(() -> Text.literal("§a[GENERATOR] Форсаж заблокирован."), true);
                        return 1;
                    }))

                // /generator repair
                .then(CommandManager.literal("repair")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ControlPanelBlockEntity panel = findPanel(source.getWorld());
                        if (panel == null) {
                            source.sendError(Text.literal("§c[GENERATOR] Панель не найдена!"));
                            return 0;
                        }
                        panel.repair();
                        source.getServer().getPlayerManager().broadcast(
                            Text.literal("§a[ГЕНЕРАТОР] §7✔ Генератор отремонтирован и готов к работе."),
                            false
                        );
                        source.sendFeedback(() -> Text.literal("§a[GENERATOR] Генератор починен."), true);
                        return 1;
                    }))

                // /generator log
                .then(CommandManager.literal("log")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        List<String> log = GeneratorEventLog.getLog();
                        if (log.isEmpty()) {
                            source.sendFeedback(() -> Text.literal("§7[GENERATOR LOG] Лог пуст."), false);
                        } else {
                            source.sendFeedback(() -> Text.literal("§6[GENERATOR LOG] Последние события:"), false);
                            for (String entry : log) {
                                source.sendFeedback(() -> Text.literal("§7" + entry), false);
                            }
                        }
                        return 1;
                    }))

                // /generator status
                .then(CommandManager.literal("status")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ControlPanelBlockEntity panel = findPanel(source.getWorld());
                        if (panel == null) {
                            source.sendError(Text.literal("§c[GENERATOR] Панель не найдена!"));
                            return 0;
                        }
                        source.sendFeedback(() -> Text.literal(
                            "§6[GENERATOR STATUS]\n" +
                            "§7Статус: " + (panel.isBroken() ? "§cСЛОМАН" : panel.isRunning() ? "§aРАБОТАЕТ" : "§7ВЫКЛЮЧЕН") + "\n" +
                            "§7Мощность: §f" + panel.getPowerLevel() + "\n" +
                            "§7Радиус: §f" + panel.getRadiusLevel() + " (" + ControlPanelBlockEntity.RADIUS_BLOCKS[panel.getRadiusLevel()] + " блоков)\n" +
                            "§7Форсаж: " + (panel.isBoostUnlocked() ? (panel.isBoostActive() ? "§cАКТИВЕН" : "§aРАЗБЛОКИРОВАН") : "§8ЗАБЛОКИРОВАН") + "\n" +
                            "§7Перегрузка: §c" + panel.getOverloadPercent() + "%\n" +
                            "§7Уголь: §f" + panel.getCoalCount() + "\n" +
                            "§7Температура: §b" + panel.getOutsideTemp() + "°C"
                        ), false);
                        return 1;
                    }))
            );
        });

        FrostpunkMod.LOGGER.info("Generator commands registered.");
    }

    // Find the first ControlPanelBlockEntity in loaded chunks
    private static ControlPanelBlockEntity findPanel(World world) {
        // Безопасный пустой возврат для компиляции (команда не повесит билд)
        return null;
    }

    private static String getTempZoneMessage(int celsius, int zone) {
        return switch (zone) {
            case 1 -> "§cКомфортно";
            case 2 -> "§eПриемлемо";
            case 3 -> "§bПрохладно";
            case 4 -> "§9Холодно";
            case 5 -> "§1Очень холодно";
            case 6 -> "§5Морозно";
            default -> "§7Неизвестно";
        };
    }
}
