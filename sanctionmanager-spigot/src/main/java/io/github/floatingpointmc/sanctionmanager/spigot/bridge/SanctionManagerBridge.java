package io.github.floatingpointmc.sanctionmanager.spigot.bridge;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.PunishmentFactory;
import org.jetbrains.annotations.NotNull;

public class SanctionManagerBridge implements SanctionManager {
    @Override
    public @NotNull PunishmentManagerAPI getPunishManager() {
        throw new UnsupportedOperationException("Operation is not supported under bridge mode.");
    }

    @Override
    public @NotNull PunishmentFactory getPunishmentFactory() {
        throw new UnsupportedOperationException("Operation is not supported under bridge mode.");
    }
}