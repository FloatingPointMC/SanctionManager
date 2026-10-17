package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for runtime caching of entries and player data.
 * <p>
 * Caching can be disabled entirely, or use in-memory or Redis-backed storage.
 * When caching is disabled, all configuration related to Redis is ignored.
 * </p>
 * <p>
 * The in-memory cache uses bounded LRU eviction for both individual entries and query results.
 * </p>
 */
@Getter
public final class CacheConfig {
    /** Default maximum entry cache capacity. */
    public static final int DEFAULT_ENTRY_CAPACITY = 10000;
    /** Default maximum query result cache capacity. */
    public static final int DEFAULT_QUERY_CAPACITY = 1000;

    /** Whether caching is enabled. */
    private final boolean enabled;
    /** Whether to use Redis for caching (only meaningful if enabled is true). */
    private final boolean usingRedis;
    /** Redis configuration, non-null if {@code enabled} and {@code useRedis} are both true. */
    private final @Nullable RedisConfig redisConfig;
    /** Maximum number of entries in the entry cache (applies to in-memory cache only). */
    private final int entryCapacity;
    /** Maximum number of query results in the query result cache (applies to in-memory cache only). */
    private final int queryCapacity;

    /**
     * Constructs a CacheConfig with validation and default capacities.
     * <p>
     * If {@code enabled} is {@code false}, Redis configuration is ignored and {@code useRedis} is forced to false.
     * If {@code enabled} is {@code true} and {@code useRedis} is {@code true}, {@code redisConfig} must be non-null.
     * </p>
     *
     * @param enabled     whether caching is enabled
     * @param usingRedis    whether to use Redis for caching
     * @param redisConfig Redis configuration (required if enabled and useRedis are true)
     * @throws NullPointerException if Redis is enabled but redisConfig is null
     */
    public CacheConfig(boolean enabled, boolean usingRedis, @Nullable RedisConfig redisConfig) {
        this(enabled, usingRedis, redisConfig, DEFAULT_ENTRY_CAPACITY, DEFAULT_QUERY_CAPACITY);
    }

    /**
     * Constructs a CacheConfig with custom capacity settings.
     *
     * @param enabled        whether caching is enabled
     * @param usingRedis     whether to use Redis for caching
     * @param redisConfig    Redis configuration (required if enabled and useRedis are true)
     * @param entryCapacity  maximum entry cache size (must be positive)
     * @param queryCapacity  maximum query result cache size (must be positive)
     * @throws NullPointerException if Redis is enabled but redisConfig is null
     * @throws IllegalArgumentException if capacity values are invalid
     */
    public CacheConfig(boolean enabled, boolean usingRedis, @Nullable RedisConfig redisConfig,
                       int entryCapacity, int queryCapacity) {
        this.enabled = enabled;
        if (!enabled) {
            this.usingRedis = false;
            this.redisConfig = null;
            this.entryCapacity = DEFAULT_ENTRY_CAPACITY;
            this.queryCapacity = DEFAULT_QUERY_CAPACITY;
            return;
        }
        if (usingRedis && redisConfig == null) {
            throw new NullPointerException("redisConfig cannot be null while using redis cache");
        }
        if (entryCapacity <= 0) {
            throw new IllegalArgumentException("entryCapacity must be positive, got: " + entryCapacity);
        }
        if (queryCapacity <= 0) {
            throw new IllegalArgumentException("queryCapacity must be positive, got: " + queryCapacity);
        }
        this.usingRedis = usingRedis;
        this.redisConfig = redisConfig;
        this.entryCapacity = entryCapacity;
        this.queryCapacity = queryCapacity;
    }
}
