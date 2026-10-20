package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for storage layer implementations.
 * <p>
 * Concrete implementations handle persistence via binary file storage; the database implementation
 * is not yet implemented. Methods may throw {@code IOException} for storage-level errors, and
 * implementations may also throw unchecked exceptions (for example
 * {@link io.github.floatingpointmc.sanctionmanager.core.storage.engine.StorageException}, which extends
 * {@code RuntimeException}, or {@code UnsupportedOperationException}).
 * </p>
 */
public interface IStorageService {
    /**
     * Initializes the storage implementation.
     * <p>
     * This method sets up the persistence layer, such as opening file resources.
     * Implementations report failures with unchecked exceptions.
     * </p>
     */
    void initialize();

    /**
     * Inserts a new entry record into persistent storage.
     *
     * @param entry the entry to insert
     * @return the assigned ID for the entry
     * @throws IOException if storage operation fails
     */
    @NotNull Addition insertRecord(@NotNull EntrySnapshot entry, @NotNull Date executingTime, @Nullable Date expiryTime) throws IOException;

    /**
     * Retrieves an entry by its ID.
     *
     * @param id the entry ID
     * @return an Optional containing the entry if found, or empty if not found
     * @throws IOException if storage operation fails
     */
    Optional<EntryPersisted> getRecord(long id) throws IOException;

    /**
     * Queries all entries for a given target UUID.
     *
     * @param target the target UUID
     * @return a list of matching entries (may be empty)
     * @throws IOException if storage operation fails
     */
    List<EntryPersisted> queryByTarget(UUID target) throws IOException;

    /**
     * Queries entries by type identifier and related ID.
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     * @return a list of matching entries (may be empty)
     * @throws IOException if storage operation fails
     */
    List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException;

    /**
     * Queries all entries in storage.
     * <p>
     * This is an expensive operation and should be used sparingly.
     * </p>
     *
     * @return a list of all entries (may be empty)
     * @throws IOException if storage operation fails
     */
    List<EntryPersisted> queryAll() throws IOException;

    /**
     * Updates an existing entry record in persistent storage.
     *
     * @param entry the entry to update
     * @throws IOException if storage operation fails
     */
    void updateRecord(EntryPersisted entry) throws IOException;

    /**
     * Deletes an entry by its ID.
     *
     * @param id the entry ID to delete
     * @throws IOException if storage operation fails
     */
    void deleteRecord(long id) throws IOException;

    /**
     * Shuts down the storage implementation, releasing all resources.
     *
     * @throws IOException if shutdown fails
     */
    void shutdown() throws IOException;

    /**
     * Gets a copy of the per-type relId counters from persistent storage.
     * <p>
     * This provides the highest relId assigned for each type, allowing the service layer
     * to restore and continue the sequence after a restart.
     * </p>
     *
     * @return a map from type identifier to next relId value
     */
    java.util.Map<String, Long> getTypeRelIdCounters();

    /**
     * Updates the per-type relId counter for a given type.
     *
     * @param typeIdentifier the type identifier
     * @param nextRelId      the next relId value to assign for this type
     */
    void updateTypeRelIdCounter(String typeIdentifier, long nextRelId);

    /**
     * Persists the current metadata (type mappings and relId counters) to disk.
     *
     * @throws IOException if writing the file fails
     */
    void persistMetadata() throws IOException;
}
