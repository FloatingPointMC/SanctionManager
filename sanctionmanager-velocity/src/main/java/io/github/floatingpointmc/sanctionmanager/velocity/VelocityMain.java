package io.github.floatingpointmc.sanctionmanager.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.velocity.command.VelocityCommandSender;
import io.github.floatingpointmc.sanctionmanager.velocity.command.VelocitySanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.velocity.config.Config;
import io.github.floatingpointmc.sanctionmanager.velocity.listener.PlayerListener;
import org.bstats.velocity.Metrics;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class VelocityMain {
    private static final int PLUGIN_ID = 34210;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer pluginContainer;
    private final Metrics.Factory metricsFactory;
    private MinecraftSanctionManager manager;

    private final MinecraftProvider provider = new MinecraftProvider() {
        @Override
        public @NotNull Collection<String> getPlayerNames() {
            return proxy.getAllPlayers().stream()
                    .map(com.velocitypowered.api.proxy.Player::getUsername)
                    .collect(Collectors.toList());
        }

        @Override
        public @NotNull Collection<UUID> getPlayerUUIDs() {
            return proxy.getAllPlayers().stream()
                    .map(com.velocitypowered.api.proxy.Player::getUniqueId)
                    .collect(Collectors.toList());
        }

        @Override
        public @Nullable SanctionPlayer getPlayer(@NotNull UUID uuid) {
            return proxy.getPlayer(uuid)
                    .map(VelocitySanctionPlayer::new)
                    .orElse(null);
        }

        @Override
        public @Nullable SanctionPlayer getPlayer(@NotNull String name) {
            return proxy.getPlayer(name)
                    .map(VelocitySanctionPlayer::new)
                    .orElse(null);
        }
    };

    @Inject
    public VelocityMain(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, PluginContainer pluginContainer, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginContainer = pluginContainer;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Config config = new Config(dataDirectory, logger);
        config.saveDefaultConfig();
        config.saveDefaultTranslations();
        metricsFactory.make(this, PLUGIN_ID);

        String mode = config.getString("mode");
        TranslationConfig translationConfig = loadTranslationConfig(config);
        TranslationContext contextTemplate = TranslationContext.builder()
                .pluginName(pluginContainer.getDescription().getName().orElse("SanctionManager"))
                .pluginVersion(pluginContainer.getDescription().getVersion().orElse("unknown"))
                .build();

        String binaryDir = dataDirectory
                .resolve(config.getString("storage.binary.directory", "data"))
                .toString();

        manager = new MinecraftSanctionManager(
                provider,
                config.getBoolean("storage.database.enabled", true),
                config.getString("storage.database.driver", "com.mysql.cj.jdbc.Driver"),
                config.getString("storage.database.host", "localhost"),
                config.getInt("storage.database.port", 3306),
                config.getString("storage.database.database", "sanctionmanager"),
                config.getString("storage.database.user", "root"),
                config.getString("storage.database.password", ""),
                config.getBoolean("storage.redis.enabled", false),
                config.getString("storage.redis.host", "localhost"),
                config.getInt("storage.redis.port", 6379),
                config.getString("storage.redis.password", ""),
                binaryDir);

        if ("standalone".equals(mode)) {
            // Initialize SanctionService for standalone mode
            manager.initializeSanctionService(true, translationConfig, contextTemplate);

            VelocityCommandManager<SanctionCommandSender> commandManager =
                    new VelocityCommandManager<>(pluginContainer, proxy,
                            ExecutionCoordinator.asyncCoordinator(),
                            SenderMapper.create(
                                    VelocityCommandSender::new,
                                    mapped -> ((VelocityCommandSender) mapped).commandSource
                            ));
            new SanctionCommandManager(commandManager, manager, translationConfig, contextTemplate).buildCommands(true);

            proxy.getEventManager().register(this, new PlayerListener(
                    manager, SanctionManagerAPI.getAPI().getPunishManager(), translationConfig, contextTemplate));

            logger.info("SanctionManager is running in standalone mode.");
        } else {
            logger.warn("SanctionManager is running in proxy mode, no commands available.");
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (manager != null) {
            manager.shutdown();
            manager = null;
        }
    }

    private TranslationConfig loadTranslationConfig(Config config) {
        config.reloadTranslations();
        return config.getTranslationConfig();
    }
}