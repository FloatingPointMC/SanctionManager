package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.core.cache.PunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.core.repository.PunishmentRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public class PunishmentService {
    private final @NotNull PunishmentCache cache;
    private final @NotNull PunishmentRepository repository;

    public PunishmentService(@NotNull PunishmentCache cache, @NotNull PunishmentRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    public @Nullable Punishment queryPunishment(int id) {
        Punishment cached = cache.findById(id);
        if (cached != null) {
            return cached;
        }
        Punishment fromDb = repository.findById(id);
        if (fromDb != null) {
            cache.put(fromDb);
        }
        return fromDb;
    }

    public @NotNull Collection<Punishment> queryActivePunishments(@NotNull UUID target) {
        Collection<Punishment> cached = cache.findActiveByTarget(target);
        if (!cached.isEmpty()) {
            return cached;
        }
        Collection<Punishment> fromDb = repository.findActiveByTarget(target);
        for (Punishment p : fromDb) {
            cache.put(p);
        }
        return fromDb;
    }

    public @Nullable Punishment queryActiveBan(@NotNull UUID target) {
        LocalDateTime now = LocalDateTime.now();
        Collection<Punishment> fromDb = repository.findActiveBansByTarget(target);
        for (Punishment p : fromDb) {
            cache.put(p);
            if (isActive(p, now)) {
                return p;
            }
        }
        return null;
    }

    public @Nullable Punishment queryActiveMute(@NotNull UUID target) {
        LocalDateTime now = LocalDateTime.now();
        Collection<Punishment> fromDb = repository.findActiveMutesByTarget(target);
        for (Punishment p : fromDb) {
            cache.put(p);
            if (isActive(p, now)) {
                return p;
            }
        }
        return null;
    }

    public void addPunishment(@NotNull Punishment punishment) {
        repository.save(punishment);
        cache.put(punishment);
    }

    public void updatePunishment(@NotNull Punishment punishment) {
        repository.update(punishment);
        cache.invalidate(punishment.getId());
        cache.put(punishment);
    }

    public void withdrawPunishment(int id, @Nullable UUID withdrawnBy) {
        Punishment punishment = queryPunishment(id);
        if (punishment == null) return;
        if (punishment instanceof PunishmentRecord) {
            PunishmentRecord record = (PunishmentRecord) punishment;
            record.setWithdrawn(true);
            record.setWithdrawnBy(withdrawnBy);
            updatePunishment(record);
        }
    }

    public void removePunishment(int id) {
        repository.delete(id);
        cache.invalidate(id);
    }

    private boolean isActive(@NotNull Punishment punishment, @NotNull LocalDateTime now) {
        if (punishment.isWithdrawn()) return false;
        if (punishment.isOverridden()) return false;
        if (punishment.getOverriddenBy() != null) return false;
        LocalDateTime expiryTime = punishment.getExpiryTime();
        return expiryTime == null || expiryTime.isAfter(now);
    }
}
