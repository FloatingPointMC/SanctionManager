package io.github.floatingpointmc.sanctionmanager.core.repository;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface PunishmentRepository {
    @Nullable Punishment findById(int id);

    @NotNull Collection<Punishment> findByTarget(@NotNull UUID target);

    @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target);

    @NotNull Collection<Punishment> findActiveBansByTarget(@NotNull UUID target);

    @NotNull Collection<Punishment> findActiveMutesByTarget(@NotNull UUID target);

    @NotNull Collection<Punishment> findActiveWarnsByTarget(@NotNull UUID target);

    void save(@NotNull Punishment punishment);

    void update(@NotNull Punishment punishment);

    void delete(int id);
}