# Minecraft Player Repository Implementation

## Overview

This document describes the implementation of the Minecraft-layer player data persistence system, which enables offline player resolution for commands like `/ban <player>`.

## Architecture

### Key Principle

> **Minecraft layer maintains its own player data, completely separate from Core's Sanction data.**

```
┌─────────────────────────────────────────────────────────┐
│                    sm-minecraft                         │
├─────────────────────────────────────────────────────────┤
│  PlayerRepository (UUID ↔ Name mapping)                 │
│    ├── MemoryPlayerRepository                           │
│    ├── FilePlayerRepository                             │
│    ├── DatabasePlayerRepository                         │
│    └── RedisPlayerRepository                            │
├─────────────────────────────────────────────────────────┤
│  Commands (/ban, /mute, etc.)                           │
│    └── PlayerResolver (online + offline)                │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼ (UUID only)
┌─────────────────────────────────────────────────────────┐
│                     sm-core                             │
├─────────────────────────────────────────────────────────┤
│  Sanction Data (Punishment, Ban, Mute)                  │
│    - Uses UUID as identity                              │
│    - Does NOT store player names                        │
└─────────────────────────────────────────────────────────┘
```

## Data Separation

### Minecraft Layer Data
- **What**: UUID ↔ Name mapping
- **Purpose**: Resolve player names to UUIDs for commands
- **Storage**: Minecraft's own storage (separate from Core)
- **Namespaces**:
  - Database: `minecraft_players` table
  - File: `data/minecraft/players/`
  - Redis: `sm:minecraft:player:*`

### Core Layer Data
- **What**: Punishment records (Ban, Mute, etc.)
- **Purpose**: Sanction management
- **Identity**: UUID only (no player names)
- **Storage**: Core's storage system
- **Namespaces**:
  - Database: `punishment`, `ban`, `mute` tables
  - File: `data/core/`
  - Redis: `sm:core:*`

## PlayerRepository API

### Interface

```java
public interface PlayerRepository extends AutoCloseable {
    /**
     * Find UUID by player name (case-insensitive).
     * @return UUID if found, null otherwise
     */
    @Nullable UUID findUuidByName(@NotNull String name);

    /**
     * Find player name by UUID.
     * @return Name if found, null otherwise
     */
    @Nullable String findNameByUuid(@NotNull UUID uuid);

    /**
     * Save or update player data.
     * If UUID already exists with different name, old name is removed.
     */
    void save(@NotNull UUID uuid, @NotNull String name);

    /**
     * Close repository and release resources.
     */
    void close();
}
```

## Implementations

### 1. MemoryPlayerRepository
- **Storage**: `ConcurrentHashMap` in memory
- **Lifecycle**: Data lost on server restart
- **Use case**: RAM mode, testing
- **Thread-safe**: Yes

### 2. FilePlayerRepository
- **Storage**: JSON files in `data/minecraft/players/`
- **Format**: One file per player: `{uuid}.json`
- **Lifecycle**: Persists across restarts
- **Use case**: Standalone mode without database

### 3. DatabasePlayerRepository
- **Storage**: SQL database table `minecraft_players`
- **Schema**:
  ```sql
  CREATE TABLE IF NOT EXISTS minecraft_players (
      uuid VARCHAR(36) PRIMARY KEY,
      name VARCHAR(16) NOT NULL,
      normalized_name VARCHAR(16) NOT NULL,
      last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  CREATE INDEX idx_normalized_name ON minecraft_players(normalized_name);
  ```
- **Connection**: Uses HikariCP (must be provided by platform)
- **Use case**: Production deployments with database

### 4. RedisPlayerRepository
- **Storage**: Redis with keys:
  - `sm:minecraft:player:uuid:{uuid}` → name
  - `sm:minecraft:player:name:{normalized_name}` → uuid
