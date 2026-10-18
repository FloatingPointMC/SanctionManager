package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.core.config.cache.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Redis-backed cache service implementation.
 * <p>
 * Currently not implemented - throws UnsupportedOperationException for all operations.
 * </p>
 */
@RequiredArgsConstructor
public class RedisCacheService implements ICacheService {
    private final @NotNull RedisConfig config;

    @Override
    public @NotNull Optional<EntryPersisted> getById(long id) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void put(@NotNull EntryPersisted entry) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void invalidateById(long id) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void invalidateByTypeAndRelId(@NotNull String typeId, long relId) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void invalidateQueryResult(@NotNull QueryKey key) {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void invalidateAll() {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }

    @Override
    public void invalidateAllQueryResults() {
        throw new UnsupportedOperationException("Redis cache not implemented");
    }
}
