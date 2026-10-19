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
 * Internal implementation of Entry representing a persisted storage record.
 * <p>
 * This class holds persistent data written to or read from storage. Getters for the API properties are
 * Lombok-generated; {@code data}, {@code withdrawnBy}, {@code withdrawalReason}, {@code overridden},
 * {@code overriddenBy} and {@code version} also have Lombok-generated setters that mutate this instance.
 * </p>
 * <p>
 * The {@code version} field is carried through copies but is never incremented or checked. Neither
 * {@code version} nor {@code overridden} is written by the binary record format; decoded records have
 * {@code version} 0 and {@code overridden} {@code false}.
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

    /**
     * Creates a persisted entry. Arguments are stored as given, without copying or validation.
     *
     * @param id             the unique ID
     * @param relId          the type-relative ID
     * @param type           the entry type
     * @param target         the target UUID
     * @param executor       the executor UUID
     * @param expiryTime     the expiry time, or null if the entry never expires
     * @param executingTime  the time of execution
     * @param reason         the reason, or null
     * @param data           the extra data, or null if the type does not support extra data
     * @param withdrawnBy    the UUID of the withdrawing user, or null if not withdrawn
     * @param withdrawReason the withdrawal reason, or null
     * @param overridden     whether the entry is overridden
     * @param overriddenBy   the ID of the overriding entry, or null
     * @param version        the version number (currently unused)
     */
    public EntryPersisted(long id, long relId, @NotNull EntryType type, @NotNull UUID target, @NotNull UUID executor,
                          @Nullable Date expiryTime, @NotNull Date executingTime, @Nullable String reason,
                          @Nullable JsonObject data, @Nullable UUID withdrawnBy, @Nullable String withdrawReason,
                          boolean overridden, @Nullable Long overriddenBy, int version) {
        this.id = id;
        this.relId = relId;
        this.type = type;
        this.target = target;
        this.executor = executor;
        this.expiryTime = expiryTime != null ? new Date(expiryTime.getTime()) : null;
        this.executingTime = new Date(executingTime.getTime());
        this.reason = reason;
        this.data = data;
        this.withdrawnBy = withdrawnBy;
        this.withdrawalReason = withdrawReason;
        this.overridden = overridden;
        this.overriddenBy = overriddenBy;
        this.version = version;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a copy of the stored date. Note that {@link #getExecutingTime()} also returns a copy.
     * </p>
     */
    @Override
    public @Nullable Date getExpiryTime() {
        return expiryTime != null ? new Date(expiryTime.getTime()) : null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a copy of the stored date to prevent external mutation.
     * </p>
     */
    @Override
    public @NotNull Date getExecutingTime() {
        return new Date(executingTime.getTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWithdrawn() {
        return withdrawnBy != null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a deep copy of the stored data.
     * </p>
     *
     * @throws IllegalStateException if this entry has no extra data
     */
    @Override
    public @NotNull JsonObject getData() {
        if (data == null)
            throw new IllegalStateException("Extra data is not enabled for this entry(type=" + getType() + ")");
        return data.deepCopy();
    }

    // Getter for internal usage
    /**
     * Gets the stored extra data without copying.
     *
     * @return the live data object, or null if this entry has no extra data
     */
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
