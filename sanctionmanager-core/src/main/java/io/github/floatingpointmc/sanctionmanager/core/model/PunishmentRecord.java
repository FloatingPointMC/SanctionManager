package io.github.floatingpointmc.sanctionmanager.core.model;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
public class PunishmentRecord implements Punishment {
    private int id;
    private int relId;
    @Getter
    private @NotNull UUID target;
    @Getter
    private @Nullable UUID executor;
    @Getter
    private @Nullable String operatorName;
    @Getter
    private @NotNull LocalDateTime executingTime;
    @Getter
    private @Nullable LocalDateTime expiryTime;
    @Getter
    private boolean overridden;
    @Getter
    private @Nullable Punishment overriddenBy;
    @Getter
    private boolean overriding;
    @Getter
    private @Nullable Punishment overriddenPunishment;
    @Getter
    private boolean withdrawn;
    @Getter
    private @Nullable UUID withdrawnBy;
    @Getter
    private @Nullable String reason;
    @Getter
    private @NotNull Type type;
    private boolean processed;

    public PunishmentRecord(int id, int relId, @NotNull UUID target, @Nullable UUID executor, @Nullable String operatorName, @NotNull LocalDateTime executingTime, @Nullable LocalDateTime expiryTime, boolean overridden, @Nullable Punishment overriddenBy, boolean overriding, @Nullable Punishment overriddenPunishment, boolean withdrawn, @Nullable UUID withdrawnBy, @Nullable String reason, @NotNull Type type) {
        this.id = id;
        this.relId = relId;
        this.target = target;
        this.executor = executor;
        this.operatorName = operatorName;
        this.executingTime = executingTime;
        this.expiryTime = expiryTime;
        this.overridden = overridden;
        this.overriddenBy = overriddenBy;
        this.overriding = overriding;
        this.overriddenPunishment = overriddenPunishment;
        this.withdrawn = withdrawn;
        this.withdrawnBy = withdrawnBy;
        this.reason = reason;
        this.type = type;
        processed = id != 0;
    }

    @Override
    public int getId() throws IllegalStateException {
        if (!processed) throw new IllegalStateException("Punishment record has not been processed");
        return id;
    }

    @Override
    public int getRelId() throws IllegalStateException {
        if (!processed) throw new IllegalStateException("Punishment record has not been processed");
        return relId;
    }
}