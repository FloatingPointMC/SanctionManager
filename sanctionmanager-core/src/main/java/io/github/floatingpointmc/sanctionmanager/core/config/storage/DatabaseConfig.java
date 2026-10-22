package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for database-backed storage.
 * <p>
 * Specifies the JDBC connection string and credentials for connecting to the persistence database.
 * </p>
 * <p>
 * Note: database storage is not implemented. Selecting it causes Core initialization to fail with
 * {@code UnsupportedOperationException}.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class DatabaseConfig {
    /**
     * JDBC URL for the database connection.
     */
    private final @NotNull String jdbcUrl;
    private final int maximumPoolSize;
    /**
     * Provider for database credentials.
     */
    private final @NotNull CredentialProvider credentialProvider;

    public static @NotNull DatabaseConfig of(@NotNull String jdbcUrl, int maximumPoolSize, @NotNull CredentialProvider provider) {
        return new DatabaseConfig(jdbcUrl, maximumPoolSize, provider);
    }
}