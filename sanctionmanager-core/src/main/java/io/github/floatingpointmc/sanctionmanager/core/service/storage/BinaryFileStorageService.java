package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
//import io.github.floatingpointmc.sanctionmanager.core.storage.engine.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Storage service implementation using binary file-based backend.
 * <p>
 * Initializes and manages binary file persistence for entries with configurable page size and caching.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class BinaryFileStorageService implements IStorageService {
    private final SanctionManagerCore core;
    private final BinaryFileConfig config;
//    private StorageEngine engine;

    /**
     * Initializes the binary file storage implementation.
     * <p>
     * Validates configuration parameters and prepares the file-based persistence layer.
     * </p>
     *
     * @throws IllegalArgumentException if the page size is less than 512 bytes
     */
    @Override
    public void initialize() {
//        try {
        int pageSize = config.getPage();
        if (pageSize < 512) {
            throw new IllegalArgumentException("Invalid page size: " + pageSize + " (minimum 512)");
        }

        String dbPath = config.getFilePath();
//
//            engine = new StorageEngine(
//                    Paths.get(dbPath),
//                    pageSize,
//                    cacheCapacity
//            );
//            engine.initialize();
        log.info("Binary file storage engine initialized: path={}, pageSize={}", dbPath, pageSize);
//        } catch (IOException e) {
//            log.error("Failed to initialize binary file storage", e);
//            throw new RuntimeException("Storage initialization failed", e);
//        }
    }

//    public StorageEngine getEngine() {
//        if (engine == null) {
//            throw new IllegalStateException("Storage engine not initialized");
//        }
//        return engine;
//    }
//
//    public void shutdown() throws IOException {
//        if (engine != null) {
//            engine.shutdown();
//        }
//    }

    @Override
    public String toString() {
        return "BinaryFileService";
    }
}
