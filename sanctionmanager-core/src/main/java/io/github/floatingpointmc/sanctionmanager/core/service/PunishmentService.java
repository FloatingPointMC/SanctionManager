package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.cache.PunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.core.repository.PunishmentRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
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
        // Check cache first
        Optional<Punishment> cached = cache.findActiveByTargetAndType(target, Type.BAN);
        if (cached.isPresent()) {
            return cached.get(); // May be null
        }
        
        // Cache miss - query DB
        LocalDateTime now = LocalDateTime.now();
        Collection<Punishment> fromDb = repository.findByTarget(target);
        Punishment activeBan = null;
        for (Punishment p : fromDb) {
            if (p.getType() == Type.BAN && activeBan == null && isActive(p, now)) {
                activeBan = p;
            }
            cache.put(p);
        }
        
        // Cache result (including null)
        cache.putActiveByTargetAndType(target, Type.BAN, activeBan);
        return activeBan;
    }

    public @Nullable Punishment queryActiveMute(@NotNull UUID target) {
        // Check cache first
        Optional<Punishment> cached = cache.findActiveByTargetAndType(target, Type.MUTE);
        if (cached.isPresent()) {
            return cached.get(); // May be null
        }

        // Cache miss - query DB
        LocalDateTime now = LocalDateTime.now();
        Collection<Punishment> fromDb = repository.findByTarget(target);
        Punishment activeMute = null;
        for (Punishment p : fromDb) {
            if (p.getType() == Type.MUTE && activeMute == null && isActive(p, now)) {
                activeMute = p;
            }
            cache.put(p);
        }

        // Cache result (including null)
        cache.putActiveByTargetAndType(target, Type.MUTE, activeMute);
        return activeMute;
    }

    public @NotNull Collection<Punishment> queryActiveWarns(@NotNull UUID target) {
        Collection<Punishment> fromDb = repository.findActiveWarnsByTarget(target);
        for (Punishment p : fromDb) {
            cache.put(p);
        }
        return fromDb;
    }

    public @Nullable Punishment queryActiveWarn(@NotNull UUID target) {
        // Check cache first
        Optional<Punishment> cached = cache.findActiveByTargetAndType(target, Type.WARN);
        if (cached.isPresent()) {
            return cached.get(); // May be null
        }

        // Cache miss - query DB
        LocalDateTime now = LocalDateTime.now();
        Collection<Punishment> fromDb = repository.findByTarget(target);
        Punishment activeWarn = null;
        for (Punishment p : fromDb) {
            if (p.getType() == Type.WARN && activeWarn == null && isActive(p, now)) {
                activeWarn = p;
            }
            cache.put(p);
        }

        // Cache result (including null)
        cache.putActiveByTargetAndType(target, Type.WARN, activeWarn);
        return activeWarn;
    }

    public void addPunishment(@NotNull Punishment punishment) {
        repository.save(punishment);
        cache.put(punishment);
        cache.invalidateByTargetAndType(punishment.getTarget(), punishment.getType());
    }

    public void updatePunishment(@NotNull Punishment punishment) {
        repository.update(punishment);
        cache.invalidate(punishment.getId());
        cache.put(punishment);
        cache.invalidateByTargetAndType(punishment.getTarget(), punishment.getType());
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
        Punishment punishment = queryPunishment(id);
        repository.delete(id);
        cache.invalidate(id);
        if (punishment != null) {
            cache.invalidateByTargetAndType(punishment.getTarget(), punishment.getType());
        }
    }

    private boolean isActive(@NotNull Punishment punishment, @NotNull LocalDateTime now) {
        if (punishment.isWithdrawn()) return false;
        if (punishment.isOverridden()) return false;
        if (punishment.getOverriddenBy() != null) return false;
        LocalDateTime expiryTime = punishment.getExpiryTime();
        return expiryTime == null || expiryTime.isAfter(now);
    }
}
