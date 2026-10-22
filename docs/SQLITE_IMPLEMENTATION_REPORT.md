# SQLite Backend Implementation Report

## Executive Summary

SQLite has been successfully implemented as an additional persistent storage backend for SanctionManager, alongside the existing MySQL and binary file storage options. The implementation:

- Maintains full backward compatibility with MySQL and binary file storage
- Provides single-file, zero-configuration persistence for single-server deployments
- Implements comprehensive transaction support with automatic rollback on failure
- Includes metadata persistence for type-relative ID counters
- Passes all data model tests (UUID/timestamp serialization, nullable values, etc.)

## Architecture

### Storage Layer Design

The storage layer follows a multi-tier architecture:

1. **IStorageService Interface** (sanctionmanager-core/service/storage/)
   - Abstraction for all storage implementations
   - Implementations: BinaryFileStorageService, DatabaseStorageService
   
2. **DatabaseStorageService** (implements IStorageService)
   - Bridges IStorageService to IDatabase implementations
   - Delegates all storage operations to concrete database classes
   - Handles HikariCP connection pooling and lifecycle
   - Provides unified error handling (SQLException → IOException)

3. **IDatabase Interface** (sanctionmanager-core/storage/sql/database/)
   - Contract for database-specific implementations
   - Implementations: MySQL, PostgreSQL (stub), SQLite

4. **SQLite Implementation** (NEW)
   - SQLite-specific schema management
   - UUID and timestamp serialization
   - Transaction management with rollback
   - Metadata persistence

### Module Structure

```
sanctionmanager-core/
├── src/main/java/io/github/floatingpointmc/sanctionmanager/core/
│   ├── service/storage/
│   │   ├── IStorageService.java (interface)
│   │   ├── BinaryFileStorageService.java (existing)
│   │   ├── DatabaseStorageService.java (updated)
│   │   └── Addition.java
│   └── storage/sql/
│       ├── DatabaseType.java (updated)
│       └── database/
│           ├── IDatabase.java
│           ├── MySQL.java (enhanced)
│           ├── SQLite.java (NEW)
│           └── PostgreSQL.java (stub)
└── src/test/java/
    └── storage/sql/
        └── SQLiteStorageServiceTest.java (NEW)
```

## Implementation Details

### SQLite Database Implementation

**File:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/SQLite.java`

#### Schema Design

SQLite uses the same logical schema as MySQL for consistency:

```sql
-- Metadata persistence
CREATE TABLE IF NOT EXISTS sm_metadata (
    type_id TEXT PRIMARY KEY,
    next_rel_id INTEGER NOT NULL
);

-- Global entries
CREATE TABLE IF NOT EXISTS sm_parent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target BLOB NOT NULL,
    type TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_parent_target ON sm_parent(target);

-- Type-specific entries (one table per type)
CREATE TABLE IF NOT EXISTS sm_data_[type] (
    rel_id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER NOT NULL UNIQUE,
    executor BLOB NOT NULL,
    expiry_time INTEGER,
    execution_time INTEGER NOT NULL,
    status INTEGER NOT NULL,
    reason TEXT,
    [extra_data TEXT]  -- Only for types with extra data support
    FOREIGN KEY (parent_id) REFERENCES sm_parent(id) ON DELETE CASCADE
);
```

#### Key Features

1. **SQLite Configuration**
   - `PRAGMA foreign_keys = ON`: Enable foreign key enforcement
   - `PRAGMA journal_mode = WAL`: Write-Ahead Logging for better concurrency
   - `PRAGMA busy_timeout = 5000`: 5-second timeout for lock contention

2. **UUID Serialization**
   - 16-byte BLOB format (8 bytes MSB + 8 bytes LSB)
   - Compatible with MySQL BINARY(16) representation
   - Reversible: `UUID ↔ byte[16] ↔ BLOB`

3. **Timestamp Serialization**
   - Millisecond precision (BIGINT in SQLite, stored as INTEGER)
   - Compatible with MySQL DATETIME granularity
   - Supports nullable values for optional expiry times

4. **Transaction Management**
   - All write operations wrapped in transactions
   - Automatic rollback on failure
   - Proper resource cleanup in finally blocks

5. **Metadata Persistence**
   - Type-relative ID counters persisted to sm_metadata table
   - Loaded on initialization
   - Updated via persistMetadata() method
   - Survives database restart

### DatabaseStorageService Enhancements

**File:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/storage/DatabaseStorageService.java`

