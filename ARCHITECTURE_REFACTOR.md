# SanctionManager Storage Architecture Refactor

## Executive Summary

This document describes the storage architecture refactor completed for SanctionManager. The refactor establishes clear domain boundaries between Core and Minecraft modules while maintaining shared storage infrastructure.

**Key Achievement**: Removed `operatorName` from Core domain model, establishing UUID-only identity principle in Core while preserving the ability for Minecraft layer to handle player names.

---

## 1. Current Storage Architecture (After Refactor)

### Module Structure

```
sanctionmanager-platform
        ↓
sanctionmanager-minecraft
        ↓
sanctionmanager-core
        ↓
sanctionmanager-api
```

**Critical Principle**: Dependency flows downward only. Core never depends on Minecraft.

---

### 1.1 Core (sanctionmanager-core)

**Data Ownership**: Punishment domain data only

#### Domain Models
- `Punishment` (API interface)
- `PunishmentRecord` (Core implementation)
- `Ban`, `Mute`, `Kick`, `Warn` (Types)

#### Identity Principle
- **UUID = Identity** (strictly enforced)
- Core does NOT store player names
- Core does NOT store operator names
- `PunishmentRecord` constructor signature:
  ```java
  PunishmentRecord(
      int id,
      int relId,
      UUID target,
      UUID executor,        // Note: No operatorName
      LocalDateTime start,
      LocalDateTime end,
      boolean active,
      Punishment precedent,
      boolean lifted,
      Punishment liftedBy,
      boolean expired,
      UUID expiredBy,
      String reason,
      Type type
  )
  ```

#### Storage Backends

**PunishmentRepository Interface**
```java
public interface PunishmentRepository {
    Punishment findById(int id);
    Collection<Punishment> findByTarget(UUID target);
    Collection<Punishment> findAll();
    void save(Punishment punishment);
    void update(Punishment punishment);
    void delete(int id);
}
```

**Implementations**:
1. **HikariPunishmentRepository** - SQL database (MySQL/PostgreSQL/H2/SQLite)
2. **BinaryPunishmentRepository** - Binary file storage

**Storage Selection**: Controlled by `StorageConfig` in Core, configured by Minecraft layer

#### Cache Layer

**PunishmentCache Interface**
```java
public interface PunishmentCache {
    Optional<Punishment> get(UUID target, Type type);
    void put(UUID target, Type type, Punishment punishment);
    void invalidate(UUID target, Type type);
    void invalidateAll(UUID target);
}
```

**Implementations**:
1. **LocalPunishmentCache** - In-memory (ConcurrentHashMap)
2. **RedisPunishmentCache** - Redis-based distributed cache

**Cache Strategy**:
- Cache keyed by: `UUID:Type` (e.g., `uuid:BAN`)
- Core manages cache lifecycle
- Cache backend selection controlled by Core configuration

---

### 1.2 Minecraft (sanctionmanager-minecraft)

**Data Ownership**: Minecraft-specific player data

#### Current Responsibilities
- Player UUID resolution (existing, not modified)
- Command handling and user interface
- Configuration management
- Core initialization and lifecycle

#### Future Extension Point: Player Name Repository

**NOT YET IMPLEMENTED** - Design proposal for when needed:

```java
public interface PlayerNameRepository {
    @Nullable String findName(UUID uuid);
    void saveName(UUID uuid, String name);
    @Nullable UUID findUuid(String name);
}
```

**Storage Backend Options** (when implemented):
- Can reuse Core's storage infrastructure
- Separate data tables/files from Core data
- Separate cache namespace from Core cache

**Example Storage Separation**:
```
Database Tables:
  - Core:      punishment, ban, mute
  - Minecraft: player_name

File Storage:
  - Core:      data/core/punishments/
  - Minecraft: data/minecraft/players/

Redis Keys:
  - Core:      sanction:uuid:type
  - Minecraft: minecraft:player:uuid
```

---

## 2. What Was Changed

### 2.1 Removed: `operatorName` from Core

**Before**:
```java
PunishmentRecord(
    ...,
    UUID executor,
    String operatorName,  // ❌ Removed
    ...
)
```

**After**:
```java
PunishmentRecord(
    ...,
    UUID executor,  // ✅ UUID only
    ...
)
```

**Rationale**: 
- Core identity = UUID only
- Player names are Minecraft concept
- Name resolution belongs in Minecraft layer

### 2.2 Modified Files

