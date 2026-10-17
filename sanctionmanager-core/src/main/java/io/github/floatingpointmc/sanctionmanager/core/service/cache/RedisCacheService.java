package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.RedisConfig;
import org.jetbrains.annotations.NotNull;

public class RedisCacheService implements ICacheService {
    private final @NotNull String host;
    private final CredentialProvider credentialProvider;

    public RedisCacheService(@NotNull RedisConfig redisConfig) {
        host = redisConfig.getHost();
        credentialProvider = redisConfig.getCredentialProvider();
    }
}
