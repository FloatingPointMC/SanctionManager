package io.github.floatingpointmc.sanctionmanager.core.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;

import java.util.Objects;

public final class HikariDataSourceFactory {

    private HikariDataSourceFactory() {
    }

    public static HikariDataSource create(
            DatabaseConfig database,
            CredentialProvider credentials
    ) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(credentials, "credentials");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(database.getJdbcUrl());
        config.setMaximumPoolSize(database.getMaximumPoolSize());
        config.setPoolName("SanctionManager-HikariCP");

        credentials.useCredential(credential -> {
            Objects.requireNonNull(credential, "credential");

            config.setUsername(credential.getUsername());

            // todo: IMPORTANT, shorten the password lifecycle
            credential.usePassword(password ->
                    config.setPassword(new String(password))
            );
        });

        return new HikariDataSource(config);
    }
}