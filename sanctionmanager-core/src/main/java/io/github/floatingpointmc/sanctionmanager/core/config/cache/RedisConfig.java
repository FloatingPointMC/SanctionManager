package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for Redis-backed caching.
 * <p>
 * Specifies the Redis server connection details and authentication credentials. The credential is requested
 * on every connection handshake; an empty username authenticates with the password only, and an empty password
 * disables authentication.
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
