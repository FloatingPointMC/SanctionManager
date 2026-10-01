package io.github.floatingpointmc.sanctionmanager.api.punishment;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface PunishmentFactory {
    Punishment.Builder create(@NotNull UUID target, @NotNull Type type);
}
