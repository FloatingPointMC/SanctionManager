package io.github.floatingpointmc.sanctionmanager.core.cache;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalPunishmentCache implements PunishmentCache {
    private final Map<Integer, Punishment> idCache = new ConcurrentHashMap<>();
    private final Map<UUID, Collection<Punishment>> targetCache = new ConcurrentHashMap<>();
    private final Map<TypedCacheKey, Punishment> activeTypedCache = new ConcurrentHashMap<>();
    private final Set<TypedCacheKey> nullMarkers = ConcurrentHashMap.newKeySet();

    @Override
    public @Nullable Punishment findById(int id) {
        return idCache.get(id);
    }

    @Override
    public @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target) {
        return targetCache.getOrDefault(target, Collections.emptyList());
    }

    @Override
    public void put(@NotNull Punishment punishment) {
        idCache.put(punishment.getId(), punishment);
        targetCache.computeIfAbsent(punishment.getTarget(), k -> new CopyOnWriteArrayList<>()).add(punishment);
    }

    @Override
    public void invalidate(int id) {
        Punishment removed = idCache.remove(id);
        if (removed != null) {
            Collection<Punishment> list = targetCache.get(removed.getTarget());
            if (list != null) {
                list.removeIf(p -> p.getId() == id);
                if (list.isEmpty()) {
                    targetCache.remove(removed.getTarget());
                }
            }
        }
    }

    @Override
    public void invalidateByTarget(@NotNull UUID target) {
        Collection<Punishment> removed = targetCache.remove(target);
        if (removed != null) {
            for (Punishment p : removed) {
                idCache.remove(p.getId());
            }
        }
        activeTypedCache.remove(new TypedCacheKey(target, Type.BAN));
        activeTypedCache.remove(new TypedCacheKey(target, Type.MUTE));
        nullMarkers.remove(new TypedCacheKey(target, Type.BAN));
        nullMarkers.remove(new TypedCacheKey(target, Type.MUTE));
    }

    @Override
    public @NotNull Optional<Punishment> findActiveByTargetAndType(@NotNull UUID target, @NotNull Type type) {
        TypedCacheKey key = new TypedCacheKey(target, type);
        if (nullMarkers.contains(key)) {
            return Optional.empty();
        }
        Punishment punishment = activeTypedCache.get(key);
        if (punishment != null) {
            return Optional.of(punishment);
        }
        return Optional.empty();
    }

    @Override
    public void putActiveByTargetAndType(@NotNull UUID target, @NotNull Type type, @Nullable Punishment punishment) {
        TypedCacheKey key = new TypedCacheKey(target, type);
        if (punishment == null) {
            nullMarkers.add(key);
            activeTypedCache.remove(key);
        } else {
            nullMarkers.remove(key);
            activeTypedCache.put(key, punishment);
        }
    }

    @Override
    public void invalidateByTargetAndType(@NotNull UUID target, @NotNull Type type) {
        activeTypedCache.remove(new TypedCacheKey(target, type));
        nullMarkers.remove(new TypedCacheKey(target, type));
    }

    private static class TypedCacheKey {
        private final UUID target;
        private final Type type;

        TypedCacheKey(UUID target, Type type) {
            this.target = target;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypedCacheKey)) return false;
            TypedCacheKey that = (TypedCacheKey) o;
            return target.equals(that.target) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, type);
        }
    }
}
