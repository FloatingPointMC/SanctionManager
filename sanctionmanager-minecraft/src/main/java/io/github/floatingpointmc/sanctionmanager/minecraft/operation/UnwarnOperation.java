package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents an unwarn operation to be executed.
 * <p>
 * This is a platform-independent business operation object used for
 * communication between Command layer, Service layer, and Bridge/Proxy transport.
 * <p>
 * Immutable and serializable for network transmission.
 * Only contains UUIDs for identity - names are resolved at presentation layer.
 */
public final class UnwarnOperation implements Serializable {
    private static final long serialVersionUID = 2L;

    private final @NotNull UUID targetUuid;
    private final @Nullable UUID executorUuid;

    public UnwarnOperation(
            @NotNull UUID targetUuid,
            @Nullable UUID executorUuid
    ) {
        this.targetUuid = targetUuid;
        this.executorUuid = executorUuid;
    }

    public @NotNull UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable UUID getExecutorUuid() {
        return executorUuid;
    }
}
