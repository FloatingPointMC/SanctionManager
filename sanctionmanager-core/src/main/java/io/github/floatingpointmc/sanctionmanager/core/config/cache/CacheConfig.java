package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for runtime caching of entries and query results.
 * <p>
 * Caching can be disabled entirely, or use in-memory or Redis-backed storage.
 * When caching is disabled, all configuration related to Redis is ignored.
 * Instances are created through the static factory methods {@link #disabled()}, {@link #memory(MemoryCacheConfig)}
 * and {@link #redis(RedisConfig)}.
 * </p>
 * <p>
 * The in-memory cache uses bounded LRU eviction for both individual entries and query results.
 * The Redis cache is unbounded; memory is limited by the Redis server's eviction policy.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class CacheConfig {
    /**
     * Whether caching is enabled.
     */
    private final boolean enabled;
    /**
     * Whether to use Redis for caching (only meaningful if enabled is true).
     */
    private final boolean usingRedis;
    /**
     * Redis configuration, non-null if this configuration was created by {@link #redis(RedisConfig)}.
     */
    private final @Nullable RedisConfig redisConfig;
    /**
     * In-memory cache configuration, non-null if this configuration was created by {@link #memory(MemoryCacheConfig)}.
     */
    private final @Nullable MemoryCacheConfig memoryCacheConfig;

    /**
     * Creates a configuration with caching disabled.
     *
     * @return a configuration with {@code enabled} and {@code usingRedis} set to {@code false}
     */
    public static CacheConfig disabled() {
        return new CacheConfig(false, false, null, null);
    }

    /**
     * Creates a configuration that enables the in-memory cache.
     * <p>
     * The argument is not validated here; its capacities are validated when the cache service is created.
     * </p>
     *
     * @param memoryCacheConfig the in-memory cache configuration. Should not be null.
     * @return a configuration with {@code enabled} set to {@code true} and {@code usingRedis} set to {@code false}
     */
    public static CacheConfig memory(@NotNull MemoryCacheConfig memoryCacheConfig) {
        return new CacheConfig(true, false, null, memoryCacheConfig);
    }

    /**
     * Creates a configuration that enables the Redis cache.
     * <p>
     * The argument is not validated here; it is validated when the cache service is created.
     * </p>
     *
     * @param redisConfig the Redis configuration. Should not be null.
     * @return a configuration with {@code enabled} and {@code usingRedis} set to {@code true}
     */
    public static CacheConfig redis(@NotNull RedisConfig redisConfig) {
        return new CacheConfig(true, true, redisConfig, null);
    }
}
