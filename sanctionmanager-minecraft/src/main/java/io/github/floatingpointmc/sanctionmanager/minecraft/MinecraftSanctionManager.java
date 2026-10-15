package io.github.floatingpointmc.sanctionmanager.minecraft;

import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerManager;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerService;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerServiceFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinecraftSanctionManager {
    private final @NotNull SanctionManagerManager core;
    private final @Nullable MinecraftProvider provider;
    private com.zaxxer.hikari.HikariDataSource playerDatabase;
    private redis.clients.jedis.JedisPool playerRedis;
    private final @NotNull PlayerService playerService;

    public MinecraftSanctionManager(@NotNull MinecraftProvider provider, @NotNull StorageConfig storageConfig) {
        this.provider = provider;
        this.core = initializeCore(storageConfig);
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
        this.core = initializeCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public MinecraftSanctionManager(@NotNull StorageConfig storageConfig) {
        this.provider = null;
        this.core = initializeCore(storageConfig);
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
        this.core = initializeCore(storageConfig);
        this.playerService = initializePlayerService(storageConfig);
    }

    public @NotNull EntryService getEntryService() {
        return core.getEntryService();
    }

    public @NotNull SanctionManager getCore() {
        return core;
    }

    public @Nullable MinecraftProvider getProvider() {
        return provider;
    }

    public @NotNull PlayerService getPlayerService() {
        return playerService;
    }

    public void shutdown() {
        try { playerService.close(); } finally {
            core.close();
            if (playerDatabase != null) playerDatabase.close();
            if (playerRedis != null) playerRedis.close();
        }
    }

    private SanctionManagerManager initializeCore(StorageConfig storage) {
        if (!storage.isDatabaseEnabled() && java.nio.file.Files.exists(
                java.nio.file.Paths.get(storage.getBinaryDataDir(), "sanction-data.bin")))
            throw new io.github.floatingpointmc.sanctionmanager.api.exception.PersistenceException(
                    "Legacy binary sanctions require explicit host data conversion before initialization");
        String url = storage.isDatabaseEnabled() ? storage.getDatabaseConfig().getJdbcUrl()
                : "jdbc:h2:file:" + java.nio.file.Paths.get(storage.getBinaryDataDir(), "sanctions").toAbsolutePath();
        io.github.floatingpointmc.sanctionmanager.api.config.CredentialsProvider credentials = consumer -> {
            DatabaseConfig db = storage.getDatabaseConfig();
            char[] password = storage.isDatabaseEnabled() ? db.getPassword().toCharArray() : new char[0];
            try { consumer.accept(storage.isDatabaseEnabled() ? db.getUser() : "", password); }
            finally { java.util.Arrays.fill(password, '\0'); }
        };
        SanctionManagerManager result = new SanctionManagerManager(
                new CoreConfig(url, credentials));
        result.getTypeRegistry().register(MinecraftEntryTypes.BAN);
        result.getTypeRegistry().register(MinecraftEntryTypes.MUTE);
        try { result.initialize(); return result; }
        catch (RuntimeException failure) { result.close(); throw failure; }
    }

    private @NotNull PlayerService initializePlayerService(@NotNull StorageConfig storage) {
        try {
            if (storage.isDatabaseEnabled()) {
                DatabaseConfig db = storage.getDatabaseConfig();
                com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
                config.setJdbcUrl(db.getJdbcUrl());
                config.setUsername(db.getUser());
                config.setPassword(db.getPassword());
                playerDatabase = new com.zaxxer.hikari.HikariDataSource(config);
            }
            if (storage.isRedisEnabled()) {
                RedisConfig redis = storage.getRedisConfig();
                playerRedis = new redis.clients.jedis.JedisPool(new redis.clients.jedis.JedisPoolConfig(),
                        redis.getHost(), redis.getPort(), 2000,
                        redis.getPassword().isEmpty() ? null : redis.getPassword());
            }
            String dir = storage.getBinaryDataDir() == null ? "." : storage.getBinaryDataDir();
            return PlayerServiceFactory.create(playerDatabase, playerRedis, dir);
        } catch (RuntimeException failure) {
            if (playerDatabase != null) playerDatabase.close();
            if (playerRedis != null) playerRedis.close();
            core.close();
            throw failure;
        }
    }
}
