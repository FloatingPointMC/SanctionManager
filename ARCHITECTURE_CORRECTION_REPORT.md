# Architecture Correction Report: Repository vs Cache

## Executive Summary

Successfully corrected the architecture to properly distinguish between **Repository (Persistence)** and **Cache** layers in the SanctionManager Minecraft module.

### Problem Identified

The previous implementation incorrectly treated `Memory` and `Redis` as **Repository** implementations (persistence layer), when they should be **Cache** implementations (acceleration layer).

**Incorrect Design (Before):**
```
PlayerRepositoryFactory priority:
1. Database → ✅ Persistence
2. Redis → ❌ Treated as Persistence (WRONG)
3. File → ✅ Persistence
4. Memory → ❌ Treated as Persistence (WRONG)
```

**Correct Design (After):**
```
PlayerService
    ├── PlayerCache (RAM / Redis)
    └── PlayerRepository (Database / File)
```

---

## Terminology Clarification

### Project-Specific Terminology

**In this project:**
- **Repository = Persistence** (permanent storage)
- **Cache = RAM / Redis** (temporary acceleration)

This terminology is a project convention and must be respected. Do not attempt to rename `Repository` to `Persistence`.

---

## Architecture Changes

### Before (Incorrect)

```
┌─────────────────────────────────┐
│   PlayerRepositoryFactory       │
│                                 │
│   1. DatabasePlayerRepository   │
│   2. RedisPlayerRepository  ❌  │
│   3. FilePlayerRepository       │
│   4. MemoryPlayerRepository ❌  │
└─────────────────────────────────┘
         │
         ▼
    Data Access
```

**Problems:**
1. Redis and Memory were treated as persistence
2. No cache layer abstraction
3. Server restart would lose data if Redis/Memory was selected
4. No cache-first query flow

---

### After (Correct)

```
┌──────────────────────────────────────────┐
│          PlayerService                    │
│                                          │
│   Query Flow:                            │
│   1. Check PlayerCache (RAM/Redis)       │
│   2. If miss → PlayerRepository          │
│   3. Update cache with result            │
│                                          │
│   Write Flow:                            │
│   1. Write to PlayerRepository           │
│   2. Update/invalidate cache             │
└────────────┬─────────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
  PlayerCache   PlayerRepository
      │             │
  ┌───┴───┐     ┌───┴───┐
  │       │     │       │
 RAM   Redis   DB    File
```

---

## Components Created

### 1. PlayerCache Interface

**Location:** `sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/PlayerCache.java`

**Purpose:** Cache abstraction for player UUID ↔ Name mapping

**Methods:**
- `findUuidByName(String name)` - Get UUID from cache
- `findNameByUuid(UUID uuid)` - Get name from cache
- `save(UUID uuid, String name)` - Save to cache
- `invalidate(UUID uuid)` - Remove from cache
- `invalidateAll()` - Clear all cache

**Implementations:**
- `MemoryPlayerCache` - In-memory ConcurrentHashMap
- `RedisPlayerCache` - Redis-based cache

---

### 2. PlayerRepository Interface (Updated)

**Location:** `sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/PlayerRepository.java`

**Purpose:** Persistence layer for player data (unchanged interface, but semantic clarification)

**Methods:**
- `findUuidByName(String name)` - Load from persistent storage
- `findNameByUuid(UUID uuid)` - Load from persistent storage
- `save(UUID uuid, String name)` - Save to persistent storage

**Implementations:**
- `DatabasePlayerRepository` - SQL database persistence
- `FilePlayerRepository` - Binary file persistence

---

### 3. PlayerService (NEW)

**Location:** `sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/PlayerService.java`

**Purpose:** Orchestrates Cache + Repository with correct flow

**Query Flow:**
```java
public UUID findUuidByName(String name) {
    // 1. Try cache first
    UUID uuid = cache.findUuidByName(name);
    if (uuid != null) {
        return uuid; // Cache hit
    }
    
    // 2. Cache miss - query repository (persistence)
    uuid = repository.findUuidByName(name);
    if (uuid != null) {
        // 3. Update cache
        String cachedName = cache.findNameByUuid(uuid);
        if (cachedName == null) {
            cache.save(uuid, name);
        }
    }
    
    return uuid;
}
```

**Write Flow:**
```java
public void save(UUID uuid, String name) {
    // 1. Save to persistent storage FIRST
    repository.save(uuid, name);
    
    // 2. Update cache
    cache.save(uuid, name);
}
```

---

### 4. PlayerServiceFactory (NEW)

**Location:** `sanctionmanager-minecraft/src/main/java/io/github/floatingpointmc/sanctionmanager/minecraft/player/PlayerServiceFactory.java`

**Purpose:** Create PlayerService with appropriate Cache + Repository backends

**Priority:**

**Cache (one of):**
1. Redis (if enabled)
2. Memory (default)

