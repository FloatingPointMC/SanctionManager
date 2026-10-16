package io.github.floatingpointmc.sanctionmanager.core.storage;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Database connection pool for SanctionManager persistence.
 * <p>
 * Extends HikariCP's {@code HikariDataSource} for efficient pooled connection management
 * to the persistence database.
 * </p>
 */
public class SanctionManagerDataSource extends HikariDataSource {

}
