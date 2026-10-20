package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.storage.engine.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage service implementation using binary file-based backend.
 * <p>
 * Initializes and manages binary file persistence for entries through a {@link StorageEngine}.
 * The page size is configurable; the engine's page cache size is currently fixed at 100 pages.
 * </p>
 * <p>
 * All record operations throw {@code IllegalStateException} if {@link #initialize()} has not completed
 * successfully. The constructor is Lombok-generated and only stores the Core and configuration.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class BinaryFileStorageService implements IStorageService {
    private final SanctionManagerCore core;
    private final BinaryFileConfig config;
    private StorageEngine engine;

    /**
     * Initializes the binary file storage implementation.
     * <p>
     * Validates configuration parameters and prepares the file-based persistence layer.
     * </p>
     *
     * @throws IllegalArgumentException if the page size is less than 512 bytes
     * @throws IllegalStateException if the Core's type manager is not yet available
     * @throws RuntimeException if opening or creating the storage file fails
     */
    @Override
    public void initialize() {
        try {
            int pageSize = config.getPage();
            if (pageSize < 512) {
                throw new IllegalArgumentException("Invalid page size: " + pageSize + " (minimum 512)");
            }

            String dbPath = config.getFilePath();
            int maxCachedPages = 100; // Configurable in future

            engine = new StorageEngine(
                    Paths.get(dbPath),
                    pageSize,
                    maxCachedPages,
                    core.getTypeManager()
            );
            engine.initialize();

            log.info("Binary file storage engine initialized: path={}, pageSize={}", dbPath, pageSize);
        } catch (IOException e) {
            log.error("Failed to initialize binary file storage", e);
            throw new RuntimeException("Storage initialization failed", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link StorageEngine#insertRecord(EntrySnapshot)}, which assigns a new ID; the ID of
     * the given entry is ignored.
     * </p>
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public Addition insertRecord(EntrySnapshot entry) throws IOException {
        ensureInitialized();
        return engine.insertRecord(entry);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        ensureInitialized();
        return engine.getRecord(id);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Records that cannot be read are logged and omitted from the result.
     * </p>
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
        ensureInitialized();
        return engine.queryByTarget(target);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns at most one entry. If the record cannot be read, the failure is logged and an empty list is returned.
     * </p>
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
        ensureInitialized();
        return engine.queryByTypeAndRelId(typeId, relId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads every record in the primary index. Unlike {@link #queryByTarget(UUID)}, read failures propagate.
     * </p>
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public List<EntryPersisted> queryAll() throws IOException {
        ensureInitialized();
        List<EntryPersisted> result = new ArrayList<>();
        for (Long id : engine.getPrimaryIndex().keySet()) {
            Optional<EntryPersisted> entry = engine.getRecord(id);
            entry.ifPresent(result::add);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if this service is not initialized
     * @throws io.github.floatingpointmc.sanctionmanager.core.storage.engine.StorageException if no record
     *         with the entry's ID exists
     */
    @Override
    public void updateRecord(EntryPersisted entry) throws IOException {
        ensureInitialized();
        engine.updateRecord(entry);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Does nothing if no record with the given ID exists.
     * </p>
     *
     * @throws IllegalStateException if this service is not initialized
     */
    @Override
    public void deleteRecord(long id) throws IOException {
        ensureInitialized();
        engine.deleteRecord(id);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Does nothing if the engine was never initialized.
     * </p>
     */
    @Override
    public void shutdown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** {@inheritDoc} */
    @Override
    public java.util.Map<String, Long> getTypeRelIdCounters() {
        ensureInitialized();
        return engine.getTypeRelIdCounters();
    }

    /** {@inheritDoc} */
    @Override
    public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
        ensureInitialized();
        engine.updateTypeRelIdCounter(typeIdentifier, nextRelId);
    }

    /** {@inheritDoc} */
    @Override
    public void persistMetadata() throws IOException {
        ensureInitialized();
        engine.persistMetadata();
    }

    private void ensureInitialized() {
        if (engine == null) {
            throw new IllegalStateException("Storage engine not initialized");
        }
    }

    @Override
    public String toString() {
        return "BinaryFileService";
    }
}

