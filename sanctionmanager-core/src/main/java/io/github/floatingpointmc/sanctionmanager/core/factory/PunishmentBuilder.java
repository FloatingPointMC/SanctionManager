package io.github.floatingpointmc.sanctionmanager.core.factory;

import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PunishmentBuilder implements Punishment.Builder {
    private final @NotNull UUID target;
    private @Nullable UUID executor;
    private final @Nullable String operatorName;
    private @NotNull LocalDateTime executingTime = LocalDateTime.now();
    private @Nullable LocalDateTime expiryTime;
    private @Nullable String reason;
    private final @NotNull Type type;

    @Override
    public Punishment.Builder executor(@Nullable UUID executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public Punishment.Builder executingTime(@NotNull LocalDateTime executingTime) {
        this.executingTime = executingTime;
        return this;
    }

    @Override
    public Punishment.Builder expiryTime(@Nullable LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
        return this;
    }

    @Override
    public Punishment.Builder reason(@Nullable String reason) {
        this.reason = reason;
        return this;
    }

    @Override
    public Punishment build() {
        return new PunishmentRecord(0, 0, this.target, this.executor, this.operatorName, this.executingTime, this.expiryTime, false, null, false, null, false, null, this.reason, this.type);
    }
}
