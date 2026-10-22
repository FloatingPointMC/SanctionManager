package io.github.floatingpointmc.sanctionmanager.core.config.cache;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for the in-memory cache.
 * <p>
 * The constructor (Lombok-generated, taking {@code entryCapacity} and {@code queryCapacity} in that order)
 * performs no validation. Both capacities must be positive; this is checked when the in-memory cache
 * service is created, which throws {@code IllegalArgumentException} otherwise.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class MemoryCacheConfig {
    /**
     * Maximum number of entries in the entry cache (applies to in-memory cache only).
     */
    private final int entryCapacity;
    /**
     * Maximum number of query results in the query result cache (applies to in-memory cache only).
     */
    private final int queryCapacity;

    public static @NotNull MemoryCacheConfig of(int entryCapacity, int queryCapacity) {
        return new MemoryCacheConfig(entryCapacity, queryCapacity);
    }
}
