package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

/**
 * Mute command implementation with offline player support.
 * <p>
 * Allows muting players who have previously joined the server,
 * even if they are currently offline.
 */
public class MuteCommand extends AdminCommand {
    private final @NotNull MinecraftSanctionManager manager;
    private final @NotNull TranslationConfig translationConfig;
    private final @NotNull TranslationContext contextTemplate;

    public MuteCommand(@NotNull MinecraftSanctionManager manager, @NotNull TranslationConfig translationConfig, @NotNull TranslationContext contextTemplate) {
        this.manager = manager;
        this.translationConfig = translationConfig;
        this.contextTemplate = contextTemplate;
    }

    @Override
    public void execute(@NotNull CommandContext<SanctionCommandSender> context) {
        SanctionCommandSender sender = context.sender();
        String targetName = context.get("player");
        String durationStr = context.<String>optional("duration").orElse(null);
        String reason = context.<String>optional("reason").orElse(null);

        MinecraftProvider provider = manager.getProvider();
        if (provider == null) {
            sender.sendMessage("MinecraftProvider is not available.");
            return;
        }

        // Try to resolve UUID from online player first, then from PlayerRepository
        SanctionPlayer targetPlayer = provider.getPlayer(targetName);
        UUID targetUuid;
        String resolvedName;

        if (targetPlayer != null) {
            // Player is online
            targetUuid = targetPlayer.getUniqueId();
            resolvedName = targetPlayer.getName();
        } else {
            // Player is offline - query PlayerRepository
            targetUuid = manager.getPlayerRepository().findUuidByName(targetName);
            if (targetUuid == null) {
                String errorMsg = translationConfig.get("error.player-not-found");
                sender.sendMessage(errorMsg.replace("{0}", targetName));
                return;
            }
            resolvedName = manager.getPlayerRepository().findNameByUuid(targetUuid);
            if (resolvedName == null) {
                resolvedName = targetName; // Fallback to input name
            }
        }

        LocalDateTime expiryTime = null;
        if (durationStr != null) {
            try {
                long seconds = parseDuration(durationStr);
                expiryTime = LocalDateTime.now().plusSeconds(seconds);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid duration format: " + durationStr);
                return;
            }
        }

        UUID executorUuid = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getUniqueId() : null;
        String operatorName = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getName() : "[Console]";

        PunishmentRecord punishment = new PunishmentRecord(
                0, 0,
                targetUuid,
                executorUuid,
                LocalDateTime.now(),
                expiryTime,
                false, null, false, null,
                false, null,
                reason,
                Type.MUTE
        );

        PunishmentManagerAPI punishManager = manager.getPunishmentManager();
        punishManager.addPunishment(punishment);

        TranslationContext.Punishment msgContext = TranslationContext.Punishment.builder()
                .id(punishment.getId())
                .relId(punishment.getRelId())
                .target(targetUuid)
                .targetName(resolvedName)
                .executor(executorUuid != null ? executorUuid : new UUID(0, 0))
                .operatorName(operatorName)
                .executingTime(punishment.getExecutingTime())
                .expiryTime(expiryTime)
                .reason(reason)
                .pluginName(contextTemplate.getPluginName())
                .pluginVersion(contextTemplate.getPluginVersion())
                .build();

        boolean isTemp = expiryTime != null;
        java.util.List<String> lines = isTemp ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");

        String confirmMsg = "§aMuted " + resolvedName + (isTemp ? " for " + durationStr : " permanently") + (reason != null ? " (Reason: " + reason + ")" : "");
        sender.sendMessage(confirmMsg);

        // Notify online player if they're online
        if (targetPlayer != null) {
            for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                targetPlayer.sendMessage(line);
            }
        }
    }

    private long parseDuration(@NotNull String input) {
        long totalSeconds = 0;
        StringBuilder number = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() == 0) {
                    throw new IllegalArgumentException("Invalid duration: " + input);
                }
                long value = Long.parseLong(number.toString());
                number.setLength(0);
                switch (c) {
                    case 's':
                        totalSeconds += value;
                        break;
                    case 'm':
                        totalSeconds += value * 60;
                        break;
                    case 'h':
                        totalSeconds += value * 3600;
                        break;
                    case 'd':
                        totalSeconds += value * 86400;
                        break;
                    case 'w':
                        totalSeconds += value * 604800;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown duration unit: " + c);
                }
            }
        }
        if (number.length() > 0) {
            totalSeconds += Long.parseLong(number.toString());
        }
        if (totalSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + input);
        }
        return totalSeconds;
    }

    @Override
    public @NotNull String getName() {
        return "mute";
    }

    @Override
    public @Nullable Collection<SanctionCommandArgument<?>> getArguments() {
        SuggestionProvider<SanctionCommandSender> playerSuggestions = SuggestionProvider.suggestingStrings(
                manager.getProvider() != null ? manager.getProvider().getPlayerNames() : java.util.Collections.emptyList()
        );
        return Arrays.asList(
                SanctionCommandArgument.build("player", StringParser.stringParser()).suggestionProvider(playerSuggestions),
                SanctionCommandArgument.build("duration", StringParser.stringParser()).optional(),
                SanctionCommandArgument.build("reason", StringParser.stringParser()).optional()
        );
    }

    @Override
    public @NotNull String getPermission() {
        return "sanctionmanager.mute";
    }
}
