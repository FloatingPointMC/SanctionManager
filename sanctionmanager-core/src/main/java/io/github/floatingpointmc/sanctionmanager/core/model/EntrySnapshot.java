package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import lombok.Getter;
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
 * Getters for type, target, executor, duration and reason are Lombok-generated.
 * </p>
 */
public class EntrySnapshot implements Entry {
    @Getter(onMethod_ = {@Override})
    private final @NotNull EntryType type;
    @Getter(onMethod_ = {@Override})
    private final @NotNull UUID target;
    @Getter(onMethod_ = {@Override})
    private final @NotNull UUID executor;
    @Getter
    private final @Nullable Duration duration;
    @Getter(onMethod_ = {@Override})
    private final @Nullable String reason;
    private final @Nullable JsonObject data;

    /**
     * Creates a pending entry. Arguments are stored as given, without copying or validation.
     *
     * @param type     the entry type
     * @param target   the target UUID
     * @param executor the executor UUID
     * @param duration the duration, or null for a permanent entry
     * @param reason   the reason, or null
     * @param data     the extra data, or null if the type does not support extra data
     */
    public EntrySnapshot(@NotNull EntryType type, @NotNull UUID target, @NotNull UUID executor,
                         @Nullable Duration duration, @Nullable String reason, @Nullable JsonObject data) {
        this.type = type;
        this.target = target;
        this.executor = executor;
        this.duration = duration;
        this.reason = reason;
        this.data = data;
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public long getId() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public long getRelId() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public @Nullable Date getExpiryTime() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public @NotNull Date getExecutingTime() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public boolean isWithdrawn() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public @Nullable UUID getWithdrawnBy() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public @Nullable String getWithdrawalReason() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public boolean isOverridden() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
    }

    /**
     * Not available before persistence.
     *
     * @throws IllegalStateException always
     */
    @Override
    public @Nullable Long getOverriddenBy() throws IllegalStateException {
        throw new IllegalStateException("Entry is not persisted yet.");
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

    /**
     * Gets the stored extra data without copying.
     *
     * @return the live data object, or null if this entry has no extra data
     */
    public @Nullable JsonObject getDataInternal() {
        return data;
    }
}
