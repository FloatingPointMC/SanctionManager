package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.MuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.OperationResult;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.SanctionService;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DurationParser;
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

        // Resolve player UUID
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

        // Parse duration
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

        // Get executor UUID
        UUID executorUuid = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getUniqueId() : null;
        String executorName = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getName() : "[Console]";

        // Create operation object (without names)
        MuteOperation operation = new MuteOperation(
                targetUuid,
                executorUuid,
                expiryTime,
                reason
        );

        // Execute via service (async)
        SanctionService service = manager.getSanctionService();
        if (service == null) {
            sender.sendMessage("§cSanctionService is not initialized.");
            return;
        }

        OperationResult result = service.executeMute(operation);

        // Handle result
        switch (result) {
            case SUCCESS:
                TranslationContext.Punishment msgContext = TranslationContext.Punishment.builder()
                        .id(0)
                        .relId(0)
                        .target(targetUuid)
                        .targetName(resolvedName)
                        .executor(executorUuid != null ? executorUuid : new UUID(0, 0))
                        .operatorName(executorName)
                        .executingTime(LocalDateTime.now())
                        .expiryTime(expiryTime)
                        .reason(reason)
                        .pluginName(contextTemplate.getPluginName())
                        .pluginVersion(contextTemplate.getPluginVersion())
                        .build();

                boolean isTemp = expiryTime != null;
                java.util.List<String> lines = isTemp
                        ? translationConfig.getStringList("mute.temporary")
                        : translationConfig.getStringList("mute.permanent");

                for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                    sender.sendMessage(line);
                }
                break;

            case ERROR:
                sender.sendMessage("§cFailed to mute player.");
                break;

            default:
                sender.sendMessage("§cUnexpected result: " + result);
                break;
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
                SanctionCommandArgument.build("duration", DurationParser.durationParser()).optional(),
                SanctionCommandArgument.build("reason", StringParser.stringParser()).optional()
        );
    }

    @Override
    public @NotNull String getPermission() {
        return "sanctionmanager.mute";
    }
}
