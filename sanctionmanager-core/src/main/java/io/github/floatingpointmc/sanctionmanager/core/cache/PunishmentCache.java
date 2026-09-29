package io.github.floatingpointmc.sanctionmanager.core.cache;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentCache {
    @Nullable Punishment findById(int id);

    @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target);

    void put(@NotNull Punishment punishment);

    void invalidate(int id);

    void invalidateByTarget(@NotNull UUID target);

    /**
     * Find active punishment by target and type.
     * Returns Optional.empty() if not cached yet (cache miss).
     * Returns Optional with null if cached as "no active punishment".
     * Returns Optional with Punishment if cached active punishment exists.
     */
    @NotNull Optional<Punishment> findActiveByTargetAndType(@NotNull UUID target, @NotNull Type type);

    /**
     * Cache the active punishment result for a specific target and type.
     * Passing null punishment means caching "no active punishment" (negative cache).
     */
    void putActiveByTargetAndType(@NotNull UUID target, @NotNull Type type, @Nullable Punishment punishment);

    /**
     * Invalidate cached active punishment for specific target and type.
     */
    void invalidateByTargetAndType(@NotNull UUID target, @NotNull Type type);
}
