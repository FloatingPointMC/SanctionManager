package io.github.floatingpointmc.sanctionmanager.api.service;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.EntryNotFoundException;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for managing persisted sanction entries.
 * <p>
 * {@code EntryManager} provides methods for persisting new entries, querying existing entries,
 * and modifying entry state (withdrawal, data updates, deletion).
 * </p>
 * <p>
 * <strong>Core Initialization:</strong> All operations throw {@code IllegalStateException} if the Core
 * is not initialized or has been shut down.
 * </p>
 * <p>
 * <strong>Concurrency:</strong> Multiple threads may call {@code EntryManager} methods concurrently.
 * For mutating operations on the same Entry, optimistic locking ensures consistency. Conflicts are reported
 * via {@code ConcurrentDataModificationException} (for data conflicts) or other appropriate exceptions.
 * </p>
 */
public interface EntryService {
    /**
     * Queries entries for a specific target.
     * <p>
     * Results are sorted by {@code executingTime ASC, id ASC}. The returned list is immutable and consists
     * of independent query snapshots. Multiple calls do not guarantee the same Java object instances.
     * </p>
     *
     * @param target  the target UUID to query. Cannot be null.
     * @param options the query options controlling state filtering and other criteria. Cannot be null.
     * @return an immutable list of matching entries, possibly empty
     * @throws NullPointerException if {@code target} or {@code options} is null
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull List<Entry> query(@NotNull UUID target, @NotNull QueryOptions options);

    /**
     * Queries all entries without restricting by target.
     * <p>
     * Results are sorted by {@code executingTime ASC, id ASC}. The returned list is immutable and consists
     * of independent query snapshots.
     * </p>
     *
     * @param options the query options controlling state filtering and other criteria. Cannot be null.
     * @param types   the types to include in the query. Each type must be non-empty and registered.
     *                Duplicate types are automatically deduplicated.
     * @return an immutable list of matching entries, possibly empty
     * @throws NullPointerException if {@code options} or any type is null
     * @throws IllegalArgumentException if any type is empty, unregistered, or has invalid format
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull List<Entry> queryAll(@NotNull QueryOptions options, String @NotNull ... types);

    /**
     * Queries the latest active entry for a target among specified types.
     * <p>
     * An entry is active if it is not expired, not withdrawn, and not permanently overridden.
     * Among multiple matching entries, the one with the latest {@code executingTime} is selected
     * (ties broken by largest {@code id}).
     * </p>
     *
     * @param target the target UUID. Cannot be null.
     * @param types  the types to check. Each must be non-empty and registered. Duplicates are deduplicated.
     * @return the latest active entry, or {@code Optional.empty()} if none exist
     * @throws NullPointerException if {@code target} or any type is null
     * @throws IllegalArgumentException if any type is empty, unregistered, or has invalid format, or if types array is empty
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull Optional<Entry> queryLatestActive(@NotNull UUID target, String @NotNull ... types);

    /**
     * Queries an entry by its unique ID.
     * <p>
     * Returns the entry regardless of its state (expired, withdrawn, overridden, etc.).
     * </p>
     *
     * @param id the entry ID
     * @return the entry, or {@code Optional.empty()} if not found
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull Optional<Entry> queryById(long id);

    /**
     * Queries an entry by type and relation ID.
     * <p>
     * Returns the entry regardless of its state.
     * </p>
     *
     * @param type  the entry type. Cannot be null, must be non-empty and registered.
     * @param relId the relation ID
     * @return the entry, or {@code Optional.empty()} if not found
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is empty, unregistered, or has invalid format
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull Optional<Entry> queryByTypeAndRelId(@NotNull String type, long relId);

    /**
     * Executes (persists) a pending entry.
     * <p>
     * This method persists the entry to the database and assigns it a unique ID and relation ID.
     * Upon successful persistence, the entry object transitions to a persisted state. The original
     * wrapper object continues to be used and its fields become accessible.
     * </p>
     * <p>
     * <strong>Overriding:</strong> If the new entry is not expired, it may override previous Active entries
     * of compatible types, according to application-specific override rules. Overridden entries are marked
     * with the permanent {@code OVERRIDDEN} flag. All database changes occur atomically.
     * </p>
     *
     * @param entry the pending Entry to persist. Must have been created via
     * {@link io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory#create(String, UUID, UUID, String, Duration)}
     *              or
     *              {@link io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory#create(String, UUID, UUID, String, Duration, JsonObject)}
     * @throws NullPointerException if {@code entry} is null
     * @throws IllegalArgumentException if {@code entry} was not created by this Core's EntryFactory or has already been persisted
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     * @throws RuntimeException if persistence or transaction operations fail; see specific subclasses for details
     */
    void execute(@NotNull Entry entry);

