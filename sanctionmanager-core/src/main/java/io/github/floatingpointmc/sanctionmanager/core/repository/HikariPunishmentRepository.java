package io.github.floatingpointmc.sanctionmanager.core.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.config.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class HikariPunishmentRepository implements PunishmentRepository, AutoCloseable {
    private final HikariDataSource dataSource;

    private static final String CREATE_PUNISHMENT_TABLE =
            "CREATE TABLE IF NOT EXISTS punishment (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "rel_id INT NOT NULL, " +
                    "type TINYINT NOT NULL);";

    private static final String CREATE_BAN_TABLE =
            "CREATE TABLE IF NOT EXISTS ban (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "target_uuid VARCHAR(36) NOT NULL, " +
                    "executor_uuid VARCHAR(36), " +
                    "operator_name VARCHAR(64) NOT NULL DEFAULT '[Console]', " +
                    "executing_time TIMESTAMP NOT NULL, " +
                    "expiry_time TIMESTAMP, " +
                    "overridden BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "overridden_by_id INT, " +
                    "overriding BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "overridden_id INT, " +
                    "withdrawn BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "withdrawn_by_uuid VARCHAR(36), " +
                    "reason VARCHAR(256));";

    private static final String CREATE_MUTE_TABLE =
            "CREATE TABLE IF NOT EXISTS mute (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "target_uuid VARCHAR(36) NOT NULL, " +
                    "executor_uuid VARCHAR(36), " +
                    "operator_name VARCHAR(64) NOT NULL DEFAULT '[Console]', " +
                    "executing_time TIMESTAMP NOT NULL, " +
                    "expiry_time TIMESTAMP, " +
                    "overridden BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "overridden_by_id INT, " +
                    "overriding BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "overridden_id INT, " +
                    "withdrawn BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "withdrawn_by_uuid VARCHAR(36), " +
                    "reason VARCHAR(256));";

    private static final String SELECT_PUNISHMENT_BY_ID =
            "SELECT p.id, p.rel_id, p.type FROM punishment p WHERE p.id = ?;";

    private static final String SELECT_PUNISHMENT_BY_TARGET =
            "SELECT p.id, p.rel_id, p.type FROM punishment p " +
                    "JOIN ban b ON p.rel_id = b.id AND p.type = 0 " +
                    "JOIN mute m ON p.rel_id = m.id AND p.type = 1 " +
                    "WHERE b.target_uuid = ? OR m.target_uuid = ?;";

    private static final String SELECT_ACTIVE_BANS_BY_TARGET =
            "SELECT p.id, p.rel_id, p.type, b.target_uuid, b.executor_uuid, b.operator_name, " +
                    "b.executing_time, b.expiry_time, b.overridden, b.overridden_by_id, " +
                    "b.overriding, b.overridden_id, b.withdrawn, b.withdrawn_by_uuid, b.reason " +
                    "FROM punishment p " +
                    "JOIN ban b ON p.rel_id = b.id " +
                    "WHERE p.type = ? AND b.target_uuid = ? " +
                    "AND b.overridden = FALSE AND b.withdrawn = FALSE " +
                    "AND (b.expiry_time IS NULL OR b.expiry_time > CURRENT_TIMESTAMP);";

    private static final String SELECT_ACTIVE_MUTES_BY_TARGET =
            "SELECT p.id, p.rel_id, p.type, m.target_uuid, m.executor_uuid, m.operator_name, " +
                    "m.executing_time, m.expiry_time, m.overridden, m.overridden_by_id, " +
                    "m.overriding, m.overridden_id, m.withdrawn, m.withdrawn_by_uuid, m.reason " +
                    "FROM punishment p " +
                    "JOIN mute m ON p.rel_id = m.id " +
                    "WHERE p.type = ? AND m.target_uuid = ? " +
                    "AND m.overridden = FALSE AND m.withdrawn = FALSE " +
                    "AND (m.expiry_time IS NULL OR m.expiry_time > CURRENT_TIMESTAMP);";

    private static final String INSERT_BAN =
            "INSERT INTO ban (target_uuid, executor_uuid, operator_name, executing_time, expiry_time, " +
                    "overridden, overridden_by_id, overriding, overridden_id, withdrawn, withdrawn_by_uuid, reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    private static final String INSERT_MUTE =
            "INSERT INTO mute (target_uuid, executor_uuid, operator_name, executing_time, expiry_time, " +
                    "overridden, overridden_by_id, overriding, overridden_id, withdrawn, withdrawn_by_uuid, reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    private static final String INSERT_PUNISHMENT =
            "INSERT INTO punishment (rel_id, type) VALUES (?, ?);";

    private static final String UPDATE_BAN =
            "UPDATE ban SET target_uuid = ?, executor_uuid = ?, operator_name = ?, executing_time = ?, expiry_time = ?, " +
                    "overridden = ?, overridden_by_id = ?, overriding = ?, overridden_id = ?, " +
                    "withdrawn = ?, withdrawn_by_uuid = ?, reason = ? WHERE id = ?;";

    private static final String UPDATE_MUTE =
            "UPDATE mute SET target_uuid = ?, executor_uuid = ?, operator_name = ?, executing_time = ?, expiry_time = ?, " +
                    "overridden = ?, overridden_by_id = ?, overriding = ?, overridden_id = ?, " +
                    "withdrawn = ?, withdrawn_by_uuid = ?, reason = ? WHERE id = ?;";

    private static final String DELETE_PUNISHMENT =
            "DELETE FROM punishment WHERE id = ?;";

    private static final String DELETE_BAN =
            "DELETE FROM ban WHERE id = ?;";

    private static final String DELETE_MUTE =
            "DELETE FROM mute WHERE id = ?;";

    private static final String SELECT_REL_ID_BY_PUNISHMENT_ID =
            "SELECT rel_id, type FROM punishment WHERE id = ?;";

    public HikariPunishmentRepository(@NotNull DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriver());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUser());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setPoolName("SanctionManager-HikariPool");
        this.dataSource = new HikariDataSource(hikariConfig);
        initTables();
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_PUNISHMENT_TABLE);
            stmt.executeUpdate(CREATE_BAN_TABLE);
            stmt.executeUpdate(CREATE_MUTE_TABLE);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database tables", e);
        }
    }

    @Override
    public @Nullable Punishment findById(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_REL_ID_BY_PUNISHMENT_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int relId = rs.getInt("rel_id");
                    int typeVal = rs.getByte("type");
                    Type type = Type.values()[typeVal];
                    return findDetail(conn, id, relId, type);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query punishment by id: " + id, e);
        }
        return null;
    }

    private @Nullable Punishment findDetail(@NotNull Connection conn, int punishmentId, int relId, @NotNull Type type) throws SQLException {
        String table = type == Type.BAN ? "ban" : "mute";
        String sql = "SELECT * FROM " + table + " WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(punishmentId, relId, type, rs);
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull Collection<Punishment> findByTarget(@NotNull UUID target) {
        Collection<Punishment> result = new ArrayList<>();
        result.addAll(findActiveByTypeAndTarget(Type.BAN, target, false));
        result.addAll(findActiveByTypeAndTarget(Type.MUTE, target, false));
        return result;
    }

    @Override
    public @NotNull Collection<Punishment> findActiveByTarget(@NotNull UUID target) {
        Collection<Punishment> result = new ArrayList<>();
        result.addAll(findActiveByTypeAndTarget(Type.BAN, target, true));
        result.addAll(findActiveByTypeAndTarget(Type.MUTE, target, true));
        return result;
    }

    @Override
    public @NotNull Collection<Punishment> findActiveBansByTarget(@NotNull UUID target) {
        return findActiveByTypeAndTarget(Type.BAN, target, true);
    }

    @Override
    public @NotNull Collection<Punishment> findActiveMutesByTarget(@NotNull UUID target) {
        return findActiveByTypeAndTarget(Type.MUTE, target, true);
    }

    private @NotNull Collection<Punishment> findActiveByTypeAndTarget(@NotNull Type type, @NotNull UUID target, boolean activeOnly) {
        Collection<Punishment> result = new ArrayList<>();
        String sql = type == Type.BAN ? SELECT_ACTIVE_BANS_BY_TARGET : SELECT_ACTIVE_MUTES_BY_TARGET;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, type.ordinal());
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs.getInt("id"), rs.getInt("rel_id"), type, rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query " + type.name().toLowerCase() + "s by target: " + target, e);
        }
        return result;
    }

    @Override
    public void save(@NotNull Punishment punishment) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int relId = insertDetail(conn, punishment);
                int punishmentId = insertPunishmentIndex(conn, relId, punishment.getType());
                conn.commit();
                if (punishment instanceof PunishmentRecord) {
                    PunishmentRecord record = (PunishmentRecord) punishment;
                    record.setId(punishmentId);
                    record.setRelId(relId);
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save punishment", e);
        }
    }

    private int insertDetail(@NotNull Connection conn, @NotNull Punishment p) throws SQLException {
        String sql = p.getType() == Type.BAN ? INSERT_BAN : INSERT_MUTE;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setDetailParams(ps, p);
            ps.executeUpdate();
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to obtain generated key from " + p.getType().name().toLowerCase() + " table");
    }

    private int insertPunishmentIndex(@NotNull Connection conn, int relId, @NotNull Type type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PUNISHMENT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, relId);
            ps.setByte(2, (byte) type.ordinal());
            ps.executeUpdate();
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to obtain generated key from punishment table");
    }

    @Override
    public void update(@NotNull Punishment punishment) {
        String sql = punishment.getType() == Type.BAN ? UPDATE_BAN : UPDATE_MUTE;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setDetailParams(ps, punishment);
            ps.setInt(13, punishment.getRelId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update " + punishment.getType().name().toLowerCase() + ": " + punishment.getRelId(), e);
        }
    }

    @Override
    public void delete(int id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int relId;
                Type type;
                try (PreparedStatement ps = conn.prepareStatement(SELECT_REL_ID_BY_PUNISHMENT_ID)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            relId = rs.getInt("rel_id");
                            type = Type.values()[rs.getByte("type")];
                        } else {
                            conn.rollback();
                            return;
                        }
                    }
                }
                String detailDeleteSql = type == Type.BAN ? DELETE_BAN : DELETE_MUTE;
                try (PreparedStatement ps = conn.prepareStatement(detailDeleteSql)) {
                    ps.setInt(1, relId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(DELETE_PUNISHMENT)) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete punishment: " + id, e);
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private @NotNull Punishment mapRow(int punishmentId, int relId, @NotNull Type type, @NotNull ResultSet rs) throws SQLException {
        String executorUuidStr = rs.getString("executor_uuid");
        Timestamp expiryTimestamp = rs.getTimestamp("expiry_time");
        String withdrawnByUuidStr = rs.getString("withdrawn_by_uuid");
        return new PunishmentRecord(
                punishmentId,
                relId,
                UUID.fromString(rs.getString("target_uuid")),
                executorUuidStr != null ? UUID.fromString(executorUuidStr) : null,
                rs.getString("operator_name"),
                rs.getTimestamp("executing_time").toLocalDateTime(),
                expiryTimestamp != null ? expiryTimestamp.toLocalDateTime() : null,
                rs.getBoolean("overridden"),
                null,
                rs.getBoolean("overriding"),
                null,
                rs.getBoolean("withdrawn"),
                withdrawnByUuidStr != null ? UUID.fromString(withdrawnByUuidStr) : null,
                rs.getString("reason"),
                type
        );
    }

    private void setDetailParams(@NotNull PreparedStatement ps, @NotNull Punishment p) throws SQLException {
        ps.setString(1, p.getTarget().toString());
        ps.setString(2, p.getExecutor() != null ? p.getExecutor().toString() : null);
        ps.setString(3, p.getOperatorName());
        ps.setTimestamp(4, Timestamp.valueOf(p.getExecutingTime()));
        ps.setTimestamp(5, p.getExpiryTime() != null ? Timestamp.valueOf(p.getExpiryTime()) : null);
        ps.setBoolean(6, p.isOverridden());
        ps.setObject(7, p.getOverriddenBy() != null ? p.getOverriddenBy().getId() : null, Types.INTEGER);
        ps.setBoolean(8, p.isOverriding());
        ps.setObject(9, p.getOverriddenPunishment() != null ? p.getOverriddenPunishment().getId() : null, Types.INTEGER);
        ps.setBoolean(10, p.isWithdrawn());
        ps.setString(11, p.getWithdrawnBy() != null ? p.getWithdrawnBy().toString() : null);
        ps.setString(12, p.getReason());
    }
}