#### Core Module
- `sanctionmanager-api/src/main/java/io/github/floatingpointmc/sanctionmanager/api/punishment/Punishment.java`
  - Removed `getOperatorName()` method
  
- `sanctionmanager-api/src/main/java/io/github/floatingpointmc/sanctionmanager/api/punishment/PunishmentFactory.java`
  - Removed `operatorName` parameter from `create()` method

- `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/model/PunishmentRecord.java`
  - Removed `operatorName` field
  - Removed parameter from constructor
  - Removed getter method

- `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/factory/PunishmentFactoryCore.java`
  - Updated `create()` to remove `operatorName` parameter

- `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/factory/PunishmentBuilder.java`
  - Removed `operatorName` field and builder method

- `sanctionmanager-core/src/test/java/io/github/floatingpointmc/sanctionmanager/core/SanctionManagerCoreTest.java`
  - Updated all test constructors to remove `operatorName`

#### Minecraft Module
- `sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/MinecraftSanctionManager.java`
  - Removed `operatorName` from punishment creation

#### Platform Modules
- `sanctionmanager-spigot/src/main/java/io/github/floatingpointmc/sanctionmanager/spigot/listener/PlayerLoginListener.java`
  - Removed `getOperatorName()` call

- `sanctionmanager-velocity/src/main/java/io/github/floatingpointmc/sanctionmanager/velocity/listener/VelocityPlayerLoginListener.java`
  - Removed `getOperatorName()` call

### 2.3 What Was NOT Changed

**Deliberately preserved**:
- UUID resolution logic in Minecraft layer
- Command structure
- Configuration system
- Storage backend implementations
- Cache implementations
- Repository interfaces
- Module dependency direction
- Existing test coverage

---

## 3. Architecture Principles Established

### 3.1 Domain Boundary Principle

```
┌──────────────────────────────────────┐
│         Minecraft Module             │
│                                      │
│  - Player Names (UUID → Name)        │
│  - Minecraft API Integration         │
│  - Commands & UI                     │
│  - Configuration Provider            │
└─────────────┬────────────────────────┘
              │
              ↓
┌──────────────────────────────────────┐
│          Core Module                 │
│                                      │
│  - Punishment Domain (UUID only)     │
│  - Repository Interfaces             │
│  - Storage Backends                  │
│  - Cache Management                  │
└──────────────────────────────────────┘
```

### 3.2 Storage Infrastructure Sharing

**Shared** (can be reused across modules):
- Database connection pool (HikariCP)
- Redis client
- File I/O utilities
- Serialization infrastructure
- Cache abstractions

**NOT Shared** (module-specific):
- Domain models (Punishment vs Player)
- Repository implementations
- Database tables/schemas
- Cache keys/namespaces
- Data access logic

### 3.3 Configuration Flow

```
Minecraft Configuration
        ↓
  Parse & Validate
        ↓
StorageConfig → Core
CacheConfig   → Core
        ↓
Core initializes backends
```

**Key Point**: Core doesn't know about Minecraft config format. Minecraft translates config into Core-compatible parameters.

---

## 4. Storage Backend Details

### 4.1 Database Backend (HikariPunishmentRepository)

**Supported Databases**:
- MySQL
- PostgreSQL
- H2
- SQLite

**Schema** (Core tables only):
```sql
CREATE TABLE punishment (
    id          INT PRIMARY KEY,
    rel_id      INT,
    target      UUID NOT NULL,
    executor    UUID,          -- No operator_name column
    start_time  TIMESTAMP,
    end_time    TIMESTAMP,
    active      BOOLEAN,
    -- ... other fields
);
```

**Connection Management**:
- HikariCP connection pool
- Configured via Core's StorageConfig
- Lifecycle managed by Core

### 4.2 Binary File Backend (BinaryPunishmentRepository)

**File Structure**:
```
data/
└── core/
    └── punishments/
        ├── <uuid-1>.dat
        ├── <uuid-2>.dat
        └── ...
```

**Serialization**:
- Binary format (implementation-specific)
- No operator_name field stored
- UUID-based file naming

### 4.3 Cache Backend

**Local Cache**:
- `ConcurrentHashMap<CacheKey, Punishment>`
- CacheKey = `(UUID target, Type type)`
- In-memory only, no persistence

**Redis Cache**:
- Key pattern: `sanction:{uuid}:{type}`
- JSON serialization
- TTL configurable
- Distributed across servers

