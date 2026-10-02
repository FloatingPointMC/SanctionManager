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
 * Warn command implementation with offline player support.
 * <p>
 * Allows warning players who have previously joined the server,
 * even if they are currently offline.
 */
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
            targetUuid = manager.getPlayerService().findUuidByName(targetName);
            if (targetUuid == null) {
                String errorMsg = translationConfig.get("error.player-not-found");
                sender.sendMessage(errorMsg.replace("{0}", targetName));
                return;
            }
            resolvedName = manager.getPlayerService().findNameByUuid(targetUuid);
            if (resolvedName == null) {
                resolvedName = targetName; // Fallback to input name
            }
        }

        UUID executorUuid = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getUniqueId() : null;
        String operatorName = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getName() : "[Console]";

        PunishmentRecord punishment = new PunishmentRecord(
                0, 0,
                targetUuid,
                executorUuid,
                LocalDateTime.now(),
                null, // Warns don't have expiry
                false, null, false, null,
                false, null,
                reason,
                Type.WARN
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
                .expiryTime(null)
                .reason(reason)
                .pluginName(contextTemplate.getPluginName())
                .pluginVersion(contextTemplate.getPluginVersion())
                .build();

        java.util.List<String> lines = translationConfig.getStringList("warn.message");

        String confirmMsg = "§aWarned " + resolvedName + (reason != null ? " (Reason: " + reason + ")" : "");
        sender.sendMessage(confirmMsg);

        // Notify online player if they're online
        if (targetPlayer != null) {
            for (String line : TranslationFormatter.formatLines(lines, msgContext)) {
                targetPlayer.sendMessage(line);
            }
        }
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
                SanctionCommandArgument.build("reason", StringParser.stringParser()).optional()
        );
    }

    @Override
    public @NotNull String getPermission() {
        return "sanctionmanager.warn";
    }
}