Previously, most IStorageService methods threw `UnsupportedOperationException`. All methods now implemented:

- `insertRecord()`: Insert entry via IDatabase.insertEntry()
- `getRecord()`: Retrieve entry by global ID
- `queryByTarget()`: Query entries by target UUID
- `queryByTypeAndRelId()`: Query entries by type and type-relative ID
- `queryAll()`: Retrieve all entries
- `updateRecord()`: Update entry status/withdrawal state
- `deleteRecord()`: Delete entry by global ID

All methods:
- Delegate to concrete IDatabase implementations (SQLite or MySQL)
- Convert SQLException to IOException for API compatibility
- Check for null database and throw IllegalStateException if not initialized

### MySQL Enhancements

**File:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/MySQL.java`

Added missing data access methods to match SQLite implementation:

- `insertEntry()`: Insert entry and return generated IDs
- `getEntryById()`: Retrieve entry by global ID
- `queryByTarget()`: Query entries by target UUID
- `queryByTypeAndRelId()`: Query entries by type and rel ID
- `queryAll()`: Retrieve all entries
- `updateEntry()`: Update entry status
- `deleteEntry()`: Delete entry by ID
- `buildEntry()`: Helper to construct EntryPersisted from ResultSet
- `resultSetToEntry()`: Convert database row to EntryPersisted object

All methods use prepared statements and proper resource management.

### DatabaseType Enum Update

**File:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/DatabaseType.java`

Added SQLite support:

```java
public enum DatabaseType {
    MYSQL("MySQL", MySQL.class),
    MARIADB("MariaDB", MySQL.class),
    POSTGRESQL("PostgreSQL", PostgreSQL.class),
    SQLITE("SQLite", SQLite.class);
    
    public static DatabaseType parse(String jdbcUrl) {
        // Updated to handle "sqlite" prefix in JDBC URLs
        // jdbc:sqlite:... → DatabaseType.SQLITE
    }
}
```

### Build Configuration Update

**File:** `sanctionmanager-core/build.gradle.kts`

SQLite JDBC driver moved from testRuntimeOnly to runtimeOnly:

```gradle
runtimeOnly("org.xerial:sqlite-jdbc:3.46.1.0")
```

This enables SQLite as a production backend while maintaining test compatibility.

## Data Model Preservation

The implementation correctly handles all data model semantics:

1. **Global Entry IDs**: Unique across all entries (sm_parent.id)
2. **Type-Relative IDs**: Unique per type (sm_data_[type].rel_id)
3. **UUID Serialization**: Preserved losslessly as BLOB
4. **Timestamps**: Millisecond precision maintained
5. **Nullable Values**: Properly distinguished from empty strings
6. **Extra Data**: JSON serialization via Gson
7. **Entry Status**: Bit flags (withdrawn=1, overridden=2)

## Configuration

### JDBC URL Format

```
jdbc:sqlite:[path]
```

Examples:
- `jdbc:sqlite:/data/sanctions.db` - Absolute path
- `jdbc:sqlite:./sanctions.db` - Relative path
- `jdbc:sqlite:/tmp/sanctions.db` - Temporary location

### Configuration in Code

```java
DatabaseConfig config = new DatabaseConfig(
    "jdbc:sqlite:/data/sanctions.db",
    CredentialProvider.anonymous(),  // SQLite doesn't use credentials
    10  // Connection pool size (typically 5-10 for SQLite)
);

StorageConfig storageConfig = new StorageConfig(
    true,    // using database
    null,    // no binary file config
    config
);
```

### Automatic Backend Selection

The backend is selected via JDBC URL prefix:
- `jdbc:sqlite:...` → SQLite
- `jdbc:mysql:...` → MySQL
- `jdbc:mariadb:...` → MariaDB
- `jdbc:postgresql:...` → PostgreSQL

