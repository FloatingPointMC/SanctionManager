package io.github.floatingpointmc.sanctionmanager.minecraft.service;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.BanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.MuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnbanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnmuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.WarnOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnwarnOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone implementation of SanctionService.
 * <p>
 * Directly executes operations against the Core API without any network transport.
 * All operations are executed asynchronously.
 */
public class StandaloneSanctionService implements SanctionService {
    private final @NotNull PunishmentManagerAPI punishmentManager;
    private final @Nullable MinecraftProvider provider;
    private final @NotNull TranslationConfig translationConfig;
    private final @NotNull TranslationContext contextTemplate;
    private final @NotNull ExecutorService executorService;

    public StandaloneSanctionService(
            @NotNull PunishmentManagerAPI punishmentManager,
            @Nullable MinecraftProvider provider,
            @NotNull TranslationConfig translationConfig,
            @NotNull TranslationContext contextTemplate
    ) {
        this.punishmentManager = punishmentManager;
        this.provider = provider;
        this.translationConfig = translationConfig;
        this.contextTemplate = contextTemplate;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SanctionManager-Standalone-Async");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public @NotNull OperationResult executeBan(@NotNull BanOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                LocalDateTime executingTime = LocalDateTime.now();
                LocalDateTime expiryTime = operation.getDuration() != null
                        ? executingTime.plus(operation.getDuration())
                        : null;

                PunishmentRecord punishment = new PunishmentRecord(
                        0, 0,
                        operation.getTargetUuid(),
                        operation.getExecutorUuid(),
                        executingTime,
                        expiryTime,
                        false, null, false, null,
                        false, null,
                        operation.getReason(),
                        Type.BAN
                );

                punishmentManager.addPunishment(punishment);

                // Kick player if online (schedule to main thread)
                if (provider != null) {
                    provider.schedule(() -> {
                        SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                        if (targetPlayer != null) {
                            TranslationContext.Punishment msgContext = TranslationContext.Punishment.builder()
                                    .id(punishment.getId())
                                    .relId(punishment.getRelId())
                                    .target(operation.getTargetUuid())
                                    .targetName(targetPlayer.getName())
                                    .executor(operation.getExecutorUuid() != null ? operation.getExecutorUuid() : new UUID(0, 0))
                                    .operatorName("[Console]")
                                    .executingTime(punishment.getExecutingTime())
                                    .expiryTime(expiryTime)
                                    .reason(operation.getReason())
                                    .pluginName(contextTemplate.getPluginName())
                                    .pluginVersion(contextTemplate.getPluginVersion())
                                    .build();

                            boolean isTemp = expiryTime != null;
                            List<String> lines = isTemp
                                    ? translationConfig.getStringList("ban.temporary")
                                    : translationConfig.getStringList("ban.permanent");
                            String kickMessage = TranslationFormatter.format(lines, msgContext);
                            targetPlayer.kick(kickMessage);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }

    @Override
    public @NotNull OperationResult executeUnban(@NotNull UnbanOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                Punishment activeBan = punishmentManager.queryActiveBan(operation.getTargetUuid());
                if (activeBan != null) {
                    punishmentManager.removePunishment(activeBan.getId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }

    @Override
    public @NotNull OperationResult executeMute(@NotNull MuteOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                LocalDateTime executingTime = LocalDateTime.now();
                LocalDateTime expiryTime = operation.getDuration() != null
                        ? executingTime.plus(operation.getDuration())
                        : null;

                PunishmentRecord punishment = new PunishmentRecord(
                        0, 0,
                        operation.getTargetUuid(),
                        operation.getExecutorUuid(),
                        executingTime,
                        expiryTime,
                        false, null, false, null,
                        false, null,
                        operation.getReason(),
                        Type.MUTE
                );

                punishmentManager.addPunishment(punishment);

                // Notify player if online (schedule to main thread)
                if (provider != null) {
                    provider.schedule(() -> {
                        SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                        if (targetPlayer != null) {
                            TranslationContext.Punishment msgContext = TranslationContext.Punishment.builder()
                                    .id(punishment.getId())
                                    .relId(punishment.getRelId())
                                    .target(operation.getTargetUuid())
                                    .targetName(targetPlayer.getName())
                                    .executor(operation.getExecutorUuid() != null ? operation.getExecutorUuid() : new UUID(0, 0))
                                    .operatorName("[Console]")
                                    .executingTime(punishment.getExecutingTime())
                                    .expiryTime(expiryTime)
                                    .reason(operation.getReason())
                                    .pluginName(contextTemplate.getPluginName())
                                    .pluginVersion(contextTemplate.getPluginVersion())
                                    .build();

                            boolean isTemp = expiryTime != null;
                            List<String> lines = isTemp
                                    ? translationConfig.getStringList("mute.temporary")
                                    : translationConfig.getStringList("mute.permanent");

                            for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                                targetPlayer.sendMessage(line);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }

    @Override
    public @NotNull OperationResult executeUnmute(@NotNull UnmuteOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                Punishment activeMute = punishmentManager.queryActiveMute(operation.getTargetUuid());
                if (activeMute != null) {
                    punishmentManager.removePunishment(activeMute.getId());

                    // Notify player if online (schedule to main thread)
                    if (provider != null) {
                        provider.schedule(() -> {
                            SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                            if (targetPlayer != null) {
                                targetPlayer.sendMessage("§aYou have been unmuted.");
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }

    @Override
    public @NotNull OperationResult executeWarn(@NotNull WarnOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                LocalDateTime executingTime = LocalDateTime.now();
                LocalDateTime expiryTime = operation.getDuration() != null
                        ? executingTime.plus(operation.getDuration())
                        : null;

                PunishmentRecord punishment = new PunishmentRecord(
                        0, 0,
                        operation.getTargetUuid(),
                        operation.getExecutorUuid(),
                        executingTime,
                        expiryTime,
                        false, null, false, null,
                        false, null,
                        operation.getReason(),
                        Type.WARN
                );

                punishmentManager.addPunishment(punishment);

                // Notify player if online (schedule to main thread)
                if (provider != null) {
                    provider.schedule(() -> {
                        SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                        if (targetPlayer != null) {
                            TranslationContext.Punishment msgContext = TranslationContext.Punishment.builder()
                                    .id(punishment.getId())
                                    .relId(punishment.getRelId())
                                    .target(operation.getTargetUuid())
                                    .targetName(targetPlayer.getName())
                                    .executor(operation.getExecutorUuid() != null ? operation.getExecutorUuid() : new UUID(0, 0))
                                    .operatorName("[Console]")
                                    .executingTime(punishment.getExecutingTime())
                                    .expiryTime(expiryTime)
                                    .reason(operation.getReason())
                                    .pluginName(contextTemplate.getPluginName())
                                    .pluginVersion(contextTemplate.getPluginVersion())
                                    .build();

                            boolean isTemp = expiryTime != null;
                            List<String> lines = isTemp
                                    ? translationConfig.getStringList("warn.temporary")
                                    : translationConfig.getStringList("warn.permanent");

                            for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                                targetPlayer.sendMessage(line);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }

    @Override
    public @NotNull OperationResult executeUnwarn(@NotNull UnwarnOperation operation) {
        CompletableFuture.runAsync(() -> {
            try {
                java.util.Collection<Punishment> activeWarns = punishmentManager.queryActiveWarns(operation.getTargetUuid());

                if (!activeWarns.isEmpty()) {
                    // Remove the most recent warn
                    Punishment mostRecent = null;
                    for (Punishment warn : activeWarns) {
                        if (mostRecent == null || warn.getExecutingTime().isAfter(mostRecent.getExecutingTime())) {
                            mostRecent = warn;
                        }
                    }

                    if (mostRecent != null) {
                        punishmentManager.removePunishment(mostRecent.getId());

                        // Notify player if online (schedule to main thread)
                        if (provider != null) {
                            provider.schedule(() -> {
                                SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                                if (targetPlayer != null) {
                                    targetPlayer.sendMessage("§aOne of your warnings has been removed.");
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, executorService);

        return OperationResult.SUCCESS;
    }
}
