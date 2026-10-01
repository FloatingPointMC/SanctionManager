package io.github.floatingpointmc.sanctionmanager.core.cache;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public class PunishmentSerializer {
    public @NotNull String serialize(@NotNull Punishment p) {
        String overriddenById = p.getOverriddenBy() != null ? String.valueOf(p.getOverriddenBy().getId()) : "null";
        String overriddenId = p.getOverriddenPunishment() != null ? String.valueOf(p.getOverriddenPunishment().getId()) : "null";
        String withdrawnById = p.getWithdrawnBy() != null ? p.getWithdrawnBy().toString() : "null";
        return p.getId() + "|" +
                p.getRelId() + "|" +
                p.getTarget() + "|" +
                (p.getExecutor() != null ? p.getExecutor() : "null") + "|" +
                p.getExecutingTime() + "|" +
                (p.getExpiryTime() != null ? p.getExpiryTime() : "null") + "|" +
                p.isOverridden() + "|" +
                overriddenById + "|" +
                p.isOverriding() + "|" +
                overriddenId + "|" +
                p.isWithdrawn() + "|" +
                withdrawnById + "|" +
                (p.getReason() != null ? p.getReason() : "null") + "|" +
                p.getType().name();
    }

    public @Nullable Punishment deserialize(@NotNull String data) {
        String[] parts = data.split("\\|");
        if (parts.length != 14) return null;
        return new PunishmentRecord(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                UUID.fromString(parts[2]),
                "null".equals(parts[3]) ? null : UUID.fromString(parts[3]),
                LocalDateTime.parse(parts[4]),
                "null".equals(parts[5]) ? null : LocalDateTime.parse(parts[5]),
                Boolean.parseBoolean(parts[6]),
                null,
                Boolean.parseBoolean(parts[8]),
                null,
                Boolean.parseBoolean(parts[10]),
                "null".equals(parts[11]) ? null : UUID.fromString(parts[11]),
                "null".equals(parts[12]) ? null : parts[12],
                Type.valueOf(parts[13])
        );
    }
}