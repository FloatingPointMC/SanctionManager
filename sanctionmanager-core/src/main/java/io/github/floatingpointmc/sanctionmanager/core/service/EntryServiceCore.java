package io.github.floatingpointmc.sanctionmanager.core.service;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
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
public class EntryServiceCore implements EntryService {
    private final SanctionManagerCore core;
    private final Map<String, AtomicLong> typeRelIdCounters = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public EntryServiceCore(SanctionManagerCore core) {
        if (core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is already initialized");
        }
        this.core = core;
    }

    @Override
    public void execute(@NotNull Entry entry) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntrySnapshot)) {
            throw new IllegalArgumentException("Entry has already been executed");
        }

        EntrySnapshot tempSnapshot = (EntrySnapshot) wrapper.entry;

        // Generate IDs
        long id = idCounter.incrementAndGet();
        EntryType type = tempSnapshot.getType();
        long relId = typeRelIdCounters
                .computeIfAbsent(type.getIdentifier(), k -> new AtomicLong(0))
                .incrementAndGet();

        // Calculate times
        Date executingTime = new Date();
        Date expiryTime = null;
        if (tempSnapshot.getDuration() != null) {
            long expiryMillis = executingTime.getTime() + tempSnapshot.getDuration().toMillis();
            expiryTime = new Date(expiryMillis);
        }

        // Create persistent snapshot and replace wrapper content
        EntryPersisted snapshot = new EntryPersisted(
                id, relId, type, tempSnapshot.getTarget(), tempSnapshot.getExecutor(),
                expiryTime, executingTime, tempSnapshot.getReason(),
                tempSnapshot.getDataInternal() == null ? null : tempSnapshot.getData(),
                null, null, false, null, 0
        );

        // todo: Store in repository

        // Transition wrapper to persisted state
        wrapper.entry = snapshot;
    }

    @Override
    public @NotNull List<Entry> query(@NotNull UUID target, @NotNull QueryOptions options) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        List<Entry> result = new ArrayList<>();
        Date now = new Date();

        // todo: query from cache if enabled, then the persistence

        result.sort(Comparator.comparing(Entry::getExecutingTime).thenComparingLong(Entry::getId));

        return Collections.unmodifiableList(result);
    }

    @Override
    public @NotNull List<Entry> queryAll(@NotNull QueryOptions options, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Set<String> typeSet = new HashSet<>(Arrays.asList(types));
        List<Entry> result = new ArrayList<>();
        Date now = new Date();

        // todo: query from cache if enabled, then the persistence

        result.sort(Comparator.comparing(Entry::getExecutingTime).thenComparingLong(Entry::getId));

        return Collections.unmodifiableList(result);
    }

    @Override
    public @NotNull Optional<Entry> queryLatestActive(@NotNull UUID target, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (types.length == 0) {
            throw new IllegalArgumentException("types array cannot be empty");
        }

        Set<String> typeSet = new HashSet<>(Arrays.asList(types));
        Date now = new Date();
        EntryPersisted latest = null;

        // todo: query from cache if enabled, then the persistence


        return Optional.ofNullable(latest).map(this::createSnapshot);
    }

    @Override
    public @NotNull Optional<Entry> queryById(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }


        // todo: query from cache if enabled, then the persistence
        return Optional.empty();
    }

    @Override
    public @NotNull Optional<Entry> queryByTypeAndRelId(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        // todo: query from cache if enabled, then the persistence
        return Optional.empty();
    }

    @Override
    public void withdraw(@NotNull Entry entry, @NotNull UUID withdrawnBy, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        while (entry instanceof EntryWrapper) {
            entry = ((EntryWrapper) entry).entry;
        }

        if (!(entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        // todo: write changes
    }

    @Override
    public void updateWithdrawReason(@NotNull Entry entry, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        EntryPersisted snapshot = (EntryPersisted) wrapper.entry;
        if (!snapshot.isWithdrawn()) {
            throw new IllegalStateException("Entry is not withdrawn");
        }

        // todo: write changes
    }

    @Override
    public void updateData(@NotNull Entry entry, @NotNull JsonObject newData) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        if (!(entry instanceof EntryWrapper)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapper wrapper = (EntryWrapper) entry;
        if (!(wrapper.entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry is not persisted");
        }

        EntryPersisted snapshot = (EntryPersisted) wrapper.entry;
        if (snapshot.isWithdrawn()) {
            throw new IllegalStateException("Cannot update data on withdrawn entry");
        }

        // todo: write changes

    }

    @Override
    public void delete(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        // todo: write changes
    }

    @Override
    public void delete(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        // todo: write changes
    }

    // Helper methods

    private boolean matchesStateFilter(EntryPersisted snapshot, QueryOptions options, Date now) {
        boolean isExpired = snapshot.getExpiryTime() != null && now.getTime() >= snapshot.getExpiryTime().getTime();
        boolean isWithdrawn = snapshot.isWithdrawn();
        boolean isOverridden = snapshot.isOverridden();

        if (isExpired && !options.includeExpired()) return false;
        if (isWithdrawn && !options.includeWithdrawn()) return false;
        return !isOverridden || options.includeOverridden();
    }

    private boolean matchesTypeFilter(EntryPersisted snapshot, QueryOptions options) {
        Set<String> types = options.getTypes();
        if (types.isEmpty()) return true;
        return types.contains(snapshot.getType().getIdentifier());
    }

    private boolean matchesTimeRange(EntryPersisted snapshot, QueryOptions options) {
        Date start = options.getStartTime();
        Date end = options.getEndTime();
        Date executingTime = snapshot.getExecutingTime();

        if (start != null && executingTime.getTime() < start.getTime()) return false;
        return end == null || executingTime.getTime() < end.getTime();
    }

    private boolean isActive(EntryPersisted snapshot, Date now) {
        if (snapshot.isWithdrawn()) return false;
        if (snapshot.isOverridden()) return false;
        return snapshot.getExpiryTime() == null || now.getTime() < snapshot.getExpiryTime().getTime();
    }

    private boolean isNewer(EntryPersisted a, EntryPersisted b) {
        int timeComp = a.getExecutingTime().compareTo(b.getExecutingTime());
        if (timeComp != 0) return timeComp > 0;
        return a.getId() > b.getId();
    }

    private Entry createSnapshot(EntryPersisted snapshot) {
        // Create a defensive copy for query snapshots
        return new EntryPersisted(
                snapshot.getId(), snapshot.getRelId(), snapshot.getType(),
                snapshot.getTarget(), snapshot.getExecutor(),
                snapshot.getExpiryTime() != null ? new Date(snapshot.getExpiryTime().getTime()) : null,
                new Date(snapshot.getExecutingTime().getTime()),
                snapshot.getReason(),
                snapshot.getData().deepCopy(),
                snapshot.getWithdrawnBy(), snapshot.getWithdrawalReason(),
                snapshot.isOverridden(), snapshot.getOverriddenBy(), snapshot.getVersion()
        );
    }
}
