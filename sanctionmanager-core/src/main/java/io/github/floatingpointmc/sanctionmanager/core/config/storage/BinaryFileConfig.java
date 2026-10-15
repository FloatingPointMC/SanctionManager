package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class BinaryFileConfig {
    private final String filePath;
    private final int page;
    private final int cacheCapacity;

    public BinaryFileConfig(String filePath, int page) {
        this(filePath, page, 1024); // Default cache: 1024 pages
    }
}
