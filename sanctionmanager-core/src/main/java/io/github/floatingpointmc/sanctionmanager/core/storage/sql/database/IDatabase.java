package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import java.sql.SQLException;

public interface IDatabase {
    void initialize() throws SQLException;
}
