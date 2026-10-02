package io.github.floatingpointmc.sanctionmanager.core.management;

import io.github.floatingpointmc.sanctionmanager.api.events.PunishmentExecuteEvent;
import io.github.floatingpointmc.sanctionmanager.api.events.PunishmentRemoveEvent;
import io.github.floatingpointmc.sanctionmanager.api.events.PunishmentWithdrawEvent;
import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.core.service.PunishmentService;
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public class PunishmentManager implements PunishmentManagerAPI {
    private final @NotNull PunishmentService service;

    public PunishmentManager(@NotNull PunishmentService service) {
        this.service = service;
    }

    @Override
    public @Nullable Punishment queryPunishment(int id) {
        return service.queryPunishment(id);
    }

    @Override
    public @NotNull Collection<Punishment> queryActivePunishments(@NotNull UUID target) {
        return service.queryActivePunishments(target);
    }

    @Override
    public @Nullable Punishment queryActiveBan(@NotNull UUID target) {
        return service.queryActiveBan(target);
    }

    @Override
    public @Nullable Punishment queryActiveMute(@NotNull UUID target) {
        return service.queryActiveMute(target);
    }

    @Override
    public @NotNull Collection<Punishment> queryActiveWarns(@NotNull UUID target) {
        return service.queryActiveWarns(target);
    }

    @Override
    public void addPunishment(@NotNull Punishment punishment) {
        PunishmentExecuteEvent event = new PunishmentExecuteEvent(punishment);
        StandaloneEventAPI.getApi().call(event);
        if (event.canceled) return;
        service.addPunishment(punishment);
    }

    @Override
    public void withdrawPunishment(int id, @Nullable UUID withdrawnBy) {
        PunishmentWithdrawEvent event = new PunishmentWithdrawEvent(id, withdrawnBy);
        StandaloneEventAPI.getApi().call(event);
        if (event.canceled) return;
        service.withdrawPunishment(id, withdrawnBy);
    }

    @Override
    public void removePunishment(int id) {
        PunishmentRemoveEvent event = new PunishmentRemoveEvent(id);
        StandaloneEventAPI.getApi().call(event);
        if (event.canceled) return;
        service.removePunishment(id);
    }
}
