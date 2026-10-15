package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
@Getter
public final class DatabaseConfig {
    private final @NotNull String jdbcUrl;
    private final @NotNull CredentialProvider credentialProvider;
}