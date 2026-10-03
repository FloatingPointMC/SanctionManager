package io.github.floatingpointmc.sanctionmanager.minecraft;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerService;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerServiceFactory;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.SanctionService;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.StandaloneSanctionService;
import io.github.floatingpointmc.sanctionmanager.minecraft.service.BridgeSanctionService;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinecraftSanctionManager {
    private final @NotNull SanctionManagerCore core;
    private final @Nullable MinecraftProvider provider;
    private final @NotNull PlayerService playerService;
    private @NotNull SanctionService sanctionService;

    public MinecraftSanctionManager(@NotNull MinecraftProvider provider, @NotNull StorageConfig storageConfig) {
        this.provider = provider;
        this.core = new SanctionManagerCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public MinecraftSanctionManager(@NotNull MinecraftProvider provider, boolean databaseEnabled, @NotNull String driver, @NotNull String host, int port,
                                    @NotNull String database, @NotNull String user, @NotNull String password,
                                    boolean redisEnabled, @NotNull String redisHost, int redisPort, @NotNull String redisPassword,
                                    @NotNull String binaryDataDir) {
        this.provider = provider;
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
                .driver(driver)
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .build();
        RedisConfig redisConfig = RedisConfig.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword)
                .build();
        StorageConfig storageConfig = StorageConfig.builder()
                .databaseEnabled(databaseEnabled)
                .redisEnabled(redisEnabled)
                .databaseConfig(databaseConfig)
                .redisConfig(redisConfig)
                .binaryDataDir(binaryDataDir)
                .build();
        this.core = new SanctionManagerCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public MinecraftSanctionManager(@NotNull StorageConfig storageConfig) {
        this.provider = null;
        this.core = new SanctionManagerCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public MinecraftSanctionManager(boolean databaseEnabled, @NotNull String driver, @NotNull String host, int port,
                                    @NotNull String database, @NotNull String user, @NotNull String password,
                                    boolean redisEnabled, @NotNull String redisHost, int redisPort, @NotNull String redisPassword,
                                    @NotNull String binaryDataDir) {
        this.provider = null;
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
                .driver(driver)
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .build();
        RedisConfig redisConfig = RedisConfig.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword)
                .build();
        StorageConfig storageConfig = StorageConfig.builder()
                .databaseEnabled(databaseEnabled)
                .redisEnabled(redisEnabled)
                .databaseConfig(databaseConfig)
                .redisConfig(redisConfig)
                .binaryDataDir(binaryDataDir)
                .build();
        this.core = new SanctionManagerCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public @NotNull PunishmentManagerAPI getPunishmentManager() {
        return core.getPunishManager();
    }

    public @NotNull SanctionManagerCore getCore() {
        return core;
    }

    public @Nullable MinecraftProvider getProvider() {
        return provider;
    }

    public @NotNull PlayerService getPlayerService() {
        return playerService;
    }

    public @NotNull SanctionService getSanctionService() {
        return sanctionService;
    }

    /**
     * Initialize the SanctionService based on the execution mode.
     *
     * @param isStandalone true for standalone mode, false for bridge mode
     * @param translationConfig translation configuration
     * @param contextTemplate translation context template
     */
    public void initializeSanctionService(
            boolean isStandalone,
            @NotNull TranslationConfig translationConfig,
            @NotNull TranslationContext contextTemplate
    ) {
        if (isStandalone) {
            this.sanctionService = new StandaloneSanctionService(
                    core.getPunishManager(),
                    provider,
                    translationConfig,
                    contextTemplate
            );
        } else {
            this.sanctionService = new BridgeSanctionService();
        }
    }

    public void shutdown() {
        sanctionService.shutdown();
        playerService.close();
        core.shutdown();
    }

    /**
     * Initialize Minecraft's own PlayerService (Repository + Cache).
     * <p>
     * Architecture:
     * - PlayerRepository (Persistence): Database or File
     * - PlayerCache (Cache): Redis or Memory
     * <p>
     * This is SEPARATE from Core's punishment storage.
     * Minecraft owns player data (UUID <-> Name mapping).
     * Core owns sanction data (Punishment, Ban, Mute).
     * <p>
     * The PlayerService uses the same storage configuration as Core
     * but maintains its own data structures (separate tables/files/keys).
     */
    private @NotNull PlayerService initializePlayerService(@NotNull StorageConfig storageConfig) {
        // Access Core's infrastructure for Minecraft's own data
        com.zaxxer.hikari.HikariDataSource dataSource = null;
        redis.clients.jedis.JedisPool jedisPool = null;

        // Get DataSource if database is enabled
        if (core.isDatabaseEnabled() && core.getRepository() instanceof io.github.floatingpointmc.sanctionmanager.core.repository.HikariPunishmentRepository) {
            io.github.floatingpointmc.sanctionmanager.core.repository.HikariPunishmentRepository hikariRepo =
                    (io.github.floatingpointmc.sanctionmanager.core.repository.HikariPunishmentRepository) core.getRepository();
            dataSource = hikariRepo.getDataSource();
        }

        // Get JedisPool if redis is enabled
        if (core.isRedisEnabled()) {
            // Redis client is private in Core, so we'll need to create our own JedisPool
            // for now, pass null and let factory fall back to Memory cache
            // TODO: Consider exposing JedisPool from Core or creating separate pool for Minecraft
            jedisPool = null;
        }

        String dataDir = storageConfig.getBinaryDataDir() != null ? storageConfig.getBinaryDataDir() : ".";

        return PlayerServiceFactory.create(dataSource, jedisPool, dataDir);
    }
}