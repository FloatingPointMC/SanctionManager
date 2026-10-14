package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.UUID;

/**
 * Internal implementation of Entry representing a persisted database record.
 * <p>
 * This class wraps persistent data retrieved from the database. It includes
 * a version field for optimistic locking during concurrent modifications.
 * </p>
 */
public class EntrySnapshot implements Entry {
    private final long id;
    private final long relId;
    private final @NotNull String type;
    private final @NotNull UUID target;
    private final @NotNull UUID executor;
    private final @Nullable Date expiryTime;
    private final @NotNull Date executingTime;
    private final @Nullable String reason;
    private @Nullable JsonObject data;
    private @Nullable UUID withdrawnBy;
    private @Nullable String withdrawReason;
    private boolean overridden;
    private @Nullable Long overriddenBy;
    private int version;

    public EntrySnapshot(long id, long relId, @NotNull String type, @NotNull UUID target, @NotNull UUID executor,
                         @Nullable Date expiryTime, @NotNull Date executingTime, @Nullable String reason,
                         @Nullable JsonObject data, @Nullable UUID withdrawnBy, @Nullable String withdrawReason,
                         boolean overridden, @Nullable Long overriddenBy, int version) {
        this.id = id;
        this.relId = relId;
        this.type = type;
        this.target = target;
        this.executor = executor;
        this.expiryTime = expiryTime;
        this.executingTime = executingTime;
        this.reason = reason;
        this.data = data;
        this.withdrawnBy = withdrawnBy;
        this.withdrawReason = withdrawReason;
        this.overridden = overridden;
        this.overriddenBy = overriddenBy;
        this.version = version;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getRelId() {
        return relId;
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
    public @NotNull Date getExecutingTime() {
        return new Date(executingTime.getTime());
    }

    @Override
    public @Nullable Date getExpiryTime() {
        return expiryTime != null ? new Date(expiryTime.getTime()) : null;
    }

    @Override
    public boolean isWithdrawn() {
        return withdrawnBy != null;
    }

    @Override
    public @Nullable UUID getWithdrawnBy() {
        return withdrawnBy;
    }

    @Override
    public @Nullable String getWithdrawReason() {
        return withdrawReason;
    }

    @Override
    public boolean isOverridden() {
        return overridden;
    }

    @Override
    public @Nullable Long getOverriddenBy() {
        return overriddenBy;
    }

    @Override
    public @NotNull JsonObject getData() {
        return data != null ? new JsonObject().deepCopy() : new JsonObject();
    }

    // Getters and setters for internal usage
    public @Nullable JsonObject getDataInternal() {
        return data;
    }

    public void setData(@Nullable JsonObject data) {
        this.data = data;
    }

    public void setWithdrawnBy(@Nullable UUID withdrawnBy) {
        this.withdrawnBy = withdrawnBy;
    }

    public void setWithdrawReason(@Nullable String withdrawReason) {
        this.withdrawReason = withdrawReason;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setOverridden(boolean overridden) {
        this.overridden = overridden;
    }

    public void setOverriddenBy(@Nullable Long overriddenBy) {
        this.overriddenBy = overriddenBy;
    }
}
