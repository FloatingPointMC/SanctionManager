package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
@Getter
public final class RedisConfig {
    private final @NotNull String host;
    private final int port;
    private final @NotNull CredentialProvider credentialProvider;
}
