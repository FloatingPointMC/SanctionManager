package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Collection;
import java.util.UUID;

/**
 * Unmute command implementation with offline player support.
 * <p>
 * Allows unmuting players by name, resolving offline players
 * through PlayerRepository.
 */
public class UnmuteCommand extends AdminCommand {
    private final @NotNull MinecraftSanctionManager manager;
    private final @NotNull TranslationConfig translationConfig;

    public UnmuteCommand(@NotNull MinecraftSanctionManager manager, @NotNull TranslationConfig translationConfig) {
        this.manager = manager;
        this.translationConfig = translationConfig;
    }

    @Override
    public void execute(@NotNull CommandContext<SanctionCommandSender> context) {
        SanctionCommandSender sender = context.sender();
        String targetName = context.get("player");

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

        // Query active mute and remove it
        PunishmentManagerAPI punishManager = manager.getPunishmentManager();
        Punishment activeMute = punishManager.queryActiveMute(targetUuid);

        if (activeMute != null) {
            punishManager.removePunishment(activeMute.getId());
            sender.sendMessage("§aUnmuted " + resolvedName);

            // Notify online player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage("§aYou have been unmuted.");
            }
        } else {
            sender.sendMessage("§cNo active mute found for " + resolvedName);
        }
    }

    @Override
    public @NotNull String getName() {
        return "unmute";
    }

    @Override
    public @Nullable Collection<SanctionCommandArgument<?>> getArguments() {
        SuggestionProvider<SanctionCommandSender> playerSuggestions = SuggestionProvider.suggestingStrings(
                manager.getProvider() != null ? manager.getProvider().getPlayerNames() : Collections.emptyList()
        );
        return Collections.singletonList(
                SanctionCommandArgument.build("player", StringParser.stringParser()).suggestionProvider(playerSuggestions)
        );
    }

    @Override
    public @NotNull String getPermission() {
        return "sanctionmanager.unmute";
    }
}
