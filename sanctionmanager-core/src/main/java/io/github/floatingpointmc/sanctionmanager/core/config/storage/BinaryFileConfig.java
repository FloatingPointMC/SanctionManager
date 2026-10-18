package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Configuration for binary file-backed storage.
 * <p>
 * Specifies the file path and page size for binary file persistence.
 * </p>
 * <p>
 * The constructor (Lombok-generated, taking {@code filePath} and {@code page} in that order) performs no
 * validation. The page size is validated when storage is initialized.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public class BinaryFileConfig {
    /** Path to the binary storage file. */
    private final String filePath;
    /** Page size in bytes for file I/O operations. Must be at least 512; checked when storage is initialized. */
    private final int page;
}
