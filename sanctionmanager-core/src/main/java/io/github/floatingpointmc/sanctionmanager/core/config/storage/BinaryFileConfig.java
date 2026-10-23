package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Deprecated
public class BinaryFileConfig {
    /**
     * Path to the binary storage file.
     */
    private final @NotNull String filePath;
    /**
     * Page size in bytes for file I/O operations. Must be at least 512; checked when storage is initialized.
     */
    private final int page;

    public static @NotNull BinaryFileConfig of(@NotNull String filePath, int page) {
        return new BinaryFileConfig(filePath, page);
    }
}
