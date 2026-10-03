package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.WarnOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.OperationResult;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.SanctionService;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

public class WarnCommand extends AdminCommand {
    private final @NotNull MinecraftSanctionManager manager;
    private final @NotNull TranslationConfig translationConfig;
    private final @NotNull TranslationContext contextTemplate;

    public WarnCommand(@NotNull MinecraftSanctionManager manager, @NotNull TranslationConfig translationConfig, @NotNull TranslationContext contextTemplate) {
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

        // Resolve player UUID and name
        SanctionPlayer targetPlayer = provider.getPlayer(targetName);
        UUID targetUuid;
        String resolvedName;

        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
            resolvedName = targetPlayer.getName();
        } else {
            targetUuid = manager.getPlayerService().findUuidByName(targetName);
            if (targetUuid == null) {
                sender.sendMessage("Player '" + targetName + "' not found. They may have never joined this server.");
                return;
            }
            resolvedName = manager.getPlayerService().findNameByUuid(targetUuid);
            if (resolvedName == null) {
                resolvedName = targetName;
            }
        }

        // Get executor UUID
        UUID executorUuid = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getUniqueId() : null;

        // Parse duration
        Duration duration = null;
        if (durationStr != null) {
            long seconds = parseDuration(durationStr);
            duration = Duration.ofSeconds(seconds);
        }

        // Create operation object (without names)
        WarnOperation operation = new WarnOperation(
                targetUuid,
                executorUuid,
                duration,
                reason
        );

        // Execute via service
        SanctionService service = manager.getSanctionService();
        if (service == null) {
            sender.sendMessage("§cSanctionService is not initialized.");
            return;
        }

        OperationResult result = service.executeWarn(operation);

        // Handle result
        switch (result) {
            case SUCCESS:
                String displayReason = reason != null ? reason : "No reason provided";
                if (duration != null) {
                    sender.sendMessage("§6Warned §f" + resolvedName + " §6for §f" + durationStr + " §6for: §f" + displayReason);
                } else {
                    sender.sendMessage("§6Warned §f" + resolvedName + " §6for: §f" + displayReason);
                }
                break;

            case ERROR:
                sender.sendMessage("§cFailed to warn player.");
                break;

            default:
                sender.sendMessage("§cUnexpected result: " + result);
                break;
        }
    }

    /**
     * Parse duration string (e.g., "30m", "1h", "7d") to seconds.
     * Supports: s (seconds), m (minutes), h (hours), d (days), w (weeks), mo (months), y (years)
     */
    private long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 0;
        }

        long totalSeconds = 0;
        StringBuilder numberBuffer = new StringBuilder();

        for (int i = 0; i < durationStr.length(); i++) {
            char c = durationStr.charAt(i);

            if (Character.isDigit(c)) {
                numberBuffer.append(c);
            } else {
                if (numberBuffer.length() == 0) {
                    continue;
                }

                long number = Long.parseLong(numberBuffer.toString());
                numberBuffer.setLength(0);

                // Check for two-character units
                String unit;
                if (i + 1 < durationStr.length() && durationStr.charAt(i + 1) == 'o' && c == 'm') {
                    unit = "mo";
                    i++; // skip 'o'
                } else {
                    unit = String.valueOf(c);
                }

                switch (unit) {
                    case "s":
                        totalSeconds += number;
                        break;
                    case "m":
                        totalSeconds += number * 60;
                        break;
                    case "h":
                        totalSeconds += number * 3600;
                        break;
                    case "d":
                        totalSeconds += number * 86400;
                        break;
                    case "w":
                        totalSeconds += number * 604800;
                        break;
                    case "mo":
                        totalSeconds += number * 2592000; // 30 days
                        break;
                    case "y":
                        totalSeconds += number * 31536000; // 365 days
                        break;
                }
            }
        }

        return totalSeconds;
    }

    @Override
    public @NotNull String getName() {
        return "warn";
    }

    @Override
    public @Nullable Collection<SanctionCommandArgument<?>> getArguments() {
        SuggestionProvider<SanctionCommandSender> playerSuggestions = SuggestionProvider.suggestingStrings(
                manager.getProvider() != null ? manager.getProvider().getPlayerNames() : java.util.Collections.emptyList()
        );
        return Arrays.asList(
                SanctionCommandArgument.build("player", StringParser.stringParser()).suggestionProvider(playerSuggestions),
                SanctionCommandArgument.build("duration", StringParser.stringParser()).optional(),
                SanctionCommandArgument.build("reason", StringParser.greedyStringParser()).optional()
        );
    }

    @Override
    public @NotNull String getPermission() {
        return "sanctionmanager.warn";
    }
}
