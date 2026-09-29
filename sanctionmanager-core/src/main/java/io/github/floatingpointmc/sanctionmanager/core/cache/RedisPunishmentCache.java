package io.github.floatingpointmc.sanctionmanager.core.cache;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class RedisPunishmentCache implements PunishmentCache {
    private final RedisClient redisClient;
    private final PunishmentSerializer serializer;
    private static final String NULL_MARKER = "__NULL__"; // Marker for cached null values

    public RedisPunishmentCache(@NotNull RedisClient redisClient, @NotNull PunishmentSerializer serializer) {
        this.redisClient = redisClient;
        this.serializer = serializer;
    }

    @Override
    public @Nullable Punishment findById(int id) {
        String data = redisClient.get(keyById(id));
        if (data == null) return null;
        return serializer.deserialize(data);
    }

    @Override
    public @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target) {
        Collection<String> ids = redisClient.smembers(keyByTarget(target));
        if (ids.isEmpty()) return Collections.emptyList();
        Collection<Punishment> result = new ArrayList<>();
        for (String idStr : ids) {
            String data = redisClient.get(keyById(Integer.parseInt(idStr)));
            if (data != null) {
                result.add(serializer.deserialize(data));
            }
        }
        return result;
    }

    @Override
    public void put(@NotNull Punishment punishment) {
        String data = serializer.serialize(punishment);
        String idKey = keyById(punishment.getId());
        redisClient.set(idKey, data, SetParams.setParams().ex(3600));
        redisClient.sadd(keyByTarget(punishment.getTarget()), String.valueOf(punishment.getId()));
        redisClient.expire(keyByTarget(punishment.getTarget()), 3600);
    }

    @Override
    public void invalidate(int id) {
        String idKey = keyById(id);
        String data = redisClient.get(idKey);
        redisClient.del(idKey);
        if (data != null) {
            Punishment p = serializer.deserialize(data);
            if (p != null) {
                redisClient.srem(keyByTarget(p.getTarget()), String.valueOf(id));
            }
        }
    }

    @Override
    public void invalidateByTarget(@NotNull UUID target) {
        String setKey = keyByTarget(target);
        Collection<String> ids = redisClient.smembers(setKey);
        for (String idStr : ids) {
            redisClient.del(keyById(Integer.parseInt(idStr)));
        }
        redisClient.del(setKey);
        // Also invalidate typed cache
        redisClient.del(keyByTargetAndType(target, Type.BAN));
        redisClient.del(keyByTargetAndType(target, Type.MUTE));
    }

    private static String keyById(int id) {
        return "sanctionmanager:punishment:id:" + id;
    }

    private static String keyByTarget(UUID target) {
        return "sanctionmanager:punishment:target:" + target;
    }

    private static String keyByTargetAndType(UUID target, Type type) {
        return "sanctionmanager:punishment:active:" + target + ":" + type.name();
    }

    @Override
    public @NotNull Optional<Punishment> findActiveByTargetAndType(@NotNull UUID target, @NotNull Type type) {
        String key = keyByTargetAndType(target, type);
        String data = redisClient.get(key);
        if (data == null) {
            return Optional.empty(); // Cache miss
        }
        if (NULL_MARKER.equals(data)) {
            return Optional.empty(); // Cached as null
        }
        return Optional.ofNullable(serializer.deserialize(data)); // Cached punishment
    }

    @Override
    public void putActiveByTargetAndType(@NotNull UUID target, @NotNull Type type, @Nullable Punishment punishment) {
        String key = keyByTargetAndType(target, type);
        if (punishment == null) {
            // Cache null result (no active punishment)
            redisClient.set(key, NULL_MARKER, SetParams.setParams().ex(3600));
        } else {
            // Cache the punishment, with TTL based on expiry time
            String data = serializer.serialize(punishment);
            long ttl = calculateTtl(punishment);
            redisClient.set(key, data, SetParams.setParams().ex(ttl));
        }
    }

    @Override
    public void invalidateByTargetAndType(@NotNull UUID target, @NotNull Type type) {
        redisClient.del(keyByTargetAndType(target, type));
    }

    /**
     * Calculate TTL for cached punishment.
     * If punishment has expiry time, TTL should not exceed remaining lifetime.
     */
    private long calculateTtl(Punishment punishment) {
        if (punishment.getExpiryTime() == null) {
            return 3600; // 1 hour for permanent punishment
        }
        long remaining = java.time.Duration.between(
            java.time.LocalDateTime.now(),
            punishment.getExpiryTime()
        ).getSeconds();
        if (remaining <= 0) {
            return 60; // Already expired or about to, short TTL
        }
        // TTL is minimum of remaining time and 1 hour
        return Math.min(remaining, 3600);
    }
}
