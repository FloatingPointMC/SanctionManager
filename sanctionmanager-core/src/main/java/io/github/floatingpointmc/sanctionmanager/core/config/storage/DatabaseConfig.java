package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for database-backed storage.
 * <p>
 * Specifies the JDBC connection string and credentials for connecting to the persistence database.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public final class DatabaseConfig {
    /** JDBC URL for the database connection. */
    private final @NotNull String jdbcUrl;
    /** Provider for database credentials. */
    private final @NotNull CredentialProvider credentialProvider;
}