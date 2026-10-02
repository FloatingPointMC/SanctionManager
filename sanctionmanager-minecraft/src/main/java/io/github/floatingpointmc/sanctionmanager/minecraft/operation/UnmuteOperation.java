package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents an unmute operation to be executed.
 * <p>
 * This is a platform-independent business operation object used for
 * communication between Command layer, Service layer, and Bridge/Proxy transport.
 * <p>
 * Immutable and serializable for network transmission.
 */
public final class UnmuteOperation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final @NotNull UUID targetUuid;
    private final @NotNull String targetName;
    private final @Nullable UUID executorUuid;
    private final @NotNull String executorName;

    public UnmuteOperation(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable UUID executorUuid,
            @NotNull String executorName
    ) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.executorUuid = executorUuid;
        this.executorName = executorName;
    }

    public @NotNull UUID getTargetUuid() {
        return targetUuid;
    }

    public @NotNull String getTargetName() {
        return targetName;
    }

    public @Nullable UUID getExecutorUuid() {
        return executorUuid;
    }

    public @NotNull String getExecutorName() {
        return executorName;
    }
}
