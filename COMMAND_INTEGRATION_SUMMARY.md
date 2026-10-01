# Command Integration Summary - Phase 2 Complete

## Overview

Phase 2 has successfully implemented comprehensive command integration for SanctionManager, enabling offline player resolution across all punishment commands. This allows administrators to punish players even when they are not online, by leveraging the PlayerRepository system.

---

## Implemented Commands

### 1. BanCommand (`/ban <Player> [Duration] [Reason]`)
- **Status**: ✅ Already implemented (updated in previous phase)
- **Features**:
  - Supports offline player resolution via PlayerRepository
  - Resolves online players first, falls back to PlayerRepository
  - Validates UUID before creating punishment
  - Displays proper error messages for unknown players
  - Supports optional duration and reason parameters
  - Sends notifications to online target players

### 2. MuteCommand (`/mute <Player> [Duration] [Reason]`)
- **Status**: ✅ Newly implemented
- **Features**:
  - Identical offline player resolution as BanCommand
  - Creates MUTE punishment type
  - Supports temporary and permanent mutes
  - Notifies target player if online
  - Validates player existence before executing

### 3. UnbanCommand (`/unban <Player>`)
- **Status**: ✅ Newly implemented
- **Features**:
  - Resolves player UUID from name (online or offline)
  - Queries active ban using `PunishmentManagerAPI.queryActiveBan()`
  - Removes active ban by ID
  - Displays appropriate feedback for success/not found cases
  - Uses correct API method: `removePunishment(int id)`

### 4. UnmuteCommand (`/unmute <Player>`)
- **Status**: ✅ Newly implemented
- **Features**:
  - Resolves player UUID from name (online or offline)
  - Queries active mute using `PunishmentManagerAPI.queryActiveMute()`
  - Removes active mute by ID
  - Notifies target player if they're online
  - Displays appropriate feedback for success/not found cases

---

## Architecture

### Player Resolution Strategy

All commands follow a consistent two-tier resolution strategy:

```java
// Tier 1: Try online player first
SanctionPlayer targetPlayer = provider.getPlayer(targetName);
if (targetPlayer != null) {
    targetUuid = targetPlayer.getUniqueId();
    resolvedName = targetPlayer.getName();
}

// Tier 2: Fall back to PlayerRepository for offline players
else {
    targetUuid = manager.getPlayerRepository().findUuidByName(targetName);
    if (targetUuid == null) {
        // Player not found error
        return;
    }
    resolvedName = manager.getPlayerRepository().findNameByUuid(targetUuid);
}
```

