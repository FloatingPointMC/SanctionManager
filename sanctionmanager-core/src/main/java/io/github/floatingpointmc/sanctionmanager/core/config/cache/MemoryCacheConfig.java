package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class MemoryCacheConfig {
    /** Maximum number of entries in the entry cache (applies to in-memory cache only). */
    private final int entryCapacity;
    /** Maximum number of query results in the query result cache (applies to in-memory cache only). */
    private final int queryCapacity;
}
