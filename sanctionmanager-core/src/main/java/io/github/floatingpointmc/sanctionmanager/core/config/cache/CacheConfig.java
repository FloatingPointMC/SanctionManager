package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class CacheConfig {
    /** Whether caching is enabled. */
    private final boolean enabled;
    /** Whether to use Redis for caching (only meaningful if enabled is true). */
    private final boolean usingRedis;
    /** Redis configuration, non-null if {@code enabled} and {@code useRedis} are both true. */
    private final @Nullable RedisConfig redisConfig;
    private final @Nullable MemoryCacheConfig memoryCacheConfig;

    public static CacheConfig disabled() {
        return new CacheConfig(false, false, null, null);
    }

    public static CacheConfig memory(@NotNull MemoryCacheConfig memoryCacheConfig) {
        return new CacheConfig(true, false, null, memoryCacheConfig);
    }

    public static CacheConfig redis(@NotNull RedisConfig redisConfig) {
        return new CacheConfig(false, true, redisConfig, null);
    }
}