---

## 5. Future Extensions

### 5.1 Player Name Repository (When Needed)

**Design Proposal**:

```java
// In sanctionmanager-minecraft module
public interface PlayerNameRepository {
    Optional<String> getName(UUID uuid);
    Optional<UUID> getUuid(String name);
    void save(UUID uuid, String name);
    void updateName(UUID uuid, String newName);
}
```

**Storage Options**:
1. **Database**:
   ```sql
   CREATE TABLE player_name (
       uuid        UUID PRIMARY KEY,
       name        VARCHAR(16) NOT NULL,
       updated_at  TIMESTAMP
   );
   CREATE INDEX idx_name ON player_name(name);
   ```

2. **File**:
   ```
   data/minecraft/players/<uuid>.json
   ```

3. **Cache**:
   - Memory: `Map<UUID, String>` and `Map<String, UUID>`
   - Redis: `minecraft:uuid:{uuid}` → name, `minecraft:name:{name}` → uuid

**Integration Points**:
- Player join event → `saveName(uuid, name)`
- Command sender → `getName(executor)` for display
- Tab completion → `getUuid(partialName)`

**Key Principle**: This repository lives in `sanctionmanager-minecraft`, NOT in Core.

### 5.2 Additional Minecraft Data

Future Minecraft-specific data should follow the same pattern:

```
sanctionmanager-minecraft/
├── repository/
│   ├── PlayerNameRepository.java
│   ├── PlayerDataRepository.java     // Future
│   └── ServerDataRepository.java     // Future
└── storage/
    ├── MinecraftDatabaseStorage.java  // If needed
    └── MinecraftFileStorage.java      // If needed
```

**Never**:
- ❌ Add Minecraft data to Core repositories
- ❌ Add Minecraft tables to Core schema
- ❌ Mix Minecraft and Core data in same cache namespace
- ❌ Make Core aware of Player names, servers, proxies

---

## 6. Decision Rationale

### 6.1 Why Not Use Existing ORM Libraries?

**Evaluated Libraries**:
- ORMLite
- Ebean
- jOOQ
- MyBatis
- sql2o
- Apache Commons DbUtils
- JDO
- Spring Data

**Rejection Reasons**:
1. **SQL-only**: No library supports both SQL and binary file backends
2. **Size**: Most ORMs too heavy for Minecraft plugin environment
3. **Complexity**: Features like lazy loading, entity relationships, transactions are overkill
4. **License**: Some (jOOQ) have commercial restrictions
5. **Dependency bloat**: Spring Data requires entire Spring framework

**Decision**: Keep existing lightweight Repository pattern.

### 6.2 Why Not Implement CustomTable<T> System?

**Original Proposal** (REJECTED):
```java
// ❌ This approach was abandoned
public interface CustomTable<T> {
    Query<T> query();
    Insert<T> insert();
    boolean useCache();
}
```

**Why Rejected**:
1. **Over-abstraction**: SanctionManager doesn't need generic table system
2. **Domain confusion**: Blurs ownership between Core and extensions
3. **Unnecessary complexity**: Would create mini-ORM nobody asked for
4. **Wrong problem**: We need domain boundaries, not generic tables

**Better Approach**: Module-specific repositories with clear ownership.

### 6.3 Why Remove operatorName from Core?

**Problem**:
```java
// Before: Mixed concerns
PunishmentRecord(UUID executor, String operatorName)
// Core must store Minecraft concept (player name)
```

**Solution**:
```java
// After: Clean separation
PunishmentRecord(UUID executor)  // Core: UUID identity only

// Minecraft layer resolves names when needed:
String name = playerNameRepository.getName(executor);
```

**Benefits**:
- ✅ Core stays platform-agnostic
- ✅ Name resolution logic in correct module
- ✅ Easier to support offline-mode, Bedrock, multiple proxies
- ✅ Name changes don't affect Core data integrity

---

## 7. Testing & Validation

### 7.1 Build Results

```bash
./gradlew clean build
```

**Status**: ✅ BUILD SUCCESSFUL

**Modules Compiled**:
- sanctionmanager-api
- sanctionmanager-core
- sanctionmanager-minecraft
- sanctionmanager-spigot
- sanctionmanager-bungee
- sanctionmanager-velocity

### 7.2 Test Results

```bash
./gradlew test
```

**Status**: ✅ All tests passing

