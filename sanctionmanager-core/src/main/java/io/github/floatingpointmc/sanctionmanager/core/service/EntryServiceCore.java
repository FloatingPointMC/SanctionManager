package io.github.floatingpointmc.sanctionmanager.core.service;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.EntryNotFoundException;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryWrapperImpl;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.QueryKey;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core implementation of EntryService with comprehensive cache-first query execution.
 * <p>
 * All queries use a cache-first strategy:
 * </p>
 * <ol>
 *   <li>Check cache using a deterministic query key</li>
 *   <li>On cache hit, return cached result without accessing persistent storage</li>
 *   <li>On cache miss, query persistent storage via IStorageService</li>
 *   <li>Populate entry cache and query result cache after successful storage lookup</li>
 *   <li>Return result</li>
 * </ol>
 * <p>
 * Mutations invalidate all query result caches to ensure consistency. Individual entry caches
 * are updated or invalidated as appropriate.
 * </p>
 */
@Slf4j
public class EntryServiceCore implements EntryService {
    private final SanctionManagerCore core;
    private final Map<String, AtomicLong> typeRelIdCounters = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    /**
     * Constructs the entry service for the given Core.
     *
     * @param core the owning Core. Cannot be null.
     * @throws IllegalStateException if the Core is already initialized
     */
    public EntryServiceCore(SanctionManagerCore core) {
        if (core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is already initialized");
        }
        this.core = core;
    }

    /**
     * Restores the per-type relId counters from persistent storage.
     * <p>
     * This must be called after the storage service is initialized to ensure relId uniqueness
     * across service restarts.
     * </p>
     *
     * @param storage the storage service to restore from
     */
    public void restoreRelIdCounters(IStorageService storage) {
        Map<String, Long> restored = storage.getTypeRelIdCounters();
        typeRelIdCounters.clear();
        for (Map.Entry<String, Long> entry : restored.entrySet()) {
            typeRelIdCounters.put(entry.getKey(), new AtomicLong(entry.getValue()));
        }
    }

