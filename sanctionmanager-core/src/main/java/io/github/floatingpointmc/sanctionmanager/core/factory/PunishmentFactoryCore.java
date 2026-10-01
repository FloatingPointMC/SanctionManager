package io.github.floatingpointmc.sanctionmanager.core.factory;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.PunishmentFactory;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PunishmentFactoryCore implements PunishmentFactory {
    @Override
    public Punishment.Builder create(@NotNull UUID target, @NotNull Type type) {
        return new PunishmentBuilder(target, type);
    }
}
