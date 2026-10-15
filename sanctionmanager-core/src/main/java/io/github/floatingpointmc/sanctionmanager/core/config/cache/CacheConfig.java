package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public final class CacheConfig {
    private final boolean enabled;
    private final boolean useRedis;
    private final @Nullable RedisConfig redisConfig;

    public CacheConfig(boolean enabled, boolean useRedis, @Nullable RedisConfig redisConfig) {
        this.enabled = enabled;
        if (!enabled) {
            this.useRedis = false;
            this.redisConfig = null;
            return;
        }
        if (useRedis && redisConfig == null) {
            throw new NullPointerException("redisConfig cannot be null while using redis cache");
        }
        this.useRedis = useRedis;
        this.redisConfig = redisConfig;
    }
}