**Repository (one of):**
1. Database (if enabled)
2. File (default)

---

## Files Modified

### Core Minecraft Module

1. **PlayerCache.java** - ✅ Created
2. **MemoryPlayerCache.java** - ✅ Created
3. **RedisPlayerCache.java** - ✅ Created
4. **PlayerService.java** - ✅ Created
5. **PlayerServiceFactory.java** - ✅ Created
6. **MinecraftSanctionManager.java** - ✅ Updated (getPlayerService() instead of getPlayerRepository())

### Commands

7. **BanCommand.java** - ✅ Updated (uses getPlayerService())
8. **MuteCommand.java** - ✅ Updated (uses getPlayerService())
9. **UnbanCommand.java** - ✅ Updated (uses getPlayerService())
10. **UnmuteCommand.java** - ✅ Updated (uses getPlayerService())

### Platform Listeners

11. **sanctionmanager-spigot/.../PlayerListener.java** - ✅ Updated
12. **sanctionmanager-bungee/.../PlayerListener.java** - ✅ Updated
13. **sanctionmanager-velocity/.../PlayerListener.java** - ✅ Updated

### Tests

14. **PlayerRepositoryTest.java** - ✅ Updated

### Deleted Files

15. **MemoryPlayerRepository.java** - ❌ Deleted (was incorrectly treating Memory as persistence)
16. **RedisPlayerRepository.java** - ❌ Deleted (was incorrectly treating Redis as persistence)
17. **PlayerRepositoryFactory.java** - ❌ Deleted (replaced by PlayerServiceFactory)

---

## Data Flow Examples

### Example 1: Ban Offline Player

**User command:** `/ban Steve Hacking`

**Flow:**
```
1. BanCommand.execute()
   ↓
2. manager.getPlayerService().findUuidByName("Steve")
   ↓
3. PlayerService.findUuidByName()
   ↓
4. PlayerCache.findUuidByName("Steve")
   ↓ (cache miss)
5. PlayerRepository.findUuidByName("Steve")
   ↓
6. DatabasePlayerRepository queries SQL:
   SELECT uuid FROM players WHERE name = 'Steve'
   ↓
7. Returns UUID: 550e8400-e29b-41d4-a716-446655440000
   ↓
8. PlayerCache.save(uuid, "Steve") ← Update cache
   ↓
9. PunishmentManagerAPI.ban(uuid, ...)
```

**Result:** ✅ Offline player "Steve" can be banned using historical data

---

### Example 2: Player Join

**Event:** Player "Alex" joins server

**Flow:**
```
1. PlayerListener.onJoin(event)
   ↓
2. UUID uuid = player.getUniqueId()
   String name = player.getName()
   ↓
3. manager.getPlayerService().save(uuid, name)
   ↓
4. PlayerService.save()
   ↓
5. PlayerRepository.save(uuid, name) ← Persist FIRST
   ↓
6. DatabasePlayerRepository executes SQL:
   INSERT INTO players (uuid, name) VALUES (?, ?)
   ON DUPLICATE KEY UPDATE name = ?
   ↓
7. PlayerCache.save(uuid, name) ← Update cache
   ↓
8. MemoryPlayerCache stores in ConcurrentHashMap
```

**Result:** ✅ Player data persisted to database AND cached in memory

---

### Example 3: Server Restart

**Before restart:**
- Memory cache has 1000 UUID ↔ Name mappings
- Database has 10,000 player records

**After restart:**
- Memory cache is empty (RAM cleared)
- Database still has 10,000 records ✅

**First query after restart:**
```
/ban HistoricalPlayer Griefing
   ↓
PlayerCache.findUuidByName() → null (cache cold)
   ↓
PlayerRepository.findUuidByName() → UUID (from database)
   ↓
Cache warmed with result
```

**Result:** ✅ Historical player data survives restart

---

## Repository vs Cache: Clear Boundaries

### PlayerRepository (Persistence)

**Responsibility:** **Permanent storage**

**Backend Options:**
- Database (MySQL, PostgreSQL, etc.)
- Binary File

**Characteristics:**
- ✅ Survives server restart
- ✅ Stores all historical player data
- ❌ Slower access (disk I/O or network)
- ✅ Source of truth

**When to use:**
- Initial player join (persist UUID ↔ Name)
- Cache miss
- Historical data queries

---

### PlayerCache (Acceleration)

**Responsibility:** **Temporary acceleration**

**Backend Options:**
- Memory (RAM)
- Redis

**Characteristics:**
- ✅ Fast access (in-memory)
- ❌ May not survive restart (Memory) or may be volatile (Redis)
- ❌ Not source of truth
- ✅ Reduces database load

**When to use:**
- First attempt for all queries
- After successful repository query (warm cache)
- Frequently accessed data

---

## Configuration Impact

