package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.PunishmentFactory;
import io.github.floatingpointmc.sanctionmanager.core.cache.LocalPunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.cache.PunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.cache.PunishmentSerializer;
import io.github.floatingpointmc.sanctionmanager.core.cache.RedisPunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.PunishmentFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.PunishmentManager;
import io.github.floatingpointmc.sanctionmanager.core.repository.BinaryPunishmentRepository;
import io.github.floatingpointmc.sanctionmanager.core.repository.HikariPunishmentRepository;
import io.github.floatingpointmc.sanctionmanager.core.repository.PunishmentRepository;
import io.github.floatingpointmc.sanctionmanager.core.service.PunishmentService;
import io.github.vlouboos.standaloneevent.api.ApiProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.RedisClient;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SanctionManagerCore implements SanctionManager {
    private final @NotNull PunishmentManager punishmentManager;
    private final @NotNull PunishmentFactory punishmentFactory;
    private final @NotNull PunishmentRepository repository;
    private final @NotNull PunishmentCache cache;
    private final @Nullable RedisClient redisClient;

    public SanctionManagerCore(@NotNull StorageConfig storageConfig) {
        ApiProvider.injectApi(false);

        if (storageConfig.isDatabaseEnabled()) {
            assert storageConfig.getDatabaseConfig() != null;
            repository = new HikariPunishmentRepository(storageConfig.getDatabaseConfig());
        } else {
            assert storageConfig.getBinaryDataDir() != null;
            Path dataDir = Paths.get(storageConfig.getBinaryDataDir());
            repository = new BinaryPunishmentRepository(dataDir);
        }

        if (storageConfig.isRedisEnabled()) {
            assert storageConfig.getRedisConfig() != null;
            redisClient = RedisClient.create(
                    "redis://" +
                    (storageConfig.getRedisConfig().getPassword().isEmpty() ? "" :
                            ":" + storageConfig.getRedisConfig().getPassword() + "@") +
                    storageConfig.getRedisConfig().getHost() + ":" +
                    storageConfig.getRedisConfig().getPort());
            cache = new RedisPunishmentCache(redisClient, new PunishmentSerializer());
        } else {
            redisClient = null;
            cache = new LocalPunishmentCache();
        }

        PunishmentService service = new PunishmentService(cache, repository);
        punishmentManager = new PunishmentManager(service);
        punishmentFactory = new PunishmentFactoryCore();
        SanctionManagerAPI.register(this);
    }

    public SanctionManagerCore(@NotNull PunishmentCache cache, @NotNull PunishmentRepository repository) {
        ApiProvider.injectApi(false);
        this.cache = cache;
        this.repository = repository;
        redisClient = null;
        PunishmentService service = new PunishmentService(cache, repository);
        punishmentManager = new PunishmentManager(service);
        punishmentFactory = new PunishmentFactoryCore();
        SanctionManagerAPI.register(this);
    }

    @Override
    public @NotNull PunishmentManagerAPI getPunishManager() {
        return punishmentManager;
    }

    @Override
    public @NotNull PunishmentFactory getPunishmentFactory() {
        return punishmentFactory;
    }

    public @NotNull PunishmentRepository getRepository() {
        return repository;
    }

    public @NotNull PunishmentCache getCache() {
        return cache;
    }

    public boolean isRedisEnabled() {
        return redisClient != null;
    }

    public boolean isDatabaseEnabled() {
        return repository instanceof HikariPunishmentRepository;
    }

    public void shutdown() {
        if (repository instanceof AutoCloseable) {
            try {
                ((AutoCloseable) repository).close();
            } catch (Exception ignored) {
            }
        }
        if (redisClient != null) {
            redisClient.close();
        }
    }
}