    /**
     * Updates the extended data of an entry.
     * <p>
     * The provided {@code newData} must be a complete JSON object (not a patch).
     * Modifications to the returned data use optimistic locking: if another thread modifies the entry
     * concurrently, a {@code ConcurrentDataModificationException} is thrown and the original Entry remains unchanged.
     * </p>
     *
     * @param entry   the Entry to update. Must be a persisted entry from this Core's queries and must not be withdrawn.
     * @param newData the new extended data as a JSON object. Cannot be null. {@code {}} clears all extended data.
     *                Top-level JSON null, arrays, and scalars are rejected.
     * @throws NullPointerException if {@code entry} or {@code newData} is null
     * @throws IllegalArgumentException if {@code entry} was not created by this Core or is not persisted, or if {@code newData} is not a valid JSON object
     * @throws IllegalStateException if the entry is withdrawn, extended data is not enabled for this type,
     *                                or if the Core is not initialized or has been shut down
     * @throws EntryNotFoundException if the entry has been physically deleted from the database
     * @throws RuntimeException if persistence, transaction, or optimistic locking operations fail
     */
    void updateData(@NotNull Entry entry, @NotNull JsonObject newData);

    /**
     * Withdraws a persisted entry.
     * <p>
     * Marks the entry as withdrawn by the specified player or system. A withdrawn entry is no longer active
     * and cannot be further modified except for updating the withdrawal reason.
     * </p>
     *
     * @param entry           the Entry to withdraw. Must be a persisted entry from this Core's queries.
     * @param withdrawnBy     the UUID of the player or system withdrawing the entry. Cannot be null.
     * @param withdrawReason  the reason for withdrawal. May be null.
     * @throws NullPointerException if {@code entry} or {@code withdrawnBy} is null
     * @throws IllegalArgumentException if {@code entry} was not created by this Core or is not persisted
     * @throws IllegalStateException if the entry has already been withdrawn, or if the Core is not initialized or has been shut down
     * @throws EntryNotFoundException if the entry has been physically deleted from the database
     * @throws RuntimeException if persistence or transaction operations fail
     */
    void withdraw(@NotNull Entry entry, @NotNull UUID withdrawnBy, @Nullable String withdrawReason);

    /**
     * Updates the withdrawal reason for an already-withdrawn entry.
     * <p>
     * Only the withdrawal reason is modified. All other fields remain unchanged.
     * </p>
     *
     * @param entry           the withdrawn Entry. Must be a persisted entry from this Core's queries and must already be withdrawn.
     * @param withdrawReason  the new reason for withdrawal. May be null to clear the reason.
     * @throws NullPointerException if {@code entry} is null
     * @throws IllegalArgumentException if {@code entry} was not created by this Core or is not persisted
     * @throws IllegalStateException if the entry has not been withdrawn, or if the Core is not initialized or has been shut down
     * @throws EntryNotFoundException if the entry has been physically deleted from the database
     * @throws RuntimeException if persistence or transaction operations fail
     */
    void updateWithdrawReason(@NotNull Entry entry, @Nullable String withdrawReason);

    /**
     * Deletes an entry by type and relation ID.
     * <p>
     * Performs physical deletion, not soft-delete or withdrawal. Deletes the entry and clears any other entries'
     * references to this entry's ID (e.g., {@code overriddenBy}), but does not restore overridden entries to
     * active status or reconstruct override chains.
     * </p>
     *
     * @param type  the entry type. Cannot be null, must be non-empty and registered.
     * @param relId the relation ID
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is empty, unregistered, or has invalid format
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     * @throws EntryNotFoundException if no entry with the given type and relation ID exists
     * @throws RuntimeException if deletion or transaction operations fail
     */
    void delete(@NotNull String type, long relId);

    /**
     * Deletes an entry by its unique ID.
     * <p>
     * Performs physical deletion. Clears any references to this entry and leaves permanent overridden flags unchanged.
     * </p>
     *
     * @param id the entry ID
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     * @throws EntryNotFoundException if no entry with the given ID exists or if the entry is withdrawn
     * @throws RuntimeException if deletion or transaction operations fail
     */
    void delete(long id);
}
