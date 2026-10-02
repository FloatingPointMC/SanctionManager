package io.github.floatingpointmc.sanctionmanager.minecraft.command;

import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionCommandArgument;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.BanCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.MuteCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.UnbanCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.UnmuteCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.WarnCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.impl.admin.UnwarnCommand;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import lombok.RequiredArgsConstructor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.CommandDescription;
import org.incendo.cloud.description.Description;

@RequiredArgsConstructor
public class SanctionCommandManager {
    private final CommandManager<SanctionCommandSender> commandManager;
    private final MinecraftSanctionManager sanctionManager;
    private final TranslationConfig translationConfig;
    private final TranslationContext translationContext;

    public void buildCommands(boolean service) {
        commandManager.command(
                commandManager.commandBuilder("sanction")
                        .handler(context -> {
                            for (String line : TranslationFormatter.formatLines(translationConfig.getStringList("description"), translationContext)) {
                                context.sender().sendMessage(line);
                            }
                        })
        );
        if (service) buildServiceCommands();
    }

    private void buildServiceCommands() {
        buildCommand(new BanCommand(sanctionManager, translationConfig, translationContext));
        buildCommand(new UnbanCommand(sanctionManager, translationConfig));
        buildCommand(new MuteCommand(sanctionManager, translationConfig, translationContext));
        buildCommand(new UnmuteCommand(sanctionManager, translationConfig));
        buildCommand(new WarnCommand(sanctionManager, translationConfig, translationContext));
        buildCommand(new UnwarnCommand(sanctionManager, translationConfig));
    }

    private void buildCommand(SanctionCommand command) {
        String descText = translationConfig.getCommandDescription(command.getName());
        commandManager.command(
                commandManager.commandBuilder(command.getName())
                        .commandDescription(CommandDescription.commandDescription(descText))
                        .apply(builder -> {
                            if (command.getArguments() != null) {
                                for (SanctionCommandArgument<?> argument : command.getArguments()) {
                                    String argDesc = translationConfig.getCommandArgumentDescription(command.getName(), argument.getLiteral());
                                    CommandComponent.Builder<SanctionCommandSender, ?> component = CommandComponent.builder(argument.getLiteral(), argument.getParser());
                                    component.description(Description.of(argDesc));
                                    if (argument.isOptional()) component.optional();
                                    if (argument.getSuggestionProvider() != null) component.suggestionProvider(argument.getSuggestionProvider());
                                    builder.argument(component);
                                }
                            }
                            return builder;
                        })
                        .permission(command.getRequiredPermission())
                        .handler(command::execute)
        );
    }
}