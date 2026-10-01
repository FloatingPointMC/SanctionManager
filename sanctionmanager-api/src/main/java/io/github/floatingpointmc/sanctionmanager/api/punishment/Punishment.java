package io.github.floatingpointmc.sanctionmanager.api.punishment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface Punishment {
    int getId() throws IllegalStateException;

    int getRelId() throws IllegalStateException;

    @NotNull UUID getTarget();

    @Nullable UUID getExecutor();

    @NotNull LocalDateTime getExecutingTime();

    @Nullable LocalDateTime getExpiryTime();

    boolean isOverridden();

    @Nullable Punishment getOverriddenBy();

    boolean isOverriding();

    @Nullable Punishment getOverriddenPunishment();

    boolean isWithdrawn();

    @Nullable UUID getWithdrawnBy();

    @Nullable String getReason();

    @NotNull Type getType();

    interface Builder {
        Builder executor(@Nullable UUID executor);

        Builder executingTime(@NotNull LocalDateTime executingTime);

        Builder expiryTime(@Nullable LocalDateTime expiryTime);

        Builder reason(@Nullable String reason);

        Punishment build();
    }
}
