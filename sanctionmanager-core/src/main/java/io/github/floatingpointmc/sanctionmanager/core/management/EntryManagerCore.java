package io.github.floatingpointmc.sanctionmanager.core.management;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.ConcurrentDataModificationException;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.EntryNotFoundException;
import io.github.floatingpointmc.sanctionmanager.api.management.EntryManager;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryTempSnapshot;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core implementation of EntryManager.
 * <p>
 * This is a minimal in-memory implementation for demonstration. A production implementation
 * would use a database for persistence.
 * </p>
 */
public class EntryManagerCore implements EntryManager {
    private final SanctionManagerCore core;
    private final Map<Long, EntrySnapshot> entries = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> typeRelIdCounters = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public EntryManagerCore(SanctionManagerCore core) {
        this.core = core;
    }

    @Override
    public void execute(@NotNull Entry entry) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (entry == null) {
            throw new NullPointerException("entry cannot be null");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntryTempSnapshot)) {
            throw new IllegalArgumentException("Entry has already been executed");
        }

        EntryTempSnapshot tempSnapshot = (EntryTempSnapshot) wrapper.entry;

        // Generate IDs
        long id = idCounter.incrementAndGet();
        String type = tempSnapshot.getType();
        long relId = typeRelIdCounters
                .computeIfAbsent(type, k -> new AtomicLong(0))
                .incrementAndGet();

        // Calculate times
        Date executingTime = new Date();
        Date expiryTime = null;
        if (tempSnapshot.getDuration() != null) {
            long expiryMillis = executingTime.getTime() + tempSnapshot.getDuration().toMillis();
            expiryTime = new Date(expiryMillis);
        }

        // Create persistent snapshot and replace wrapper content
        EntrySnapshot snapshot = new EntrySnapshot(
                id, relId, type, tempSnapshot.getTarget(), tempSnapshot.getExecutor(),
                expiryTime, executingTime, tempSnapshot.getReason(),
                tempSnapshot.getData() != null ? new JsonObject().getAsJsonObject() : null,
                null, null, false, null, 0
        );

        // Store in repository
        entries.put(id, snapshot);

        // Transition wrapper to persisted state
        wrapper.entry = snapshot;
    }

    @Override
    public @NotNull List<Entry> query(@NotNull UUID target, @NotNull QueryOptions options) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (target == null) {
            throw new NullPointerException("target cannot be null");
        }
        if (options == null) {
            throw new NullPointerException("options cannot be null");
        }

        List<Entry> result = new ArrayList<>();
        Date now = new Date();

        for (EntrySnapshot snapshot : entries.values()) {
            if (!snapshot.getTarget().equals(target)) {
                continue;
            }

            if (!matchesStateFilter(snapshot, options, now)) {
                continue;
            }

            if (!matchesTypeFilter(snapshot, options)) {
                continue;
            }

            if (!matchesTimeRange(snapshot, options)) {
                continue;
            }

            result.add(createSnapshot(snapshot));
        }

        result.sort((a, b) -> {
            int timeComp = a.getExecutingTime().compareTo(b.getExecutingTime());
            if (timeComp != 0) return timeComp;
            return Long.compare(a.getId(), b.getId());
        });

        return Collections.unmodifiableList(result);
    }

    @Override
    public @NotNull List<Entry> queryAll(@NotNull QueryOptions options, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (options == null) {
            throw new NullPointerException("options cannot be null");
        }
        if (types == null) {
            throw new NullPointerException("types cannot be null");
        }

        Set<String> typeSet = new HashSet<>(Arrays.asList(types));
        List<Entry> result = new ArrayList<>();
        Date now = new Date();

        for (EntrySnapshot snapshot : entries.values()) {
            if (!typeSet.isEmpty() && !typeSet.contains(snapshot.getType())) {
                continue;
            }

            if (!matchesStateFilter(snapshot, options, now)) {
                continue;
            }

            if (!matchesTimeRange(snapshot, options)) {
                continue;
            }

            result.add(createSnapshot(snapshot));
        }

        result.sort((a, b) -> {
            int timeComp = a.getExecutingTime().compareTo(b.getExecutingTime());
            if (timeComp != 0) return timeComp;
            return Long.compare(a.getId(), b.getId());
        });

        return Collections.unmodifiableList(result);
    }

    @Override
    public @NotNull Optional<Entry> queryLatestActive(@NotNull UUID target, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (target == null) {
            throw new NullPointerException("target cannot be null");
        }
        if (types == null) {
            throw new NullPointerException("types cannot be null");
        }
        if (types.length == 0) {
            throw new IllegalArgumentException("types array cannot be empty");
        }

        Set<String> typeSet = new HashSet<>(Arrays.asList(types));
        Date now = new Date();
        EntrySnapshot latest = null;

        for (EntrySnapshot snapshot : entries.values()) {
            if (!snapshot.getTarget().equals(target)) {
                continue;
            }
            if (!typeSet.contains(snapshot.getType())) {
                continue;
            }
            if (!isActive(snapshot, now)) {
                continue;
            }

            if (latest == null || isNewer(snapshot, latest)) {
                latest = snapshot;
            }
        }

        return Optional.ofNullable(latest).map(this::createSnapshot);
    }

    @Override
    public @NotNull Optional<Entry> queryById(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        return Optional.ofNullable(entries.get(id)).map(this::createSnapshot);
    }

    @Override
    public @NotNull Optional<Entry> queryByTypeAndRelId(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }

        return entries.values().stream()
                .filter(s -> s.getType().equals(type) && s.getRelId() == relId)
                .findFirst()
                .map(this::createSnapshot);
    }

    @Override
    public void withdraw(@NotNull Entry entry, @NotNull UUID withdrawnBy, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (entry == null) {
            throw new NullPointerException("entry cannot be null");
        }
        if (withdrawnBy == null) {
            throw new NullPointerException("withdrawnBy cannot be null");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntrySnapshot)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        EntrySnapshot snapshot = (EntrySnapshot) wrapper.entry;
        if (snapshot.isWithdrawn()) {
            throw new IllegalStateException("Entry is already withdrawn");
        }

        EntrySnapshot current = entries.get(snapshot.getId());
        if (current == null) {
            throw new EntryNotFoundException("Entry not found: id=" + snapshot.getId());
        }

        // Update withdrawal info
        current.setWithdrawnBy(withdrawnBy);
        current.setWithdrawReason(withdrawReason);

        // Refresh the wrapper's underlying snapshot
        snapshot.setWithdrawnBy(withdrawnBy);
        snapshot.setWithdrawReason(withdrawReason);
    }

    @Override
    public void updateWithdrawReason(@NotNull Entry entry, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (entry == null) {
            throw new NullPointerException("entry cannot be null");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntrySnapshot)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        EntrySnapshot snapshot = (EntrySnapshot) wrapper.entry;
        if (!snapshot.isWithdrawn()) {
            throw new IllegalStateException("Entry is not withdrawn");
        }

        EntrySnapshot current = entries.get(snapshot.getId());
        if (current == null) {
            throw new EntryNotFoundException("Entry not found: id=" + snapshot.getId());
        }

        current.setWithdrawReason(withdrawReason);
        snapshot.setWithdrawReason(withdrawReason);
    }

    @Override
    public void updateData(@NotNull Entry entry, @NotNull JsonObject newData) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (entry == null) {
            throw new NullPointerException("entry cannot be null");
        }
        if (newData == null) {
            throw new NullPointerException("newData cannot be null");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntrySnapshot)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        EntrySnapshot snapshot = (EntrySnapshot) wrapper.entry;
        if (snapshot.isWithdrawn()) {
            throw new IllegalStateException("Cannot update data on withdrawn entry");
        }

        EntrySnapshot current = entries.get(snapshot.getId());
        if (current == null) {
            throw new EntryNotFoundException("Entry not found: id=" + snapshot.getId());
        }

        // Check version for optimistic locking
        if (snapshot.getVersion() != current.getVersion()) {
            throw new ConcurrentDataModificationException(
                    "Entry was modified concurrently: expected version " + snapshot.getVersion()
                            + " but found " + current.getVersion()
            );
        }

        // Update data and version
        current.setData(newData);
        current.setVersion(current.getVersion() + 1);

        snapshot.setData(newData);
        snapshot.setVersion(current.getVersion());
    }

    @Override
    public void delete(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }

        EntrySnapshot toDelete = entries.values().stream()
                .filter(s -> s.getType().equals(type) && s.getRelId() == relId)
                .findFirst()
                .orElse(null);

        if (toDelete == null) {
            throw new EntryNotFoundException("Entry not found: type=" + type + ", relId=" + relId);
        }

        delete(toDelete.getId());
    }

    @Override
    public void delete(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        EntrySnapshot toDelete = entries.get(id);
        if (toDelete == null) {
            throw new EntryNotFoundException("Entry not found: id=" + id);
        }

        if (toDelete.isWithdrawn()) {
            throw new IllegalStateException("Cannot delete withdrawn entry: id=" + id);
        }

        // Clear references to this entry from other entries
        entries.values().forEach(s -> {
            if (s.getOverriddenBy() != null && s.getOverriddenBy().equals(id)) {
                s.setOverriddenBy(null);
            }
        });

        // Delete the entry
        entries.remove(id);
    }

    // Helper methods

    private boolean matchesStateFilter(EntrySnapshot snapshot, QueryOptions options, Date now) {
        boolean isExpired = snapshot.getExpiryTime() != null && now.getTime() >= snapshot.getExpiryTime().getTime();
        boolean isWithdrawn = snapshot.isWithdrawn();
        boolean isOverridden = snapshot.isOverridden();

        if (isExpired && !options.includeExpired()) return false;
        if (isWithdrawn && !options.includeWithdrawn()) return false;
        if (isOverridden && !options.includeOverridden()) return false;

        return true;
    }

    private boolean matchesTypeFilter(EntrySnapshot snapshot, QueryOptions options) {
        Set<String> types = options.getTypes();
        if (types.isEmpty()) return true;
        return types.contains(snapshot.getType());
    }

    private boolean matchesTimeRange(EntrySnapshot snapshot, QueryOptions options) {
        Date start = options.getStartTime();
        Date end = options.getEndTime();
        Date executingTime = snapshot.getExecutingTime();

        if (start != null && executingTime.getTime() < start.getTime()) return false;
        if (end != null && executingTime.getTime() >= end.getTime()) return false;

        return true;
    }

    private boolean isActive(EntrySnapshot snapshot, Date now) {
        if (snapshot.isWithdrawn()) return false;
        if (snapshot.isOverridden()) return false;
        if (snapshot.getExpiryTime() != null && now.getTime() >= snapshot.getExpiryTime().getTime()) {
            return false;
        }
        return true;
    }

    private boolean isNewer(EntrySnapshot a, EntrySnapshot b) {
        int timeComp = a.getExecutingTime().compareTo(b.getExecutingTime());
        if (timeComp != 0) return timeComp > 0;
        return a.getId() > b.getId();
    }

    private Entry createSnapshot(EntrySnapshot snapshot) {
        // Create a defensive copy for query snapshots
        return new EntrySnapshot(
                snapshot.getId(), snapshot.getRelId(), snapshot.getType(),
                snapshot.getTarget(), snapshot.getExecutor(),
                snapshot.getExpiryTime() != null ? new Date(snapshot.getExpiryTime().getTime()) : null,
                new Date(snapshot.getExecutingTime().getTime()),
                snapshot.getReason(),
                snapshot.getData() != null ? new JsonObject().getAsJsonObject() : null,
                snapshot.getWithdrawnBy(), snapshot.getWithdrawReason(),
                snapshot.isOverridden(), snapshot.getOverriddenBy(), snapshot.getVersion()
        );
    }
}
