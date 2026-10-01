package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-based implementation of PlayerRepository.
 * <p>
 * Uses a separate table: sm_minecraft_players
 * <p>
 * This is SEPARATE from Core's punishment tables.
 * Minecraft owns player data; Core owns sanction data.
 * <p>
 * Uses in-memory cache for fast lookups.
 */
public class DatabasePlayerRepository implements PlayerRepository {
    private static final String TABLE_NAME = "sm_minecraft_players";

    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<UUID, String> uuidToName;
    private final ConcurrentHashMap<String, UUID> nameToUuid;

    public DatabasePlayerRepository(@NotNull HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.uuidToName = new ConcurrentHashMap<>();
        this.nameToUuid = new ConcurrentHashMap<>();

        initializeTable();
        loadCache();
    }

    @Override
    public @Nullable UUID findUuidByName(@NotNull String name) {
        String normalizedName = normalizeName(name);

        // Check cache first
        UUID cached = nameToUuid.get(normalizedName);
        if (cached != null) {
            return cached;
        }

        // Query database
        String sql = "SELECT uuid FROM " + TABLE_NAME + " WHERE LOWER(name) = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, normalizedName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    // Update cache
                    String actualName = findNameByUuid(uuid);
                    if (actualName != null) {
                        nameToUuid.put(normalizedName, uuid);
                    }
                    return uuid;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find UUID by name: " + name, e);
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

        // Query database
        String sql = "SELECT name FROM " + TABLE_NAME + " WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    // Update cache
                    uuidToName.put(uuid, name);
                    nameToUuid.put(normalizeName(name), uuid);
                    return name;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find name by UUID: " + uuid, e);
        }

        return null;
    }

    @Override
    public void save(@NotNull UUID uuid, @NotNull String name) {
        String normalizedName = normalizeName(name);

        // Remove old name mapping if UUID had different name
        String oldName = uuidToName.get(uuid);
        if (oldName != null && !oldName.equalsIgnoreCase(name)) {
            nameToUuid.remove(normalizeName(oldName));
        }

        // Update cache
        uuidToName.put(uuid, name);
        nameToUuid.put(normalizedName, uuid);

        // Persist to database (UPSERT)
        String sql = "INSERT INTO " + TABLE_NAME + " (uuid, name, last_seen) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = ?, last_seen = ?";

        long now = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setLong(3, now);
            stmt.setString(4, name);
            stmt.setLong(5, now);

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save player: " + uuid + " -> " + name, e);
        }
    }

    @Override
    public void close() {
        uuidToName.clear();
        nameToUuid.clear();
        // DataSource lifecycle managed by Core, not closed here
    }

    private void initializeTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                     "uuid VARCHAR(36) PRIMARY KEY, " +
                     "name VARCHAR(16) NOT NULL, " +
                     "last_seen BIGINT NOT NULL, " +
                     "INDEX idx_name (name)" +
                     ")";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize minecraft players table", e);
        }
    }

    private void loadCache() {
        String sql = "SELECT uuid, name FROM " + TABLE_NAME;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                uuidToName.put(uuid, name);
                nameToUuid.put(normalizeName(name), uuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load player cache from database", e);
        }
    }

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
