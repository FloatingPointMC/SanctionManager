# Storage Backends

SanctionManager supports multiple persistence backends for storing entry data. Choose the backend that best fits your deployment scenario.

## Available Backends

### SQLite (Recommended for Single-Server)

SQLite provides file-based persistence suitable for single-server deployments and development environments.

**JDBC URL Format:**
```
jdbc:sqlite:/path/to/database.db
```

**Configuration Example:**
```java
StorageConfig config = new StorageConfig(
    true,  // using database
    null,  // no binary file config needed
    new DatabaseConfig(
        "jdbc:sqlite:/data/sanctions.db",
        CredentialProvider.anonymous(),
        10  // connection pool size
    )
);
```

**Advantages:**
- No external database server required
- File-based, portable storage
- Automatic initialization
- Good performance for single-server setups
- WAL (Write-Ahead Logging) mode for improved concurrency

**Limitations:**
- Not suitable for distributed systems
- Limited concurrent write performance compared to MySQL
- Single-process locking model

**Configuration via Config File:**

In your configuration YAML (if using file-based config):
```yaml
storage:
  database: true
  jdbc_url: "jdbc:sqlite:/data/sanctions.db"
  pool_size: 10
```

### MySQL / MariaDB (Recommended for Distributed)

MySQL provides network-accessible persistence suitable for distributed deployments.

**JDBC URL Format:**
```
jdbc:mysql://host:port/database
```

**Configuration Example:**
```java
StorageConfig config = new StorageConfig(
    true,  // using database
    null,
    new DatabaseConfig(
        "jdbc:mysql://localhost:3306/sanctions",
        new CredentialProvider() {
            public AbstractMap.SimpleEntry<String, String> getCredentials() {
                return new AbstractMap.SimpleEntry<>("user", "password");
            }
        },
        20  // connection pool size
    )
);
```

**Advantages:**
- Network-accessible from multiple servers
- Excellent performance at scale
- Strong transaction support
- Mature ecosystem and tooling

**Schema Initialization:**

The MySQL backend automatically creates the required schema on initialization:
- `sm_parent` table for global entry metadata
- `sm_data_[type]` tables for type-specific entry data (one table per entry type)
- Indexes and foreign key constraints

### Binary File Storage (Legacy)

Binary file storage uses a compact binary format for persistence. This backend is still supported but SQLite is recommended for new deployments.

**Configuration Example:**
```java
StorageConfig config = new StorageConfig(
    false,  // not using database
    new BinaryFileConfig(4096, "data/sanctions.dat"),
    null
);
```

## Schema Overview

All database backends use the same logical schema:

### sm_parent table
- `id` (BIGINT PRIMARY KEY AUTOINCREMENT): Global entry identifier
- `target` (BLOB): Target UUID (16 bytes)
- `type` (VARCHAR/TEXT): Entry type identifier

### sm_data_[type] table (one per entry type)
- `rel_id` (BIGINT PRIMARY KEY AUTOINCREMENT): Type-relative entry identifier
- `parent_id` (BIGINT UNIQUE): Foreign key to sm_parent.id
- `executor` (BLOB): Executor UUID (16 bytes)
- `expiry_time` (DATETIME/INTEGER): Expiry timestamp (nullable)
- `execution_time` (DATETIME/INTEGER): Execution timestamp
- `status` (TINYINT/INTEGER): Entry status flags (withdrawn, overridden)
- `reason` (TEXT): Entry reason (nullable)
- `extra_data` (JSON/TEXT): Additional data for types with extra data support (nullable)

### sm_metadata table (Database backends only)
- `type_id` (TEXT PRIMARY KEY): Entry type identifier
- `next_rel_id` (INTEGER): Next type-relative ID to assign

## Backend Selection at Runtime

The backend is automatically selected based on the JDBC URL prefix:

| JDBC URL Prefix | Backend      |
|-----------------|--------------|
| `jdbc:sqlite`   | SQLite       |
| `jdbc:mysql`    | MySQL        |
| `jdbc:mariadb`  | MariaDB      |
| `jdbc:postgresql`| PostgreSQL* |

*PostgreSQL support is planned but not yet implemented.

## UUID and Timestamp Serialization

- **UUIDs**: Stored as 16-byte BLOB values (most significant bits, then least significant bits)
- **Timestamps**: Stored as millisecond precision for database backends

## Concurrent Access Considerations

### SQLite
- Single-process locking model
- WAL mode enables concurrent reads during writes
- Busy timeout configured to 5 seconds
- Suitable for 1-3 concurrent processes

### MySQL/MariaDB
- Row-level locking
- Supports high concurrency
- Configure `innodb_lock_wait_timeout` and connection pool size for your workload

## Migration Between Backends

Currently, there is no built-in migration tool between backends. To migrate:

1. Export all entries from the source backend (query all)
2. Initialize the target backend
3. Re-insert entries using the storage service API
4. Verify entry counts and sample records match

For binary file to SQLite migration, a migration utility is planned.

## Troubleshooting

### SQLite File Permissions
Ensure the directory containing the SQLite database file is writable by the application process.

### MySQL Connection Refused
- Verify MySQL server is running
- Check JDBC URL hostname and port
- Verify credentials and database existence
- Check firewall rules

### Lock Wait Timeout (MySQL)
Increase the connection pool size or configure `innodb_lock_wait_timeout` if you see frequent lock wait errors.

### Database Corruption (SQLite)
If the SQLite file becomes corrupted:
1. Stop the application
2. Back up the corrupted file
3. Delete the corrupted file
4. Restart the application to initialize a fresh database

Note: This will lose all persisted entries. Ensure you have backups.
