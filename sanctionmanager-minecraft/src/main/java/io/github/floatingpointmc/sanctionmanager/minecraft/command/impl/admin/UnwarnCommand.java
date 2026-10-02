package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnwarnOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.OperationResult;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.SanctionService;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Collection;
import java.util.UUID;

/**
 * Unwarn command implementation with offline player support.
 * <p>
 * Removes the most recent active warning from a player.
 */
public class UnwarnCommand extends AdminCommand {
    private final @NotNull MinecraftSanctionManager manager;
    private final @NotNull TranslationConfig translationConfig;

    public UnwarnCommand(@NotNull MinecraftSanctionManager manager, @NotNull TranslationConfig translationConfig) {
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
                String errorMsg = translationConfig.get("error.player-not-found");
                sender.sendMessage(errorMsg.replace("{0}", targetName));
                return;
            }
            resolvedName = manager.getPlayerService().findNameByUuid(targetUuid);
            if (resolvedName == null) {
                resolvedName = targetName;
            }
        }

        // Get executor info
        UUID executorUuid = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getUniqueId() : null;
        String executorName = sender instanceof SanctionPlayer ? ((SanctionPlayer) sender).getName() : "[Console]";

        // Create operation object
        UnwarnOperation operation = new UnwarnOperation(
                targetUuid,
                resolvedName,
                executorUuid,
                executorName
        );

        // Execute via service
        SanctionService service = manager.getSanctionService();
        if (service == null) {
            sender.sendMessage("§cSanctionService is not initialized.");
            return;
        }

        OperationResult result = service.executeUnwarn(operation);

        // Handle result
        switch (result) {
            case SUCCESS:
                sender.sendMessage("§aRemoved warning from " + resolvedName);
                break;

            case NO_ACTIVE_PUNISHMENT:
                sender.sendMessage("§cNo active warnings found for " + resolvedName);
                break;

            case ERROR:
                sender.sendMessage("§cFailed to remove warning.");
                break;

            default:
                sender.sendMessage("§cUnexpected result: " + result);
                break;
        }
    }

    @Override
    public @NotNull String getName() {
        return "unwarn";
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
        return "sanctionmanager.unwarn";
    }
}