## Testing

### Test Coverage

**File:** `sanctionmanager-core/src/test/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/SQLiteStorageServiceTest.java`

Comprehensive integration tests covering:

1. **Basic CRUD Operations**
   - Insert and retrieve single entries
   - Query by target UUID
   - Query by type and rel ID
   - Delete entries
   - Query all entries

2. **Data Integrity**
   - Entry fields correctly persisted
   - UUID serialization round-trip
   - Timestamp preservation

3. **Extra Data**
   - JSON serialization for types with extra data
   - Null handling for types without extra data

4. **Metadata Persistence**
   - Type-relative ID counters persisted
   - Metadata survives database close/reopen

### Test Execution

Tests use isolated temporary databases:
- Each test creates fresh database in temp directory
- Automatic cleanup after test completion
- No dependencies on external services
- No fixtures or test data in repository

## Backward Compatibility

### Binary File Storage
- Fully preserved and functional
- No changes to BinaryFileStorageService
- Existing binary file databases can continue to be used

### MySQL Storage
- All existing MySQL functionality preserved
- Enhanced with new data access methods
- Schema unchanged

### API Contracts
- IStorageService interface unchanged (only implementations updated)
- StorageConfig API unchanged
- DatabaseConfig API unchanged

## Known Limitations and Design Decisions

### Limitations

1. **Single-Server Only**: SQLite uses file-level locking; not suitable for distributed deployments
2. **Concurrent Writes**: Write operations serialize at the database level; suitable for 1-3 concurrent processes
3. **Withdrawn User Tracking**: Current implementation stores executor as withdrawn user; full history not tracked (design limitation in schema)

### Design Decisions

1. **Millisecond vs Second Precision**: Timestamps use millisecond precision for compatibility with MySQL DATETIME granularity

2. **UUID as BLOB**: 16-byte BLOB provides efficient storage and enables binary comparisons; compatible with MySQL BINARY(16)

3. **Status Bit Flags**: Withdrawn (1) and overridden (2) states packed into single integer for compactness; extensible for future status types

4. **Metadata Persistence**: Separate sm_metadata table avoids dependency on type-specific tables and enables global counter management

5. **No Migration Tool**: Migration between backends deferred; users can export/re-import via the storage API

## Documentation

### User Documentation

**File:** `docs/STORAGE_BACKENDS.md`

Comprehensive guide covering:
- Backend selection criteria
- Configuration examples for each backend
- Schema overview
- UUID/timestamp serialization details
- Concurrent access considerations
- Troubleshooting guide

### README Update

Updated `README.md` to reference storage backends documentation.

## Verification and Quality Assurance

### Compilation
- All files compile without errors or warnings
- Imports properly resolved
- Annotations processed correctly

### Code Quality
- Follows existing code style and conventions
- Proper resource management (try-with-resources)
- Comprehensive null checking
- Clear error messages
- Detailed JavaDoc comments

### Architecture
- Module boundaries preserved
- Dependency direction maintained (Core independent of implementations)
- Clean separation of concerns
- No hardcoding of database-specific logic in Core

## Future Enhancements

1. **PostgreSQL Implementation**: Complete PostgreSQL support (currently stub)
2. **Migration Utility**: Tool to migrate between backends
3. **Connection Pooling Optimization**: Per-database pool configuration
4. **Query Optimization**: Indexes and query hints for large datasets
5. **Full Historical Tracking**: Separate table for withdrawal/override history
6. **Distributed Deployment**: Redis-backed cache coordination layer

## Conclusion

SQLite support has been successfully integrated into SanctionManager as a production-ready backend for single-server deployments. The implementation:

- ✅ Preserves all existing functionality (MySQL, binary file)
- ✅ Implements complete data persistence contract
- ✅ Maintains data model semantics
- ✅ Provides comprehensive documentation
- ✅ Includes integration tests
- ✅ Follows architectural patterns
- ✅ Is immediately usable in production for appropriate workloads

The architecture supports easy addition of future backends without breaking existing deployments.
