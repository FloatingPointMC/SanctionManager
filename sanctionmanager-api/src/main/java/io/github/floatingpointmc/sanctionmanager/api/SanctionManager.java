package io.github.floatingpointmc.sanctionmanager.api;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.PunishmentFactory;
import org.jetbrains.annotations.NotNull;

public interface SanctionManager {
    @NotNull PunishmentManagerAPI getPunishManager();

    @NotNull PunishmentFactory getPunishmentFactory();
}
