package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Configuration for binary file-backed storage.
 * <p>
 * Specifies file path, page size, and in-memory cache capacity for binary file persistence.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public class BinaryFileConfig {
    /** Path to the binary storage file. */
    private final String filePath;
    /** Page size in bytes for file I/O operations. */
    private final int page;
}
