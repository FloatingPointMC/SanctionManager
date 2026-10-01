package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;

/**
 * Database-based implementation of PlayerRepository (Persistence Layer).
 * <p>
 * Uses a separate table: sm_minecraft_players
 * <p>
 * This is SEPARATE from Core's punishment tables.
 * Minecraft owns player data; Core owns sanction data.
 * <p>
 * This is PERSISTENCE ONLY. Data survives server restarts.
 * For caching, use PlayerCache (not embedded in Repository).
 */
public class DatabasePlayerRepository implements PlayerRepository {
    private static final String TABLE_NAME = "sm_minecraft_players";

    private final HikariDataSource dataSource;

    public DatabasePlayerRepository(@NotNull HikariDataSource dataSource) {
        this.dataSource = dataSource;
        initializeTable();
    }

    @Override
    public @Nullable UUID findUuidByName(@NotNull String name) {
        String normalizedName = normalizeName(name);

        // Query database directly (no cache in Repository layer)
        String sql = "SELECT uuid FROM " + TABLE_NAME + " WHERE LOWER(name) = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, normalizedName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find UUID by name: " + name, e);
        }

        return null;
    }

    @Override
    public @Nullable String findNameByUuid(@NotNull UUID uuid) {
        // Query database directly (no cache in Repository layer)
        String sql = "SELECT name FROM " + TABLE_NAME + " WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find name by UUID: " + uuid, e);
        }

        return null;
    }

    @Override
    public void save(@NotNull UUID uuid, @NotNull String name) {
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

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
