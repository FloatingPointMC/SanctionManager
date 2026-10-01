package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

import java.util.UUID;

/**
 * Redis-based implementation of PlayerCache.
 * <p>
 * Distributed cache that survives server restarts but is still NOT persistence.
 * Redis is configured as cache with TTL, not as a primary data store.
 * <p>
 * Key format:
 * - UUID → Name: "sm:player:uuid:{uuid}"
 * - Name → UUID: "sm:player:name:{name}"
 */
public class RedisPlayerCache implements PlayerCache {
    private static final String KEY_PREFIX_UUID = "sm:player:uuid:";
    private static final String KEY_PREFIX_NAME = "sm:player:name:";
    private static final int TTL_SECONDS = 3600; // 1 hour cache TTL

    private final JedisPool jedisPool;

    public RedisPlayerCache(@NotNull JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public @Nullable UUID get(@NotNull String name) {
        String key = KEY_PREFIX_NAME + normalizeName(name);
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            return value != null ? UUID.fromString(value) : null;
        } catch (Exception e) {
            // Redis failure should not break application
            return null;
        }
    }

    @Override
    public @Nullable String get(@NotNull UUID uuid) {
        String key = KEY_PREFIX_UUID + uuid;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            // Redis failure should not break application
            return null;
        }
    }

    @Override
    public void put(@NotNull UUID uuid, @NotNull String name) {
        String normalizedName = normalizeName(name);
        String uuidKey = KEY_PREFIX_UUID + uuid;
        String nameKey = KEY_PREFIX_NAME + normalizedName;

        try (Jedis jedis = jedisPool.getResource()) {
            // Check if UUID had a different name cached
            String oldName = jedis.get(uuidKey);
            if (oldName != null && !oldName.equalsIgnoreCase(name)) {
                // Invalidate old name mapping
                jedis.del(KEY_PREFIX_NAME + normalizeName(oldName));
            }

            // Set new mappings with TTL
            jedis.setex(uuidKey, TTL_SECONDS, name);
            jedis.setex(nameKey, TTL_SECONDS, uuid.toString());
        } catch (Exception e) {
            // Redis failure should not break application
        }
    }

    @Override
    public void invalidate(@NotNull UUID uuid) {
        String uuidKey = KEY_PREFIX_UUID + uuid;
        try (Jedis jedis = jedisPool.getResource()) {
            String name = jedis.get(uuidKey);
            jedis.del(uuidKey);
            if (name != null) {
                jedis.del(KEY_PREFIX_NAME + normalizeName(name));
            }
        } catch (Exception e) {
            // Redis failure should not break application
        }
    }

    @Override
    public void invalidate(@NotNull String name) {
        String nameKey = KEY_PREFIX_NAME + normalizeName(name);
        try (Jedis jedis = jedisPool.getResource()) {
            String uuidStr = jedis.get(nameKey);
            jedis.del(nameKey);
            if (uuidStr != null) {
                jedis.del(KEY_PREFIX_UUID + uuidStr);
            }
        } catch (Exception e) {
            // Redis failure should not break application
        }
    }

    @Override
    public void clear() {
        try (Jedis jedis = jedisPool.getResource()) {
            // Clear all player cache keys (pattern matching)
            jedis.keys(KEY_PREFIX_UUID + "*").forEach(jedis::del);
            jedis.keys(KEY_PREFIX_NAME + "*").forEach(jedis::del);
        } catch (Exception e) {
            // Redis failure should not break application
        }
    }

    @Override
    public void close() {
        // JedisPool lifecycle managed externally
    }

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
