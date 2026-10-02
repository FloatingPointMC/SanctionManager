package io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin;

import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.AdminCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnbanOperation;
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
 * Unban command implementation with offline player support.
 * <p>
 * Allows unbanning players by name, resolving offline players
 * through PlayerRepository.
 */
public class UnbanCommand extends AdminCommand {
    private final @NotNull MinecraftSanctionManager manager;
    private final @NotNull TranslationConfig translationConfig;

    public UnbanCommand(@NotNull MinecraftSanctionManager manager, @NotNull TranslationConfig translationConfig) {
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
        UnbanOperation operation = new UnbanOperation(
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

        OperationResult result = service.executeUnban(operation);

        // Handle result
        switch (result) {
            case SUCCESS:
                sender.sendMessage("§aUnbanned " + resolvedName);
                break;

            case NO_ACTIVE_PUNISHMENT:
                sender.sendMessage("§cNo active ban found for " + resolvedName);
                break;

            case ERROR:
                sender.sendMessage("§cFailed to unban player.");
                break;

            default:
                sender.sendMessage("§cUnexpected result: " + result);
                break;
        }
    }

    @Override
    public @NotNull String getName() {
        return "unban";
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
        return "sanctionmanager.unban";
    }
}
