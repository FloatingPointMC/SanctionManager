package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-based implementation of PlayerRepository.
 * <p>
 * Uses Redis keys:
 * - sm:minecraft:player:uuid:{uuid} -> name
 * - sm:minecraft:player:name:{normalized_name} -> uuid
 * <p>
 * This is SEPARATE from Core's Redis keys (sm:core:...)
 * <p>
 * Uses in-memory cache for faster lookups and reduced Redis calls.
 */
public class RedisPlayerRepository implements PlayerRepository {
    private static final String KEY_PREFIX_UUID = "sm:minecraft:player:uuid:";
    private static final String KEY_PREFIX_NAME = "sm:minecraft:player:name:";

    private final JedisPool jedisPool;
    private final ConcurrentHashMap<UUID, String> uuidToName;
    private final ConcurrentHashMap<String, UUID> nameToUuid;

    public RedisPlayerRepository(@NotNull JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.uuidToName = new ConcurrentHashMap<>();
        this.nameToUuid = new ConcurrentHashMap<>();
    }

    @Override
    public @Nullable UUID findUuidByName(@NotNull String name) {
        String normalizedName = normalizeName(name);

        // Check cache first
        UUID cached = nameToUuid.get(normalizedName);
        if (cached != null) {
            return cached;
        }

        // Query Redis
        String key = KEY_PREFIX_NAME + normalizedName;
        try (Jedis jedis = jedisPool.getResource()) {
            String uuidStr = jedis.get(key);
            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                // Update cache
                nameToUuid.put(normalizedName, uuid);
                return uuid;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find UUID by name from Redis: " + name, e);
        }

        return null;
    }

    @Override
    public @Nullable String findNameByUuid(@NotNull UUID uuid) {
        // Check cache first
        String cached = uuidToName.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Query Redis
        String key = KEY_PREFIX_UUID + uuid;
        try (Jedis jedis = jedisPool.getResource()) {
            String name = jedis.get(key);
            if (name != null) {
                // Update cache
                uuidToName.put(uuid, name);
                nameToUuid.put(normalizeName(name), uuid);
                return name;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find name by UUID from Redis: " + uuid, e);
        }

        return null;
    }

    @Override
    public void save(@NotNull UUID uuid, @NotNull String name) {
        String normalizedName = normalizeName(name);

        // Remove old name mapping if UUID had different name
        String oldName = uuidToName.get(uuid);
        if (oldName != null && !oldName.equalsIgnoreCase(name)) {
            String oldNormalizedName = normalizeName(oldName);
            nameToUuid.remove(oldNormalizedName);

            // Remove old Redis key
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(KEY_PREFIX_NAME + oldNormalizedName);
            } catch (Exception e) {
                // Log but don't fail
            }
        }

        // Update cache
        uuidToName.put(uuid, name);
        nameToUuid.put(normalizedName, uuid);

        // Persist to Redis
        try (Jedis jedis = jedisPool.getResource()) {
            String keyUuid = KEY_PREFIX_UUID + uuid;
            String keyName = KEY_PREFIX_NAME + normalizedName;

            jedis.set(keyUuid, name);
            jedis.set(keyName, uuid.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save player to Redis: " + uuid + " -> " + name, e);
        }
    }

    @Override
    public void close() {
        uuidToName.clear();
        nameToUuid.clear();
        // JedisPool lifecycle managed elsewhere, not closed here
    }

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