- **Cache**: In-memory cache for faster lookups
- **Lifecycle**: Depends on Redis persistence configuration
- **Use case**: Shared data across multiple servers (Proxy mode)

## PlayerRepositoryFactory

Factory class that creates the appropriate repository based on configuration:

```java
public static PlayerRepository create(
    @NotNull String storageType,
    @Nullable HikariDataSource dataSource,
    @Nullable JedisPool jedisPool
)
```

Supported storage types:
- `"memory"` or `"ram"`: MemoryPlayerRepository
- `"file"`: FilePlayerRepository
- `"database"` or `"mysql"`: DatabasePlayerRepository
- `"redis"`: RedisPlayerRepository

## Integration

### MinecraftSanctionManager

Manages PlayerRepository lifecycle:

```java
public class MinecraftSanctionManager {
    private final PlayerRepository playerRepository;

    public MinecraftSanctionManager(...) {
        this.playerRepository = PlayerRepositoryFactory.create(
            storageType,
            dataSource,
            jedisPool
        );
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public void shutdown() {
        playerRepository.close();
    }
}
```

### Player Join Event

When a player joins the server, their data is automatically saved:

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    String name = player.getName();

    // Save to repository
    manager.getPlayerRepository().save(uuid, name);
}
```

This works for:
- Spigot/Paper: `PlayerJoinEvent`
- BungeeCord: `PostLoginEvent`
- Velocity: `PlayerConnectedEvent`

### Command Integration

Commands now resolve offline players:

```java
// Before: Only online players
Player player = Bukkit.getPlayer(name); // null if offline

// After: Online + known offline players
UUID uuid = manager.getPlayerRepository().findUuidByName(name);
if (uuid == null) {
    sender.sendMessage("Player not found");
    return;
}

// Pass UUID to Core
punishmentManager.ban(uuid, ...);
```

## Name Handling

### Case Sensitivity

Player names are case-insensitive for lookup but case-preserving for display:

- **Normalization**: All lookups normalize to lowercase
- **Preservation**: Original casing is preserved for display
- **Example**:
  ```java
  save(uuid, "StEvE");
  findUuidByName("steve")  // ✓ Returns UUID
  findUuidByName("STEVE")  // ✓ Returns UUID
  findNameByUuid(uuid)     // ✓ Returns "StEvE" (original)
  ```

### Name Changes

When a player changes their name:

```java
save(uuid, "OldName");  // Initial save
save(uuid, "NewName");  // Name change

// Old name is automatically removed
findUuidByName("OldName")  // null
findUuidByName("NewName")  // uuid
```

**Note**: This implementation does NOT maintain name history. Only the current name is stored.

## Deployment Modes

### Standalone Mode (Spigot/Paper)

- Uses local PlayerRepository
- Storage: File or Database (recommended)
- Lifecycle: Managed by Spigot plugin

### Proxy Mode (BungeeCord/Velocity)

- Uses shared PlayerRepository
- Storage: Redis or Database (recommended for multi-instance)
- Lifecycle: Managed by Proxy plugin
- **Bridge subservers**: Do NOT maintain their own player data

## Configuration

Player repository storage is controlled by the existing `storage.type` configuration:

```yaml
storage:
  type: database  # Options: memory, file, database, redis