**Test Coverage**:
- Core repository tests
- Punishment factory tests
- Cache tests
- Serialization tests

### 7.3 Java Compatibility

**Target**: Java 8
**Status**: ✅ Compatible

No Java 9+ features introduced.

---

## 8. Migration Guide

### 8.1 For Core API Users

**Breaking Changes**:

```java
// ❌ Old API (removed)
punishment.getOperatorName()

// ✅ New API
UUID executor = punishment.getExecutor();
```

**Migration**:
1. Remove calls to `getOperatorName()`
2. Use `getExecutor()` to get UUID
3. Resolve names in Minecraft layer if needed

### 8.2 For Punishment Creation

**Before**:
```java
factory.create(target, "PlayerName", Type.BAN)
```

**After**:
```java
factory.create(target, Type.BAN)
```

### 8.3 For Database Users

**Schema Change**: None required

The `operator_name` column was never actually used in the database schema. Core already stored only UUIDs.

---

## 9. Limitations & Known Issues

### 9.1 Current Limitations

1. **No name caching yet**: Minecraft layer doesn't cache UUID→Name mapping yet
   - **Impact**: May need repeated name lookups
   - **Solution**: Implement PlayerNameRepository when performance becomes issue

2. **No name history**: Old punishment records don't know historical names
   - **Impact**: Can't show "who was this player when banned"
   - **Solution**: Acceptable - names change, UUIDs don't

3. **Display layer needs update**: Commands/GUIs still need to resolve names
   - **Impact**: More UUID→Name lookups in display logic
   - **Solution**: Normal - display layer responsibility

### 9.2 Non-Issues

These are **intentional design choices**, not limitations:

- ❌ "Core doesn't store player names" → ✅ Correct domain separation
- ❌ "Need to query names separately" → ✅ Minecraft layer responsibility
- ❌ "Can't search punishments by operator name" → ✅ Search by UUID instead

---

## 10. Next Steps (Optional Future Work)

### Priority 1: PlayerNameRepository Implementation

**When needed**: If name resolution becomes performance bottleneck

**Scope**:
- Create `PlayerNameRepository` interface in `sanctionmanager-minecraft`
- Implement database backend (reuse Core's HikariCP)
- Implement cache layer (reuse Core's cache infrastructure)
- Hook into player join events
- Update command/GUI layer to use repository

**Estimated effort**: Small (~200 lines)

### Priority 2: Name Resolution Caching

**When needed**: If repeated UUID→Name lookups cause lag

**Scope**:
- Add memory cache for recent name lookups
- TTL-based expiration
- Invalidation on player join

**Estimated effort**: Very small (~50 lines)

### Priority 3: Historical Name Tracking

**When needed**: If users need "who was this player when event happened"

**Scope**:
- Store `(uuid, name, timestamp)` history
- Query API: `getName(UUID uuid, LocalDateTime when)`
- Database schema: `player_name_history` table

**Estimated effort**: Medium (~500 lines)

---

## 11. Conclusion

### What Was Achieved

✅ **Clean domain boundaries**: Core owns sanctions, Minecraft owns player data
✅ **UUID-only identity in Core**: Platform-agnostic, future-proof
✅ **Preserved existing features**: All storage backends still work
✅ **No breaking changes to storage**: Database/file formats unchanged
✅ **Maintained module dependencies**: No circular dependencies introduced
✅ **Build & tests passing**: Verified working implementation
✅ **Java 8 compatibility**: No version upgrade required

### Key Takeaways

1. **Data ownership matters**: Each module should own its domain data
2. **Infrastructure can be shared**: Storage backends are reusable utilities
3. **Domain logic cannot be shared**: Repositories/models stay module-specific
4. **Configuration flows downward**: Minecraft configures Core, not vice versa
5. **Identity abstraction works**: UUID-only Core supports any platform

### Architecture Health

The refactored architecture now properly separates:
- **Core**: Generic punishment system (UUID-based)
- **Minecraft**: Minecraft-specific features (names, commands, events)
- **Platform**: Server implementation bridges (Spigot, Velocity, etc.)

This establishes a solid foundation for:
- Supporting additional platforms (Sponge, Fabric, Forge)
- Adding Minecraft-specific features without polluting Core
- Maintaining Core as standalone library
- Third-party plugins extending Minecraft layer safely

---

**Document Version**: 1.0  
**Date**: 2025-10-01  
**Author**: Architecture Refactor Task  
**Status**: ✅ Complete
