package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for runtime caching of entries and player data.
 * <p>
 * Caching can be disabled entirely, or use in-memory or Redis-backed storage.
 * When caching is disabled, all configuration related to Redis is ignored.
 * </p>
 */
@Getter
public final class CacheConfig {
    /** Whether caching is enabled. */
    private final boolean enabled;
    /** Whether to use Redis for caching (only meaningful if enabled is true). */
    private final boolean usingRedis;
    /** Redis configuration, non-null if {@code enabled} and {@code useRedis} are both true. */
    private final @Nullable RedisConfig redisConfig;

    /**
     * Constructs a CacheConfig with validation.
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
        this.enabled = enabled;
        if (!enabled) {
            this.usingRedis = false;
            this.redisConfig = null;
            return;
        }
        if (usingRedis && redisConfig == null) {
            throw new NullPointerException("redisConfig cannot be null while using redis cache");
        }
        this.usingRedis = usingRedis;
        this.redisConfig = redisConfig;
    }
}