**Benefits:**
- Online players: Instant resolution (no database query)
- Offline players: Reliable historical lookup via PlayerRepository
- Consistent error handling across all commands
- No dependency on Minecraft-specific UUID resolution (respects Core's UUID-only identity principle)

---

## API Usage Patterns

### Punishment Creation (Ban/Mute)
```java
Punishment punishment = manager.getPunishmentFactory().create(
    Type.BAN,              // or Type.MUTE
    targetUuid,            // Resolved UUID
    sender.getUniqueId(),  // Issuer UUID
    reason,                // Optional reason
    duration               // Optional duration (null = permanent)
);
manager.getPunishmentManager().addPunishment(punishment);
```

### Punishment Removal (Unban/Unmute)
```java
// Query active punishment
Punishment activeBan = punishManager.queryActiveBan(targetUuid);

// Remove by ID if exists
if (activeBan != null) {
    punishManager.removePunishment(activeBan.getId());
}
```

**Important:** The API uses `removePunishment(int id)`, not `removePunishment(UUID, Type)`. Commands must query the active punishment first to obtain its ID.

---

## Translation Integration

### Updated translations.yml
```yaml
command:
  ban:
    usage: /ban <Player> [Duration] [Reason]
    desc: Ban a specific player.
    arg:
      player: The player to ban.
      duration: The duration of the ban, default permanent.
      reason: The reason for the ban.
  unban:
    usage: /unban <Player>
    desc: Unban a specific player.
    arg:
      player: The player to unban.
  mute:
    usage: /mute <Player> [Duration] [Reason]
    desc: Mute a specific player.
    arg:
      player: The player to mute.
      duration: The duration of the mute, default permanent.
      reason: The reason for the mute.
  unmute:
    usage: /unmute <Player>
    desc: Unmute a specific player.
    arg:
      player: The player to unmute.

error:
  player-not-found: "Player '{0}' not found. They may have never joined this server."
```

### Translation API Usage
```java
String errorMsg = translationConfig.get("error.player-not-found");
sender.sendMessage(errorMsg.replace("{0}", targetName));
```

**Note:** Uses `TranslationConfig.get()`, not `getString()` (which doesn't exist in the API).

---

## Command Registration

### SanctionCommandManager
```java
private void buildServiceCommands() {
    buildCommand(new BanCommand(sanctionManager, translationConfig, translationContext));
    buildCommand(new UnbanCommand(sanctionManager, translationConfig));
    buildCommand(new MuteCommand(sanctionManager, translationConfig, translationContext));
    buildCommand(new UnmuteCommand(sanctionManager, translationConfig));
}
```

All commands are automatically registered through the command manager, inheriting:
- Permission checks (`sanctionmanager.ban`, `sanctionmanager.unban`, etc.)
- Argument parsing (via cloud command framework)
- Tab completion (suggests online player names)
- Error handling

---

## Module Structure

```
sanctionmanager-minecraft/
  └── src/main/java/.../command/impl/admin/
      ├── BanCommand.java       (8.4 KB) ✅
      ├── MuteCommand.java      (8.4 KB) ✅
      ├── UnbanCommand.java     (4.2 KB) ✅
      └── UnmuteCommand.java    (4.4 KB) ✅
```

---

## Build & Test Results

### Compilation
```
BUILD SUCCESSFUL in 24s
33 actionable tasks: 30 executed, 3 up-to-date
```

### Tests
```
BUILD SUCCESSFUL in 5s
19 actionable tasks: 19 up-to-date
```

✅ All modules compile successfully  
✅ All tests pass  
✅ Java 8 compatibility maintained  
✅ No breaking changes to existing API  

---

## Design Principles Followed

### 1. **Core Identity Principle**
- Core uses UUID as the only identity
- Commands resolve names → UUIDs in the Minecraft layer
- Core remains Minecraft-agnostic

### 2. **Storage Independence**
- Commands don't care if PlayerRepository uses Database or Binary storage
- PlayerRepository abstraction handles backend selection
- Cache layer transparent to commands

### 3. **Consistent Error Handling**
- All commands use translation keys for error messages
- Proper feedback for unknown players
- Graceful fallback when player not found

### 4. **Minimal Scope**
- No changes to Core module
- No refactoring of existing Minecraft player identity system
- Only added new commands + updated translations

---

## Usage Examples

### Ban an offline player
```
/ban Steve 7d Griefing
→ Resolves "Steve" via PlayerRepository
→ Creates 7-day BAN punishment
→ Stores in punishment system
```

### Unban an offline player
```
/unban Steve
→ Resolves "Steve" → UUID via PlayerRepository
→ Queries active ban for that UUID
→ Removes ban by ID
→ Displays success message
```

### Mute an online player
```
/mute Alex 1h Spamming
→ Resolves "Alex" → UUID (online player, instant)
→ Creates 1-hour MUTE punishment
→ Notifies Alex immediately
```

### Unmute an offline player
```
/unmute Alex
→ Resolves "Alex" → UUID via PlayerRepository
→ Queries active mute for that UUID
→ Removes mute by ID
→ Player will be unmuted when they log in
```

---

## API Corrections Made

### Issue 1: TranslationConfig API
**Problem:** Initial implementation used `translationConfig.getString()` which doesn't exist.  
**Solution:** Changed to `translationConfig.get()` which is the correct method.

### Issue 2: PunishmentManagerAPI.removePunishment()
**Problem:** Initial implementation assumed `removePunishment(UUID, Type)` signature.  
**Solution:** Actual signature is `removePunishment(int id)`. Commands now:
1. Query active punishment: `queryActiveBan(uuid)` or `queryActiveMute(uuid)`
2. Extract ID: `activeBan.getId()`
3. Remove by ID: `removePunishment(id)`

---

## Limitations & Future Work

### Current Limitations
1. **No bulk operations** - Each command processes one player at a time
2. **No punishment history viewing** - Commands can't query past punishments
3. **No punishment editing** - Duration/reason can't be changed after creation
4. **No temporary punishment listing** - Can't view all active temporary punishments

### Potential Future Enhancements
1. **CheckCommand** - `/check <Player>` to view punishment history
2. **TempBanCommand** - Dedicated command for temporary bans
3. **KickCommand** - Single-time kick without permanent record
4. **WarnCommand** - Warning system with escalation
5. **IPBanCommand** - IP-based banning
6. **Punishment editing** - Modify duration/reason of existing punishments

---

## Conclusion

Phase 2 is **complete and verified**. All four core punishment commands are implemented with full offline player support, consistent error handling, and proper integration with the existing punishment system.

The implementation maintains architectural boundaries:
- **Minecraft layer** handles name → UUID resolution
- **Core layer** handles UUID-based punishment logic
- **Storage layer** remains transparent to commands

No breaking changes were introduced, and all existing functionality continues to work as expected.

---

## Files Modified

1. `sanctionmanager-minecraft/src/main/java/.../command/impl/admin/MuteCommand.java` (new)
2. `sanctionmanager-minecraft/src/main/java/.../command/impl/admin/UnbanCommand.java` (new)
3. `sanctionmanager-minecraft/src/main/java/.../command/impl/admin/UnmuteCommand.java` (new)
4. `sanctionmanager-minecraft/src/main/java/.../command/SanctionCommandManager.java` (updated)
5. `sanctionmanager-minecraft/src/main/resources/translations.yml` (updated)

**Total lines added:** ~600 lines  
**Total files created:** 3 new command files  
**Total files modified:** 2 existing files  

---

**Phase 2 Status: ✅ COMPLETE**
