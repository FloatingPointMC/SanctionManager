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
 * {@code EntryService} provides methods for persisting new entries, querying existing entries,
 * and modifying entry state (withdrawal, data updates, deletion).
 * </p>
 * <p>
 * <strong>Core Initialization:</strong> All operations throw {@code IllegalStateException} if the Core
 * is not initialized or has been shut down.
 * </p>
 * <p>
 * <strong>Concurrency:</strong> This interface does not define a concurrency contract. Optimistic locking
 * and {@link io.github.floatingpointmc.sanctionmanager.api.exceptions.ConcurrentDataModificationException}
 * are reserved for future use; the current Core implementation does not perform version checks and does not
 * throw that exception.
 * </p>
 * <p>
 * <strong>Type validation:</strong> Type identifiers passed to query and delete methods are checked for
 * {@code null} and blank values. The current Core implementation does not reject identifiers that are not
 * registered; such queries simply match no entries.
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
     * @param types   the types to include in the query. Each type must be non-null and non-blank.
     *                Duplicate types are automatically deduplicated. An empty array applies no
     *                varargs type restriction; any types set in {@code options} still apply.
     * @return an immutable list of matching entries, possibly empty
     * @throws NullPointerException if {@code options}, {@code types}, or any type is null
     * @throws IllegalArgumentException if any type is blank
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
     * @param types  the types to check. Each must be non-null and non-blank. Duplicates are deduplicated.
     * @return the latest active entry, or {@code Optional.empty()} if none exist
     * @throws NullPointerException if {@code target}, {@code types}, or any type is null
     * @throws IllegalArgumentException if any type is blank, or if the types array is empty
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
     * @param type  the entry type. Cannot be null or blank.
     * @param relId the relation ID
     * @return the entry, or {@code Optional.empty()} if not found
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is blank
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull Optional<Entry> queryByTypeAndRelId(@NotNull String type, long relId);

    /**
     * Executes (persists) a pending entry.
     * <p>
     * This method persists the entry to the configured storage and assigns it a unique ID and relation ID.
     * Upon successful persistence, the entry object transitions to a persisted state. The original
     * wrapper object continues to be used and its fields become accessible.
     * </p>
     * <p>
     * <strong>Overriding:</strong> Automatic overriding of previous entries is reserved for future use.
     * The current Core implementation does not mark any existing entry as overridden when a new entry is
     * executed, and does not provide transactional atomicity across multiple records.
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
     * The provided {@code newData} must be a complete JSON object (not a patch); it replaces the
     * existing data. If storage is updated successfully, the given {@code entry} object reflects the new data.
     * No optimistic locking is performed by the current Core implementation.
     * </p>
     *
     * @param entry   the Entry to update. Must be a persisted entry from this Core's queries and must not be withdrawn.
     * @param newData the new extended data as a JSON object. Cannot be null. {@code {}} clears all extended data.
     * @throws NullPointerException if {@code entry} or {@code newData} is null
     * @throws IllegalArgumentException if {@code entry} was not created by this Core or is not persisted
     * @throws IllegalStateException if the entry is withdrawn, or if the Core is not initialized or has been shut down
     * @throws RuntimeException if the storage operation fails
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
     * @throws RuntimeException if the storage operation fails, including when the entry no longer exists in storage
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
     * @throws RuntimeException if the storage operation fails, including when the entry no longer exists in storage
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
     * @param type  the entry type. Cannot be null or blank.
     * @param relId the relation ID
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is blank
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