### Current Configuration (sm-minecraft)

```yaml
player:
  repository:
    type: database  # or 'file'
  cache:
    type: memory    # or 'redis'
```

### Factory Selection Logic

**PlayerServiceFactory.create():**

1. **Determine Repository backend:**
   - If database configured → DatabasePlayerRepository
   - Else → FilePlayerRepository (default)

2. **Determine Cache backend:**
   - If Redis configured → RedisPlayerCache
   - Else → MemoryPlayerCache (default)

3. **Create PlayerService:**
   - PlayerService(cache, repository)

---

## Testing Results

### Unit Tests

✅ **PlayerRepositoryTest** - All tests pass

**Tests verified:**
- DatabasePlayerRepository save/find operations
- FilePlayerRepository save/find operations
- PlayerService cache-first query flow
- PlayerService cache update on repository query
- PlayerService persistence-first write flow

### Integration Tests

✅ **Full module tests** - `./gradlew :sanctionmanager-minecraft:test`

**Result:** BUILD SUCCESSFUL

---

### Full Project Build

✅ **All modules** - `./gradlew build`

**Modules compiled:**
- sanctionmanager-api ✅
- sanctionmanager-core ✅
- sanctionmanager-minecraft ✅
- sanctionmanager-spigot ✅
- sanctionmanager-bungee ✅
- sanctionmanager-velocity ✅

**Result:** BUILD SUCCESSFUL

---

## Answering the Requirements

### ✅ Can `/ban <player>` query offline historical players?

**Yes.** 

When banning an offline player:
1. PlayerService checks cache (miss for offline player)
2. PlayerService queries PlayerRepository (database or file)
3. If player has ever joined, their UUID is found
4. Ban is applied using historical UUID

**Example:**
```
/ban OldPlayer Griefing
→ Queries database for "OldPlayer"
→ Finds UUID from 6 months ago
→ Successfully bans UUID
```

---

### ✅ Repository = Persistence (Database / File)

**Confirmed.**

- `DatabasePlayerRepository` - SQL persistence
- `FilePlayerRepository` - Binary file persistence
- Both implement `PlayerRepository` interface
- Both survive server restarts
- Both are source of truth

---

### ✅ Cache = RAM / Redis

**Confirmed.**

- `MemoryPlayerCache` - ConcurrentHashMap (RAM)
- `RedisPlayerCache` - Redis client
- Both implement `PlayerCache` interface
- Both are acceleration layers
- Neither is source of truth

---

### ✅ Query Flow: Cache → Repository

**Confirmed.**

```java
PlayerService.findUuidByName():
1. cache.findUuidByName() ← Try cache first
2. if (uuid != null) return uuid ← Cache hit
3. repository.findUuidByName() ← Cache miss, query persistence
4. cache.save(uuid, name) ← Warm cache
5. return uuid
```

---

### ✅ Write Flow: Repository → Cache

**Confirmed.**

```java
PlayerService.save():
1. repository.save(uuid, name) ← Persist FIRST
2. cache.save(uuid, name) ← Update cache
```

This guarantees data is never lost, even if cache fails.

---

## Limitations and Constraints

### What was NOT changed:

1. ❌ **Core module** - No changes to sm-core
2. ❌ **Punishment domain** - No changes to Ban/Mute/Kick models
3. ❌ **Core Repository** - PunishmentRepository unchanged
4. ❌ **Core Cache** - Core cache layer unchanged
5. ❌ **Project terminology** - "Repository" still means "Persistence"
6. ❌ **Minecraft identity logic** - UUID resolution unchanged
7. ❌ **Platform-specific code** - Only updated to use new API

### Current Limitations:

1. **No automatic cache invalidation** - Cache entries persist until server restart or manual invalidation
2. **No cache TTL** - Memory cache entries never expire
3. **No cache warming** - Cache is populated lazily on first query
4. **No distributed cache sync** - If using Redis, each server instance must invalidate independently

These are acceptable limitations for the current scope.

---

## Future Enhancements (Out of Scope)

1. **Cache TTL** - Expire old entries after configurable duration
2. **Cache warming** - Pre-load frequently accessed players on startup
3. **Distributed invalidation** - Redis pub/sub for cross-server cache sync
4. **Cache statistics** - Hit/miss ratio, eviction metrics
5. **Layered cache** - Memory (L1) + Redis (L2)

---

## Conclusion

The architecture has been successfully corrected to properly separate:

- **Repository (Persistence)** - Database / File (source of truth)
- **Cache (Acceleration)** - Memory / Redis (performance optimization)

All commands now correctly query historical player data through the PlayerService, which implements the proper cache-first, persistence-backed data flow.

**Project Status:** ✅ BUILD SUCCESSFUL  
**Tests:** ✅ ALL PASSING  
**Architecture:** ✅ CORRECTED
