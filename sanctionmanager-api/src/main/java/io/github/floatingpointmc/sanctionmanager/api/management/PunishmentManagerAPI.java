package io.github.floatingpointmc.sanctionmanager.api.management;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface PunishmentManagerAPI {
    @Nullable Punishment queryPunishment(int id);

    @NotNull Collection<Punishment> queryActivePunishments(@NotNull UUID target);

    @Nullable Punishment queryActiveBan(@NotNull UUID target);

    @Nullable Punishment queryActiveMute(@NotNull UUID target);

    void addPunishment(@NotNull Punishment punishment);

    void withdrawPunishment(int id, @Nullable UUID withdrawnBy);

    void removePunishment(int id);
}
