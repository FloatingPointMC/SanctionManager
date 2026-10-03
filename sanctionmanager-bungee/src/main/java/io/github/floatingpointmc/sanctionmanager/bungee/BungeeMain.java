package io.github.floatingpointmc.sanctionmanager.bungee;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.bungee.command.BungeeCommandSender;
import io.github.floatingpointmc.sanctionmanager.bungee.command.BungeeSanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.bungee.config.Config;
import io.github.floatingpointmc.sanctionmanager.bungee.listener.PlayerListener;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftProvider;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.SanctionPlayer;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.command.SanctionCommandSender;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import org.bstats.bungeecord.Metrics;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bungee.BungeeCommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BungeeMain extends Plugin {
    private static final int PLUGIN_ID = 34183;
    private Config config;
    private MinecraftSanctionManager manager;

    private final MinecraftProvider provider = new MinecraftProvider() {
        @Override
        public @NotNull Collection<String> getPlayerNames() {
            return getProxy().getPlayers().stream()
                    .map(net.md_5.bungee.api.connection.ProxiedPlayer::getName)
                    .collect(Collectors.toList());
        }

        @Override
        public @NotNull Collection<UUID> getPlayerUUIDs() {
            return getProxy().getPlayers().stream()
                    .map(net.md_5.bungee.api.connection.ProxiedPlayer::getUniqueId)
                    .collect(Collectors.toList());
        }

        @Override
        public @Nullable SanctionPlayer getPlayer(@NotNull UUID uuid) {
            net.md_5.bungee.api.connection.ProxiedPlayer player = getProxy().getPlayer(uuid);
            return player != null ? new BungeeSanctionPlayer(player) : null;
        }

        @Override
        public @Nullable SanctionPlayer getPlayer(@NotNull String name) {
            net.md_5.bungee.api.connection.ProxiedPlayer player = getProxy().getPlayer(name);
            return player != null ? new BungeeSanctionPlayer(player) : null;
        }

        @Override
        public void schedule(@NotNull Runnable task) {
            // BungeeCord doesn't have a separate main thread for most operations
            // Execute immediately - BungeeCord API is generally thread-safe
            task.run();
        }
    };

    @Override
    public void onLoad() {
        config = new Config(getDataFolder(), getLogger());
    }

    @Override
    public void onEnable() {
        config.saveDefaultConfig();
        saveDefaultTranslations();
        new Metrics(this, PLUGIN_ID);
        Configuration cfg = this.config.getConfig();
        String mode = cfg.getString("mode");
        TranslationConfig translationConfig = loadTranslationConfig();
        TranslationContext contextTemplate = TranslationContext.builder()
                .pluginName(getDescription().getName())
                .pluginVersion(getDescription().getVersion())
                .build();

        String binaryDir = getDataFolder().toPath()
                .resolve(cfg.getString("storage.binary.directory", "data"))
                .toString();

        manager = new MinecraftSanctionManager(
                provider,
                cfg.getBoolean("storage.database.enabled", true),
                cfg.getString("storage.database.driver", "com.mysql.cj.jdbc.Driver"),
                cfg.getString("storage.database.host", "localhost"),
                cfg.getInt("storage.database.port", 3306),
                cfg.getString("storage.database.database", "sanctionmanager"),
                cfg.getString("storage.database.user", "root"),
                cfg.getString("storage.database.password", ""),
                cfg.getBoolean("storage.redis.enabled", false),
                cfg.getString("storage.redis.host", "localhost"),
                cfg.getInt("storage.redis.port", 6379),
                cfg.getString("storage.redis.password", ""),
                binaryDir);

        BungeeCommandManager<SanctionCommandSender> commandManager =
                new BungeeCommandManager<>(this, ExecutionCoordinator.asyncCoordinator(),
                        SenderMapper.create(
                                BungeeCommandSender::new,
                                mapped -> ((BungeeCommandSender) mapped).commandSender
                        ));
        new SanctionCommandManager(commandManager, manager, translationConfig, contextTemplate).buildCommands("standalone".equals(mode));
        if ("standalone".equals(mode)) {
            getLogger().info("SanctionManager is running in standalone mode.");
        } else {
            getLogger().info("SanctionManager is running in proxy mode, no commands available.");
        }
        getProxy().getPluginManager().registerListener(this,
                new PlayerListener(manager, SanctionManagerAPI.getAPI().getPunishManager(), translationConfig, contextTemplate));
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
            manager = null;
        }
    }

    private TranslationConfig loadTranslationConfig() {
        File file = new File(getDataFolder(), "translations.yml");
        if (!file.exists()) {
            config.saveResource("translations.yml", false);
        }
        Configuration cfg = this.config.loadConfiguration(file);
        return new TranslationConfig(flattenConfiguration(cfg));
    }

    private Map<String, Object> flattenConfiguration(Configuration cfg) {
        Map<String, Object> result = new HashMap<>();
        for (String key : cfg.getKeys()) {
            Object value = cfg.get(key);
            if (value instanceof Configuration) {
                Map<String, Object> nested = flattenConfiguration((Configuration) value);
                for (Map.Entry<String, Object> entry : nested.entrySet()) {
                    result.put(key + "." + entry.getKey(), entry.getValue());
                }
                result.put(key, nested);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private void saveDefaultTranslations() {
        File file = new File(getDataFolder(), "translations.yml");
        if (!file.exists()) {
            config.saveResource("translations.yml", false);
        }
    }
}