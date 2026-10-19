package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.UUID;

/**
 * Wrapper that delegates to an underlying Entry implementation.
 * <p>
 * This allows the Core to change the underlying implementation (from {@code EntrySnapshot}
 * to {@code EntryPersisted}) while maintaining the same wrapper object identity.
 * </p>
 * <p>
 * All {@link Entry} methods delegate to {@link #entry}, so their results and exceptions are those of the
 * current delegate. The constructor is Lombok-generated and takes the initial delegate.
 * </p>
 */
@AllArgsConstructor
public class EntryWrapperImpl implements EntryWrapper {
    /**
     * The current delegate. This field is public and mutable; the Core replaces it with an
     * {@code EntryPersisted} after the entry is executed.
     */
    public Entry entry;

    /** {@inheritDoc} */
    @Override
    public long getId() {
        return entry.getId();
    }

    /** {@inheritDoc} */
    @Override
    public long getRelId() {
        return entry.getRelId();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull EntryType getType() {
        return entry.getType();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull UUID getTarget() {
        return entry.getTarget();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull UUID getExecutor() {
        return entry.getExecutor();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Date getExecutingTime() {
        return entry.getExecutingTime();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getReason() {
        return entry.getReason();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Date getExpiryTime() {
        return entry.getExpiryTime();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable UUID getWithdrawnBy() {
        return entry.getWithdrawnBy();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getWithdrawalReason() {
        return entry.getWithdrawalReason();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWithdrawn() {
        return entry.isWithdrawn();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOverridden() {
        return entry.isOverridden();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Long getOverriddenBy() {
        return entry.getOverriddenBy();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull JsonObject getData() {
        return entry.getData();
    }

    public Entry unwrap() {
        if (entry instanceof EntryWrapperImpl) {
            return ((EntryWrapperImpl) entry).unwrap();
        }
        return entry;
    }
}
