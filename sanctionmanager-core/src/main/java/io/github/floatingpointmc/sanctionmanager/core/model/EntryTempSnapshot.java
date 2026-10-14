package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Internal implementation of Entry representing a pending (not yet persisted) entry.
 * <p>
 * This class wraps the initial data provided by the caller before persistence.
 * Attempting to access persistent-only fields (id, relId, executingTime, etc.) throws {@code IllegalStateException}.
 * </p>
 */
public class EntryTempSnapshot implements Entry {
    private final @NotNull String type;
    private final @NotNull UUID target;
    private final @NotNull UUID executor;
    private final @Nullable Duration duration;
    private final @Nullable String reason;
    private final @Nullable JsonObject data;

    public EntryTempSnapshot(@NotNull String type, @NotNull UUID target, @NotNull UUID executor,
                             @Nullable Duration duration, @Nullable String reason, @Nullable JsonObject data) {
        this.type = type;
        this.target = target;
        this.executor = executor;
        this.duration = duration;
        this.reason = reason;
        this.data = data;
    }

    @Override
    public long getId() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public long getRelId() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @NotNull String getType() {
        return type;
    }

    @Override
    public @NotNull UUID getTarget() {
        return target;
    }

    @Override
    public @NotNull UUID getExecutor() {
        return executor;
    }

    @Override
    public @Nullable String getReason() {
        return reason;
    }

    @Override
    public @Nullable Date getExpiryTime() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @NotNull Date getExecutingTime() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public boolean isWithdrawn() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @Nullable UUID getWithdrawnBy() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @Nullable String getWithdrawReason() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public boolean isOverridden() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @Nullable Long getOverriddenBy() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    @Override
    public @NotNull JsonObject getData() {
        return data != null ? new JsonObject().deepCopy() : new JsonObject();
    }

    // Getters for internal usage
    public @Nullable Duration getDuration() {
        return duration;
    }

    public @Nullable JsonObject getDataInternal() {
        return data;
    }
}
