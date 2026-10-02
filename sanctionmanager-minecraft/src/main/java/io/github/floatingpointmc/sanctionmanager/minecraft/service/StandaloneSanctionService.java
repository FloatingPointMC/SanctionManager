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

/**
 * Standalone implementation of SanctionService.
 * <p>
 * Directly executes operations against the Core API without any network transport.
 */
public class StandaloneSanctionService implements SanctionService {
    private final @NotNull PunishmentManagerAPI punishmentManager;
    private final @Nullable MinecraftProvider provider;
    private final @NotNull TranslationConfig translationConfig;
    private final @NotNull TranslationContext contextTemplate;

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
    }

    @Override
    public @NotNull OperationResult executeBan(@NotNull BanOperation operation) {
        try {
            PunishmentRecord punishment = new PunishmentRecord(
                    0, 0,
                    operation.getTargetUuid(),
                    operation.getExecutorUuid(),
                    LocalDateTime.now(),
                    operation.getExpiryTime(),
                    false, null, false, null,
                    false, null,
                    operation.getReason(),
                    Type.BAN
            );

            punishmentManager.addPunishment(punishment);

            // Kick player if online
            if (provider != null) {
                SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                if (targetPlayer != null) {
                    TranslationContext.Punishment msgContext = buildPunishmentContext(
                            punishment, operation.getTargetUuid(), operation.getTargetName(),
                            operation.getExecutorUuid(), operation.getExecutorName(),
                            operation.getExpiryTime(), operation.getReason()
                    );

                    boolean isTemp = operation.getExpiryTime() != null;
                    List<String> lines = isTemp
                            ? translationConfig.getStringList("ban.temporary")
                            : translationConfig.getStringList("ban.permanent");
                    String kickMessage = TranslationFormatter.format(lines, msgContext);
                    targetPlayer.kick(kickMessage);
                }
            }

            return OperationResult.SUCCESS;
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    @Override
    public @NotNull OperationResult executeUnban(@NotNull UnbanOperation operation) {
        try {
            Punishment activeBan = punishmentManager.queryActiveBan(operation.getTargetUuid());

            if (activeBan != null) {
                punishmentManager.removePunishment(activeBan.getId());
                return OperationResult.SUCCESS;
            } else {
                return OperationResult.NO_ACTIVE_PUNISHMENT;
            }
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    @Override
    public @NotNull OperationResult executeMute(@NotNull MuteOperation operation) {
        try {
            PunishmentRecord punishment = new PunishmentRecord(
                    0, 0,
                    operation.getTargetUuid(),
                    operation.getExecutorUuid(),
                    LocalDateTime.now(),
                    operation.getExpiryTime(),
                    false, null, false, null,
                    false, null,
                    operation.getReason(),
                    Type.MUTE
            );

            punishmentManager.addPunishment(punishment);

            // Notify player if online
            if (provider != null) {
                SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                if (targetPlayer != null) {
                    TranslationContext.Punishment msgContext = buildPunishmentContext(
                            punishment, operation.getTargetUuid(), operation.getTargetName(),
                            operation.getExecutorUuid(), operation.getExecutorName(),
                            operation.getExpiryTime(), operation.getReason()
                    );

                    boolean isTemp = operation.getExpiryTime() != null;
                    List<String> lines = isTemp
                            ? translationConfig.getStringList("mute.temporary")
                            : translationConfig.getStringList("mute.permanent");

                    for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                        targetPlayer.sendMessage(line);
                    }
                }
            }

            return OperationResult.SUCCESS;
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    @Override
    public @NotNull OperationResult executeUnmute(@NotNull UnmuteOperation operation) {
        try {
            Punishment activeMute = punishmentManager.queryActiveMute(operation.getTargetUuid());

            if (activeMute != null) {
                punishmentManager.removePunishment(activeMute.getId());

                // Notify player if online
                if (provider != null) {
                    SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage("§aYou have been unmuted.");
                    }
                }

                return OperationResult.SUCCESS;
            } else {
                return OperationResult.NO_ACTIVE_PUNISHMENT;
            }
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    @Override
    public @NotNull OperationResult executeWarn(@NotNull WarnOperation operation) {
        try {
            PunishmentRecord punishment = new PunishmentRecord(
                    0, 0,
                    operation.getTargetUuid(),
                    operation.getExecutorUuid(),
                    LocalDateTime.now(),
                    null,
                    false, null, false, null,
                    false, null,
                    operation.getReason(),
                    Type.WARN
            );

            punishmentManager.addPunishment(punishment);

            // Notify player if online
            if (provider != null) {
                SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                if (targetPlayer != null) {
                    String reason = operation.getReason() != null ? operation.getReason() : "No reason provided";
                    targetPlayer.sendMessage("§6You have been warned: §f" + reason);
                }
            }

            return OperationResult.SUCCESS;
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    @Override
    public @NotNull OperationResult executeUnwarn(@NotNull UnwarnOperation operation) {
        try {
            // Find the most recent active warn for this player
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

                    // Notify player if online
                    if (provider != null) {
                        SanctionPlayer targetPlayer = provider.getPlayer(operation.getTargetUuid());
                        if (targetPlayer != null) {
                            targetPlayer.sendMessage("§aOne of your warnings has been removed.");
                        }
                    }

                    return OperationResult.SUCCESS;
                }
            }

            return OperationResult.NO_ACTIVE_PUNISHMENT;
        } catch (Exception e) {
            return OperationResult.ERROR;
        }
    }

    private TranslationContext.Punishment buildPunishmentContext(
            PunishmentRecord punishment,
            UUID targetUuid, String targetName,
            UUID executorUuid, String executorName,
            LocalDateTime expiryTime, String reason
    ) {
        return TranslationContext.Punishment.builder()
                .id(punishment.getId())
                .relId(punishment.getRelId())
                .target(targetUuid)
                .targetName(targetName)
                .executor(executorUuid != null ? executorUuid : new UUID(0, 0))
                .operatorName(executorName)
                .executingTime(punishment.getExecutingTime())
                .expiryTime(expiryTime)
                .reason(reason)
                .pluginName(contextTemplate.getPluginName())
                .pluginVersion(contextTemplate.getPluginVersion())
                .build();
    }
}
