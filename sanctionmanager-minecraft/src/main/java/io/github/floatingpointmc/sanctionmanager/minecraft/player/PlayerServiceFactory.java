package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.DatabasePlayerRepository;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.FilePlayerRepository;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.MemoryPlayerCache;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.RedisPlayerCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;

/**
 * Factory for creating PlayerService instances with correct Repository + Cache architecture.
 * <p>
 * Architecture:
 * <pre>
 * PlayerService
 *     ├── PlayerRepository (Persistence)
 *     │       ├── DatabasePlayerRepository (SQL - persistent)
 *     │       └── FilePlayerRepository (Binary - persistent)
 *     │
 *     └── PlayerCache (Cache only - NOT persistence)
 *             ├── MemoryPlayerCache (RAM)
 *             └── RedisPlayerCache (Redis)
 * </pre>
 * <p>
 * Priority for Repository (Persistence):
 * 1. Database (if HikariDataSource available)
 * 2. File (fallback)
 * <p>
 * Priority for Cache:
 * 1. Redis (if JedisPool available)
 * 2. Memory (fallback - always available)
 */
public class PlayerServiceFactory {

    /**
     * Create PlayerService with appropriate Repository and Cache.
     *
     * @param dataSource Optional HikariDataSource for database persistence
     * @param jedisPool  Optional JedisPool for Redis caching
     * @param dataDir    Data directory for file-based persistence fallback
     * @return PlayerService instance
     */
    public static PlayerService create(
            @Nullable HikariDataSource dataSource,
            @Nullable JedisPool jedisPool,
            @NotNull String dataDir
    ) {
        // Choose Repository (Persistence Layer)
        PlayerRepository repository = createRepository(dataSource, dataDir);

        // Choose Cache
        PlayerCache cache = createCache(jedisPool);

        return new PlayerService(repository, cache);
    }

    private static PlayerRepository createRepository(
            @Nullable HikariDataSource dataSource,
            @NotNull String dataDir
    ) {
        // Priority 1: Database (if available)
        if (dataSource != null) {
            return new DatabasePlayerRepository(dataSource);
        }

        // Priority 2: File (fallback - always works)
        return new FilePlayerRepository(dataDir);
    }

    private static PlayerCache createCache(@Nullable JedisPool jedisPool) {
        // Priority 1: Redis (if available)
        if (jedisPool != null) {
            return new RedisPlayerCache(jedisPool);
        }

        // Priority 2: Memory (fallback - always works)
        return new MemoryPlayerCache();
    }
}
