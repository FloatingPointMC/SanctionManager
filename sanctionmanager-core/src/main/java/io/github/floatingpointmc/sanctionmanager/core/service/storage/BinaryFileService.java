package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
//import io.github.floatingpointmc.sanctionmanager.core.storage.engine.StorageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class BinaryFileService implements IStorageService {
    private final SanctionManagerCore core;
    private final BinaryFileConfig config;
//    private StorageEngine engine;

    @Override
    public void initialize() {
//        try {
            int pageSize = config.getPage();
            if (pageSize < 512) {
                throw new IllegalArgumentException("Invalid page size: " + pageSize + " (minimum 512)");
            }

            String dbPath = config.getFilePath();
            int cacheCapacity = config.getCacheCapacity();
//
//            engine = new StorageEngine(
//                    Paths.get(dbPath),
//                    pageSize,
//                    cacheCapacity
//            );
//            engine.initialize();
            log.info("Binary file storage engine initialized: path={}, pageSize={}, cacheCapacity={}", dbPath, pageSize, cacheCapacity);
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