    /**
     * Persists the current per-type relId counters to storage.
     *
     * @throws IOException if writing the file fails
     */
    private void persistRelIdCounters() throws IOException {
        IStorageService storage = core.getStorageService().getStorageService();
        for (Map.Entry<String, AtomicLong> entry : typeRelIdCounters.entrySet()) {
            storage.updateTypeRelIdCounter(entry.getKey(), entry.getValue().get());
        }
        storage.persistMetadata();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull List<Entry> query(@NotNull UUID target, @NotNull QueryOptions options) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        // Cache-first: construct query key
        QueryKey.TargetQuery queryKey = new QueryKey.TargetQuery(target, options);

        // Check cache and capture generation
        long generation = core.getCacheService().getQueryGeneration();
        Optional<List<EntryPersisted>> cached = core.getCacheService().getQueryResult(queryKey);
        if (cached.isPresent()) {
            // Cache hit - return wrapped entries
            return cached.get().stream()
                    .map(this::wrapWithDefensiveCopy)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Collections::unmodifiableList
                    ));
        }

        // Cache miss - query persistent storage
        Date now = new Date();
        List<EntryPersisted> result = new ArrayList<>();

        try {
            IStorageService storage = core.getStorageService().getStorageService();
            List<EntryPersisted> entries = storage.queryByTarget(target);

            for (EntryPersisted entry : entries) {
                if (matchesStateFilter(entry, options, now)
                        && matchesTypeFilter(entry, options)
                        && matchesTimeRange(entry, options)) {
                    result.add(entry);
                    // Populate entry cache only if not already cached (prevents publishing stale data)
                    if (!core.getCacheService().getById(entry.getId()).isPresent()) {
                        core.getCacheService().put(entry);
                    }
                }
            }

            // Sort results
            result.sort(Comparator.comparing(EntryPersisted::getExecutingTime)
                    .thenComparingLong(EntryPersisted::getId));

            // Populate query result cache only if generation hasn't changed (prevents stale cache after concurrent invalidation)
            core.getCacheService().putQueryResultIfGenerationMatches(queryKey, result, generation);

            return result.stream()
                    .map(this::wrapWithDefensiveCopy)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Collections::unmodifiableList
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to query entries", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull List<Entry> queryAll(@NotNull QueryOptions options, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(options, "options cannot be null");
        Objects.requireNonNull(types, "types cannot be null");

        // Validate types
        Set<String> allowedTypes = new HashSet<>();
        for (String type : types) {
            Objects.requireNonNull(type, "type cannot be null");
            if (type.trim().isEmpty()) {
                throw new IllegalArgumentException("type cannot be empty");
            }
            core.getTypeManager().getType(type);
            allowedTypes.add(type);
        }

        // Cache-first: construct query key
        QueryKey.AllQuery queryKey = new QueryKey.AllQuery(options, types);

        // Check cache and capture generation
        long generation = core.getCacheService().getQueryGeneration();
        Optional<List<EntryPersisted>> cached = core.getCacheService().getQueryResult(queryKey);
        if (cached.isPresent()) {
            // Cache hit - return wrapped entries
            return cached.get().stream()
                    .map(this::wrapWithDefensiveCopy)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Collections::unmodifiableList
                    ));
        }

        // Cache miss - query persistent storage
        Date now = new Date();
        List<EntryPersisted> result = new ArrayList<>();

        try {
            IStorageService storage = core.getStorageService().getStorageService();
            List<EntryPersisted> entries = storage.queryAll();

            for (EntryPersisted entry : entries) {
                if ((allowedTypes.isEmpty() || allowedTypes.contains(entry.getType().getIdentifier()))
                        && matchesStateFilter(entry, options, now)
                        && matchesTypeFilter(entry, options)
                        && matchesTimeRange(entry, options)) {
                    result.add(entry);
                    // Populate entry cache only if not already cached (prevents publishing stale data)
                    if (!core.getCacheService().getById(entry.getId()).isPresent()) {
                        core.getCacheService().put(entry);
                    }
                }
            }

            // Sort results
            result.sort(Comparator.comparing(EntryPersisted::getExecutingTime)
                    .thenComparingLong(EntryPersisted::getId));

            // Populate query result cache only if generation hasn't changed (prevents stale cache after concurrent invalidation)
            core.getCacheService().putQueryResultIfGenerationMatches(queryKey, result, generation);

            return result.stream()
                    .map(this::wrapWithDefensiveCopy)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            Collections::unmodifiableList
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to query all entries", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned entry wraps the cached or stored instance directly; no defensive copy is made.
     * </p>
     */
    @Override
    public @NotNull Optional<Entry> queryLatestActive(@NotNull UUID target, String @NotNull ... types) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(types, "types cannot be null");

        if (types.length == 0) {
            throw new IllegalArgumentException("types array cannot be empty");
        }

        // Validate types
        Set<String> allowedTypes = new HashSet<>();
        for (String type : types) {
            Objects.requireNonNull(type, "type cannot be null");
            if (type.trim().isEmpty()) {
                throw new IllegalArgumentException("type cannot be empty");
            }
            core.getTypeManager().getType(type);
            allowedTypes.add(type);
        }

        // Cache-first: construct query key
        QueryKey.LatestActiveQuery queryKey = new QueryKey.LatestActiveQuery(target, types);

        // Check cache and capture generation
        long generation = core.getCacheService().getQueryGeneration();
        Optional<List<EntryPersisted>> cached = core.getCacheService().getQueryResult(queryKey);
        if (cached.isPresent()) {
            // Cache hit - return the latest (should be single entry or empty)
            List<EntryPersisted> cachedList = cached.get();
            if (cachedList.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new EntryWrapperImpl(cachedList.get(0)));
        }

        // Cache miss - query persistent storage
        Date now = new Date();

        try {
            IStorageService storage = core.getStorageService().getStorageService();
            List<EntryPersisted> entries = storage.queryByTarget(target);

            Optional<EntryPersisted> latest = entries.stream()
                    .filter(e -> allowedTypes.contains(e.getType().getIdentifier()))
                    .filter(e -> !e.isWithdrawn())
                    .filter(e -> !e.isOverridden())
                    .filter(e -> e.getExpiryTime() == null || e.getExpiryTime().after(now))
                    .peek(e -> {
                        // Populate entry cache only if not already cached (prevents publishing stale data)
                        if (!core.getCacheService().getById(e.getId()).isPresent()) {
                            core.getCacheService().put(e);
                        }
                    })
                    .max(Comparator.comparing(EntryPersisted::getExecutingTime)
                            .thenComparingLong(EntryPersisted::getId));

            // Populate query result cache only if generation hasn't changed (prevents stale cache after concurrent invalidation)
            List<EntryPersisted> resultList = latest.map(Collections::singletonList)
                    .orElse(Collections.emptyList());
            core.getCacheService().putQueryResultIfGenerationMatches(queryKey, resultList, generation);

            return latest.map(EntryWrapperImpl::new);
        } catch (IOException e) {
            throw new RuntimeException("Failed to query latest active entry", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned entry wraps the cached or stored instance directly; no defensive copy is made.
     * </p>
     */
    @Override
    public @NotNull Optional<Entry> queryById(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        // Cache-first lookup
        Optional<EntryPersisted> cached = core.getCacheService().getById(id);
        if (cached.isPresent()) {
            return Optional.of(new EntryWrapperImpl(cached.get()));
        }

        // Cache miss, query persistent storage
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            Optional<EntryPersisted> persisted = storage.getRecord(id);

            if (persisted.isPresent()) {
                EntryPersisted entry = persisted.get();
                // Populate entry cache only if not already cached (prevents publishing stale data)
                if (!core.getCacheService().getById(entry.getId()).isPresent()) {
                    core.getCacheService().put(entry);
                }
                return Optional.of(new EntryWrapperImpl(entry));
            }

            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException("Failed to query entry by ID", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned entry wraps the cached or stored instance directly; no defensive copy is made.
     * </p>
     */
    @Override
    public @NotNull Optional<Entry> queryByTypeAndRelId(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(type, "type cannot be null");
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }

        // Validate type is registered
        core.getTypeManager().getType(type);

        // Cache-first lookup
        Optional<EntryPersisted> cached = core.getCacheService().getByTypeAndRelId(type, relId);
        if (cached.isPresent()) {
            return Optional.of(new EntryWrapperImpl(cached.get()));
        }

        // Cache miss, query persistent storage
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            List<EntryPersisted> entries = storage.queryByTypeAndRelId(type, relId);

            if (!entries.isEmpty()) {
                EntryPersisted entry = entries.get(0); // Should be unique
                // Populate entry cache only if not already cached (prevents publishing stale data)
                if (!core.getCacheService().getById(entry.getId()).isPresent()) {
                    core.getCacheService().put(entry);
                }
                return Optional.of(new EntryWrapperImpl(entry));
            }

            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException("Failed to query entry by type and relId", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The relation ID is taken from a per-type counter held in memory by this service instance. The counters
     * start at zero for each new instance and are not restored from storage, so relation IDs may repeat
     * after a restart. The entry ID is assigned by the storage backend.
     * </p>
     */
    @Override
    public void execute(@NotNull EntryWrapper entry) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(entry, "entry cannot be null");

        if (!(entry instanceof EntryWrapperImpl)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        Entry unwrapped = entry.unwrap();
        if (!(unwrapped instanceof EntrySnapshot)) {
            throw new IllegalArgumentException("Entry has already been executed");
        }

        EntrySnapshot tempSnapshot = (EntrySnapshot) unwrapped;

        // Generate temporary IDs (storage may assign different ones)
        long tempId = idCounter.incrementAndGet();
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

        // Create persistent snapshot
        EntryPersisted snapshot = new EntryPersisted(
                tempId, relId, type, tempSnapshot.getTarget(), tempSnapshot.getExecutor(),
                expiryTime, executingTime, tempSnapshot.getReason(),
                tempSnapshot.getDataInternal(),
                null, null, false, null, 0
        );

        // Store in persistent storage
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            long assignedId = storage.insertRecord(snapshot);

            // Update snapshot with actual ID from storage
            snapshot = new EntryPersisted(
                    assignedId, relId, type, tempSnapshot.getTarget(), tempSnapshot.getExecutor(),
                    expiryTime, executingTime, tempSnapshot.getReason(),
                    tempSnapshot.getDataInternal(),
                    null, null, false, null, 0
            );

            // Transition wrapper to persisted state immediately after storage commit
            ((EntryWrapperImpl) entry).entry = snapshot;

            // Best-effort cache update: log failures without failing the operation
            try {
                core.getCacheService().put(snapshot);
                core.getCacheService().invalidateAllQueryResults();
            } catch (Exception cacheEx) {
                // Storage commit succeeded; cache update is best-effort
                log.warn("Cache update failed after successful storage commit (entry ID {})", assignedId, cacheEx);
            }

            // Persist relId counters (best-effort)
            try {
                persistRelIdCounters();
            } catch (IOException metaEx) {
                log.warn("Failed to persist relId counters after successful entry creation (entry ID {})", assignedId, metaEx);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store entry", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The given {@code newData} object is stored without copying, and the entry type's extended data
     * support is not checked.
     * </p>
     */
    @Override
    public void updateData(@NotNull EntryWrapper entry, @NotNull JsonObject newData) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(entry, "entry cannot be null");
        Objects.requireNonNull(newData, "newData cannot be null");

        if (!(entry instanceof EntryWrapperImpl)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapperImpl wrapper = (EntryWrapperImpl) entry;
        if (!(wrapper.entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry has not been executed yet");
        }

        EntryPersisted current = (EntryPersisted) wrapper.entry;

        if (current.isWithdrawn()) {
            throw new IllegalStateException("Entry is withdrawn");
        }

        // Create updated copy without mutating the original
        EntryPersisted updated = new EntryPersisted(
            current.getId(),
            current.getRelId(),
            current.getType(),
            current.getTarget(),
            current.getExecutor(),
            current.getExpiryTime(),
            current.getExecutingTime(),
            current.getReason(),
            newData, // Updated data
            current.getWithdrawnBy(),
            current.getWithdrawalReason(),
            current.isOverridden(),
            current.getOverriddenBy(),
            current.getVersion()
        );

        // Update persistent storage first
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            storage.updateRecord(updated);

            // Update the wrapper so the caller sees the new data
            wrapper.entry = updated;

            // Best-effort cache update after successful storage commit
            try {
                core.getCacheService().invalidateById(updated.getId());
                core.getCacheService().put(updated);
                core.getCacheService().invalidateAllQueryResults();
            } catch (Exception cacheEx) {
                log.warn("Cache update failed after successful data update (entry ID {})", updated.getId(), cacheEx);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update entry data", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void withdraw(@NotNull EntryWrapper entry, @NotNull UUID withdrawnBy, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(entry, "entry cannot be null");
        Objects.requireNonNull(withdrawnBy, "withdrawnBy cannot be null");

        if (!(entry instanceof EntryWrapperImpl)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapperImpl wrapper = (EntryWrapperImpl) entry;
        if (!(wrapper.entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry has not been executed yet");
        }

        EntryPersisted current = (EntryPersisted) wrapper.entry;

        if (current.isWithdrawn()) {
            throw new IllegalStateException("Entry is already withdrawn");
        }

        // Create updated copy without mutating the original
        EntryPersisted updated = new EntryPersisted(
            current.getId(),
            current.getRelId(),
            current.getType(),
            current.getTarget(),
            current.getExecutor(),
            current.getExpiryTime(),
            current.getExecutingTime(),
            current.getReason(),
            current.getDataInternal(),
            withdrawnBy, // Updated withdrawnBy
            withdrawReason, // Updated withdrawReason
            current.isOverridden(),
            current.getOverriddenBy(),
            current.getVersion()
        );

        // Update persistent storage first
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            storage.updateRecord(updated);

            // Best-effort cache update after successful storage commit
            try {
                wrapper.entry = updated;
                core.getCacheService().invalidateById(updated.getId());
                core.getCacheService().put(updated);
                core.getCacheService().invalidateAllQueryResults();
            } catch (Exception cacheEx) {
                log.warn("Cache update failed after successful withdrawal (entry ID {})", updated.getId(), cacheEx);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to withdraw entry", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateWithdrawReason(@NotNull EntryWrapper entry, @Nullable String withdrawReason) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(entry, "entry cannot be null");

        if (!(entry instanceof EntryWrapperImpl)) {
            throw new IllegalArgumentException("Invalid Entry: not created by this Core");
        }

        EntryWrapperImpl wrapper = (EntryWrapperImpl) entry;
        if (!(wrapper.entry instanceof EntryPersisted)) {
            throw new IllegalArgumentException("Entry has not been executed yet");
        }

        EntryPersisted current = (EntryPersisted) wrapper.entry;

        if (!current.isWithdrawn()) {
            throw new IllegalStateException("Entry is not withdrawn");
        }

        // Create updated copy without mutating the original
        EntryPersisted updated = new EntryPersisted(
            current.getId(),
            current.getRelId(),
            current.getType(),
            current.getTarget(),
            current.getExecutor(),
            current.getExpiryTime(),
            current.getExecutingTime(),
            current.getReason(),
            current.getDataInternal(),
            current.getWithdrawnBy(),
            withdrawReason, // Updated withdrawReason
            current.isOverridden(),
            current.getOverriddenBy(),
            current.getVersion()
        );

        // Update persistent storage first
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            storage.updateRecord(updated);

            // Best-effort cache update after successful storage commit
            try {
                wrapper.entry = updated;
                core.getCacheService().invalidateById(updated.getId());
                core.getCacheService().put(updated);
                core.getCacheService().invalidateAllQueryResults();
            } catch (Exception cacheEx) {
                log.warn("Cache update failed after successful withdraw reason update (entry ID {})", updated.getId(), cacheEx);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update withdraw reason", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clearing {@code overriddenBy} references scans all entries in storage.
     * </p>
     */
    @Override
    public void delete(@NotNull String type, long relId) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        Objects.requireNonNull(type, "type cannot be null");
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }

        // Validate type is registered
        core.getTypeManager().getType(type);

        // Find the entry first
        try {
            IStorageService storage = core.getStorageService().getStorageService();
            List<EntryPersisted> entries = storage.queryByTypeAndRelId(type, relId);

            if (entries.isEmpty()) {
                throw new EntryNotFoundException("No entry found with type=" + type + " and relId=" + relId);
            }

            EntryPersisted entry = entries.get(0);
            long deletedId = entry.getId();

            // Physical deletion
            storage.deleteRecord(deletedId);

            // Invalidate entry cache
            core.getCacheService().invalidateById(deletedId);
            core.getCacheService().invalidateByTypeAndRelId(type, relId);

            // Invalidate all query results (deletion affects all queries)
            core.getCacheService().invalidateAllQueryResults();

            // Clear any entries that referenced this one as overriddenBy
            clearOverriddenByReferences(storage, deletedId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete entry", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void delete(long id) {
        if (!core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }

        // Physical deletion from persistent storage
        try {
            IStorageService storage = core.getStorageService().getStorageService();

            // Check if entry exists
            Optional<EntryPersisted> entry = storage.getRecord(id);
            if (!entry.isPresent()) {
                throw new EntryNotFoundException("No entry found with id=" + id);
            }

            EntryPersisted persisted = entry.get();
            if (persisted.isWithdrawn()) {
                throw new EntryNotFoundException("Entry with id=" + id + " is withdrawn");
            }

            storage.deleteRecord(id);

            // Invalidate entry cache
            core.getCacheService().invalidateById(id);
            core.getCacheService().invalidateByTypeAndRelId(
                    persisted.getType().getIdentifier(),
                    persisted.getRelId()
            );

            // Invalidate all query results (deletion affects all queries)
            core.getCacheService().invalidateAllQueryResults();

            // Clear any entries that referenced this one as overriddenBy
            clearOverriddenByReferences(storage, id);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete entry", e);
        }
    }

    // Helper methods

    private void clearOverriddenByReferences(IStorageService storage, long deletedId) throws IOException {
        List<EntryPersisted> allEntries = storage.queryAll();
        for (EntryPersisted e : allEntries) {
            if (e.getOverriddenBy() != null && e.getOverriddenBy() == deletedId) {
                e.setOverriddenBy(null);
                storage.updateRecord(e);
                // Best-effort cache update
                try {
                    core.getCacheService().invalidateById(e.getId());
                    core.getCacheService().put(e);
                } catch (Exception cacheEx) {
                    log.warn("Cache update failed after clearing overriddenBy reference (entry ID {})", e.getId(), cacheEx);
                }
            }
        }
    }

    /**
     * Wraps an EntryPersisted in an EntryWrapperImpl using a defensive copy.
     * <p>
     * This ensures query results are independent snapshots that remain unchanged
     * even if the cached or another wrapper's entry is later mutated.
     * </p>
     *
     * @param entry the entry to wrap
     * @return a new EntryWrapperImpl containing a defensive copy
     */
    private Entry wrapWithDefensiveCopy(EntryPersisted entry) {
        return new EntryWrapperImpl(entry.defensiveCopy());
    }

    private boolean matchesStateFilter(EntryPersisted entry, QueryOptions options, Date now) {
        boolean isExpired = entry.getExpiryTime() != null && entry.getExpiryTime().before(now);
        boolean isWithdrawn = entry.isWithdrawn();
        boolean isOverridden = entry.isOverridden();

        // Active entry: not expired, not withdrawn, not overridden
        boolean isActive = !isExpired && !isWithdrawn && !isOverridden;
        if (isActive) {
            return true;
        }

        // If not active, check if any of the include flags match
        if (isExpired && options.includeExpired()) {
            return true;
        }
        if (isWithdrawn && options.includeWithdrawn()) {
            return true;
        }
        return isOverridden && options.includeOverridden();
    }

    private boolean matchesTypeFilter(EntryPersisted entry, QueryOptions options) {
        if (options.getTypes().isEmpty()) {
            return true;
        }
        return options.getTypes().contains(entry.getType().getIdentifier());
    }

    private boolean matchesTimeRange(EntryPersisted entry, QueryOptions options) {
        Date executingTime = entry.getExecutingTime();

        if (options.getStartTime() != null && executingTime.before(options.getStartTime())) {
            return false;
        }

        return options.getEndTime() == null || executingTime.before(options.getEndTime());
    }
}
