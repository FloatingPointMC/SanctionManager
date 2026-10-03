package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.Duration;
import java.util.UUID;

/**
 * Represents a ban operation to be executed.
 * <p>
 * This is a platform-independent business operation object used for
 * communication between Command layer, Service layer, and Bridge/Proxy transport.
 * <p>
 * Immutable and serializable for network transmission.
 * Only contains UUIDs for identity - names are resolved at presentation layer.
 * <p>
 * The duration represents how long the ban should last from the actual execution time.
 * null duration indicates a permanent ban.
 */
public final class BanOperation implements Serializable {
    private static final long serialVersionUID = 3L;

    private final @NotNull UUID targetUuid;
    private final @Nullable UUID executorUuid;
    private final @Nullable Duration duration;
    private final @Nullable String reason;

    public BanOperation(
            @NotNull UUID targetUuid,
            @Nullable UUID executorUuid,
            @Nullable Duration duration,
            @Nullable String reason
    ) {
        this.targetUuid = targetUuid;
        this.executorUuid = executorUuid;
        this.duration = duration;
        this.reason = reason;
    }

    public @NotNull UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable UUID getExecutorUuid() {
        return executorUuid;
    }

    public @Nullable Duration getDuration() {
        return duration;
    }

    public @Nullable String getReason() {
        return reason;
    }
}
