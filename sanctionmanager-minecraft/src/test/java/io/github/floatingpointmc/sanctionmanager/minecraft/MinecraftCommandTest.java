package io.github.floatingpointmc.sanctionmanager.minecraft;

import io.github.floatingpointmc.sanctionmanager.core.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class MinecraftCommandTest {
    @Test
    public void testBan() throws NoSuchFieldException, IllegalAccessException {
        MinecraftSanctionManager manager = new MinecraftSanctionManager(new MinecraftProvider() {
            @Override
            public @Unmodifiable @NotNull Collection<String> getPlayerNames() {
                return Collections.emptyList();
            }

            @Override
            public @Unmodifiable @NotNull Collection<UUID> getPlayerUUIDs() {
                return Collections.emptyList();
            }

            @Override
            public @Nullable SanctionPlayer getPlayer(@NotNull UUID uuid) {
                return null;
            }

            @Override
            public @Nullable SanctionPlayer getPlayer(@NotNull String name) {
                return null;
            }

            @Override
            public void schedule(@NotNull Runnable task) {
                // Execute immediately in test environment
                task.run();
            }
        }, new StorageConfig());
        SanctionCommandManager commandManager = new SanctionCommandManager(new CommandManager<SanctionCommandSender>(ExecutionCoordinator.asyncCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(@NotNull SanctionCommandSender sender, @NonNull String permission) {
                return true;
            }
        }, manager, TranslationConfig.defaults(), TranslationContext.builder().build());
        commandManager.buildCommands(true);
        Field field = SanctionCommandManager.class.getDeclaredField("commandManager");
        field.setAccessible(true);
        CommandManager<SanctionCommandSender> cm = (CommandManager<SanctionCommandSender>) field.get(commandManager);
        SanctionCommandSender sender = new SanctionCommandSender() {
            @Override
            public void sendMessage(String message) {
                System.out.println(message);
            }

            @Override
            public boolean hasPermission(String permission) {
                return true;
            }
        };
        cm.commandExecutor().executeCommand(sender, "ban vlouboos 1y2d3m4h5min6s test");
    }
}
