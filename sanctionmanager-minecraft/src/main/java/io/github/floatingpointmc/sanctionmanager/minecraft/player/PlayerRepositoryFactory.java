package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.core.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.*;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisPool;

/**
 * Factory for creating PlayerRepository instances based on storage configuration.
 * <p>
 * The Minecraft layer has its own storage configuration separate from Core.
 * This factory determines which backend to use for player data persistence.
 */
public class PlayerRepositoryFactory {

    /**
     * Create PlayerRepository based on storage configuration.
     * <p>
     * Priority:
     * 1. Database (if enabled and dataSource provided)
     * 2. Redis (if enabled and jedisPool provided)
     * 3. File (if binaryDataDir provided)
     * 4. Memory (fallback)
     *
     * @param config Storage configuration
     * @param dataSource Optional HikariCP data source (for database mode)
     * @param jedisPool Optional Jedis pool (for Redis mode)
     * @return PlayerRepository instance
     */
    public static @NotNull PlayerRepository create(
            @NotNull StorageConfig config,
            HikariDataSource dataSource,
            JedisPool jedisPool) {

        // Priority 1: Database
        if (config.isDatabaseEnabled() && dataSource != null) {
            return new DatabasePlayerRepository(dataSource);
        }

        // Priority 2: Redis
        if (config.isRedisEnabled() && jedisPool != null) {
            return new RedisPlayerRepository(jedisPool);
        }

        // Priority 3: File
        if (config.getBinaryDataDir() != null) {
            return new FilePlayerRepository(config.getBinaryDataDir());
        }

        // Priority 4: Memory (fallback)
        return new MemoryPlayerRepository();
    }

    /**
     * Create memory-only repository (for testing or lightweight setups).
     */
    public static @NotNull PlayerRepository createMemory() {
        return new MemoryPlayerRepository();
    }

    /**
     * Create file-based repository.
     */
    public static @NotNull PlayerRepository createFile(@NotNull String dataDir) {
        return new FilePlayerRepository(dataDir);
    }

    /**
     * Create database-based repository.
     */
    public static @NotNull PlayerRepository createDatabase(@NotNull HikariDataSource dataSource) {
        return new DatabasePlayerRepository(dataSource);
    }

    /**
     * Create Redis-based repository.
     */
    public static @NotNull PlayerRepository createRedis(@NotNull JedisPool jedisPool) {
        return new RedisPlayerRepository(jedisPool);
    }
}
