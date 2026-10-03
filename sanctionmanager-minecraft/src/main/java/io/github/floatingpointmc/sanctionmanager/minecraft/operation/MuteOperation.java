package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a mute operation to be executed.
 * <p>
 * This is a platform-independent business operation object used for
 * communication between Command layer, Service layer, and Bridge/Proxy transport.
 * <p>
 * Immutable and serializable for network transmission.
 * Only contains UUIDs for identity - names are resolved at presentation layer.
 */
public final class MuteOperation implements Serializable {
    private static final long serialVersionUID = 2L;

    private final @NotNull UUID targetUuid;
    private final @Nullable UUID executorUuid;
    private final @Nullable LocalDateTime expiryTime;
    private final @Nullable String reason;

    public MuteOperation(
            @NotNull UUID targetUuid,
            @Nullable UUID executorUuid,
            @Nullable LocalDateTime expiryTime,
            @Nullable String reason
    ) {
        this.targetUuid = targetUuid;
        this.executorUuid = executorUuid;
        this.expiryTime = expiryTime;
        this.reason = reason;
    }

    public @NotNull UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable UUID getExecutorUuid() {
        return executorUuid;
    }

    public @Nullable LocalDateTime getExpiryTime() {
        return expiryTime;
    }

    public @Nullable String getReason() {
        return reason;
    }
}
