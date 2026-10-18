package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for Redis-backed caching.
 * <p>
 * Specifies the Redis server connection details and authentication credentials.
 * </p>
 * <p>
 * Note: Redis caching is not implemented. The values are stored but never used to open a connection.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public final class RedisConfig {
    /** Redis server hostname or IP address. */
    private final @NotNull String host;
    /** Redis server port. */
    private final int port;
    /** Provider for Redis authentication credentials. */
    private final @NotNull CredentialProvider credentialProvider;
}
