package io.github.floatingpointmc.sanctionmanager.core.storage.sql;

import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

public final class SanctionManagerDataSource implements DataSource {
    private final String jdbcUrl;
    private final CredentialProvider credentialProvider;

    public SanctionManagerDataSource(
            String jdbcUrl,
            CredentialProvider credentialProvider
    ) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.credentialProvider = Objects.requireNonNull(
                credentialProvider, "credentialProvider"
        );
    }

    @Override
    public Connection getConnection() throws SQLException {
        ConnectionHolder holder = new ConnectionHolder();

        credentialProvider.useCredential(credential -> {
            Objects.requireNonNull(credential, "credential");

            credential.usePassword(password -> {
                String passwordString = new String(password);

                try {
                    holder.connection = DriverManager.getConnection(
                            jdbcUrl,
                            credential.getUsername(),
                            passwordString
                    );
                } catch (SQLException e) {
                    holder.failure = e;
                }
            });
        });

        if (holder.failure != null) {
            throw holder.failure;
        }

        if (holder.connection == null) {
            throw new SQLException(
                    "CredentialProvider did not provide a connection"
            );
        }

        return holder.connection;
    }

    @Override
    public Connection getConnection(String username, String password)
            throws SQLException {
        // 显式传入的凭据不经过 CredentialProvider。
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        DriverManager.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException(
                "Parent logger is not supported"
        );
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface != null && iface.isInstance(this);
    }

    private static final class ConnectionHolder {
        private Connection connection;
        private SQLException failure;
    }
}