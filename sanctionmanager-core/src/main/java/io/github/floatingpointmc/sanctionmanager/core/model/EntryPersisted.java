package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import lombok.Getter;
import lombok.Setter;
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
public class EntryPersisted implements Entry {
    @Getter(onMethod_ = {@Override})
    private final long id, relId;
    @Getter(onMethod_ = {@Override})
    private final @NotNull EntryType type;
    @Getter(onMethod_ = {@Override})
    private final @NotNull UUID target;
    @Getter(onMethod_ = {@Override})
    private final @NotNull UUID executor;
    private final @Nullable Date expiryTime;
    @Getter(onMethod_ = {@Override})
    private final @NotNull Date executingTime;
    @Getter(onMethod_ = {@Override})
    private final @Nullable String reason;
    @Setter
    private @Nullable JsonObject data;
    @Getter(onMethod_ = {@Override})
    @Setter
    private @Nullable UUID withdrawnBy;
    @Getter(onMethod_ = {@Override})
    @Setter
    private @Nullable String withdrawalReason;
    @Getter(onMethod_ = {@Override})
    @Setter
    private boolean overridden;
    @Getter(onMethod_ = {@Override})
    @Setter
    private @Nullable Long overriddenBy;
    @Getter
    @Setter
    private int version;

    public EntryPersisted(long id, long relId, @NotNull EntryType type, @NotNull UUID target, @NotNull UUID executor,
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
        this.withdrawalReason = withdrawReason;
        this.overridden = overridden;
        this.overriddenBy = overriddenBy;
        this.version = version;
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
    public @NotNull JsonObject getData() {
        if (data == null)
            throw new IllegalStateException("Extra data is not enabled for this entry(type=" + getType() + ")");
        return data.deepCopy();
    }

    // Getter for internal usage
    public @Nullable JsonObject getDataInternal() {
        return data;
    }

    /**
     * Creates a defensive deep copy of this EntryPersisted.
     * <p>
     * All mutable fields (Date, JsonObject) are copied to prevent external mutation.
     * This ensures that query results remain independent snapshots.
     * </p>
     *
     * @return a new independent copy of this entry
     */
    public EntryPersisted defensiveCopy() {
        return new EntryPersisted(
            id,
            relId,
            type,
            target,
            executor,
            expiryTime != null ? new Date(expiryTime.getTime()) : null,
            new Date(executingTime.getTime()),
            reason,
            data != null ? data.deepCopy() : null,
            withdrawnBy,
            withdrawalReason,
            overridden,
            overriddenBy,
            version
        );
    }
}
