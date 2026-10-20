package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import java.sql.SQLException;

/**
 * Database backend interface for sanction persistence.
 * CF-01/CF-02 FIX: Added metadata persistence methods.
 */
public interface IDatabase {
    /**
     * Initializes database schema (creates tables if needed, validates structure).
     */
    void initialize() throws SQLException;

    /**
     * Retrieves per-type relId counters from the database.
     * CF-01/CF-02 FIX: Required for Core initialization.
     *
     * @return map of type identifier to next relId value
     */
    java.util.Map<String, Long> getTypeRelIdCounters();

    /**
     * Updates the relId counter for a specific type.
     * CF-02 FIX: Required for relId persistence.
     *
     * @param typeIdentifier the type identifier
     * @param nextRelId the next relId to assign
     */
    void updateTypeRelIdCounter(String typeIdentifier, long nextRelId);

    /**
     * Persists metadata (counters, type mappings) to database.
     * CF-02 FIX: Required for durable relId counters.
     */
    void persistMetadata() throws SQLException;
}