```

**No new configuration required** - reuses existing storage infrastructure.

## Testing

Comprehensive test suite in `PlayerRepositoryTest`:

- ✓ Save and find by name
- ✓ Save and find by UUID
- ✓ Case-insensitive name lookup
- ✓ Name changes
- ✓ Unknown player handling
- ✓ Multiple players
- ✓ Name preservation
- ✓ Resource cleanup

All tests passing.

## Dependencies

Added to `sanctionmanager-minecraft/build.gradle.kts`:

```kotlin
implementation("com.zaxxer:HikariCP:4.0.3")
implementation("redis.clients:jedis:4.3.1")
```

Both are compatible with Java 8.

## Limitations

### Current Implementation

1. **No name history**: Only current name is stored
2. **No Mojang API integration**: No automatic UUID lookup for unknown players
3. **Simple query API**: No complex filtering or pagination
4. **Single-server write concerns**: File/Database modes assume single writer

### Future Enhancements (Not Implemented)

- Name history tracking
- Mojang API fallback for unknown players
- Batch operations
- Player data expiration
- Migration tools between storage types

## Files Created/Modified

### New Files

```
sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/
├── PlayerRepository.java                          (Interface)
├── PlayerRepositoryFactory.java                   (Factory)
└── impl/
    ├── MemoryPlayerRepository.java                (Memory implementation)
    ├── FilePlayerRepository.java                  (File implementation)
    ├── DatabasePlayerRepository.java              (Database implementation)
    └── RedisPlayerRepository.java                 (Redis implementation)

sanctionmanager-minecraft/src/test/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/
└── PlayerRepositoryTest.java                      (Tests)
```

### Modified Files

```
sanctionmanager-minecraft/
├── build.gradle.kts                               (Added HikariCP, Jedis)
└── src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/
    ├── MinecraftSanctionManager.java              (Added PlayerRepository)
    └── listener/
        ├── PlayerListener.java (Spigot)           (Save on join)
        ├── PlayerListener.java (Bungee)           (Save on join)
        └── PlayerListener.java (Velocity)         (Save on join)

sanctionmanager-spigot/src/main/java/.../SpigotMain.java          (Pass manager to listener)
sanctionmanager-bungee/src/main/java/.../BungeeMain.java          (Pass manager to listener)
sanctionmanager-velocity/src/main/java/.../VelocityMain.java      (Pass manager to listener)
```

## Verification

### Build Status

```bash
./gradlew :sanctionmanager-minecraft:build
# BUILD SUCCESSFUL

./gradlew :sanctionmanager-minecraft:test
# 8 tests passed
```

### Module Dependencies

```
sm-minecraft
  ├── Depends on: sm-core (API only)
  ├── Does NOT use: Core Storage, Core Repository
  └── Provides: PlayerRepository for offline player resolution
```

### Data Isolation Verified

- ✓ Minecraft uses separate database tables
- ✓ Minecraft uses separate Redis keys
- ✓ Minecraft uses separate file directories
- ✓ Core continues to use UUID-only identity
- ✓ No circular dependencies introduced

## Usage Example

### Full Command Flow

```java
// User types: /ban Steve

// 1. Resolve player name to UUID (Minecraft layer)
UUID uuid = manager.getPlayerRepository().findUuidByName("Steve");

if (uuid == null) {
    // Player never joined the server
    sender.sendMessage(translations.get("player.not-found", "Steve"));
    return;
}

// 2. Create punishment (Core layer)
Punishment punishment = punishmentFactory.createBan()
    .target(uuid)              // UUID only, no name
    .operator(operatorUuid)    // UUID only, no name
    .reason(reason)
    .build();

// 3. Apply punishment
punishmentManager.apply(punishment);

// 4. Get name for display (Minecraft layer)
String targetName = manager.getPlayerRepository().findNameByUuid(uuid);
sender.sendMessage("Banned " + targetName);
```

## Summary

This implementation successfully:

1. ✅ Enables offline player resolution for Minecraft commands
2. ✅ Maintains complete data separation between Minecraft and Core layers
3. ✅ Supports all storage modes (Memory, File, Database, Redis)
4. ✅ Preserves Core's UUID-only identity principle
5. ✅ Works across all platforms (Spigot, Bungee, Velocity)
6. ✅ Java 8 compatible
7. ✅ Thread-safe implementations
8. ✅ Comprehensive test coverage
9. ✅ Zero impact on existing Core functionality
10. ✅ Production-ready with proper resource management

The Minecraft layer now owns player name resolution, while Core continues to focus purely on sanction management with UUID identity.
