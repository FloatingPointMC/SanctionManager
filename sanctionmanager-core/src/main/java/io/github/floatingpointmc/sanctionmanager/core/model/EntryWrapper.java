package io.github.floatingpointmc.sanctionmanager.core.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
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
 */
@AllArgsConstructor
public class EntryWrapper implements Entry {
    public Entry entry;

    @Override
    public long getId() {
        return entry.getId();
    }

    @Override
    public long getRelId() {
        return entry.getRelId();
    }

    @Override
    public @NotNull EntryType getType() {
        return entry.getType();
    }

    @Override
    public @NotNull UUID getTarget() {
        return entry.getTarget();
    }

    @Override
    public @NotNull UUID getExecutor() {
        return entry.getExecutor();
    }

    @Override
    public @NotNull Date getExecutingTime() {
        return entry.getExecutingTime();
    }

    @Override
    public @Nullable String getReason() {
        return entry.getReason();
    }

    @Override
    public @Nullable Date getExpiryTime() {
        return entry.getExpiryTime();
    }

    @Override
    public @Nullable UUID getWithdrawnBy() {
        return entry.getWithdrawnBy();
    }

    @Override
    public @Nullable String getWithdrawalReason() {
        return entry.getWithdrawalReason();
    }

    @Override
    public boolean isWithdrawn() {
        return entry.isWithdrawn();
    }

    @Override
    public boolean isOverridden() {
        return entry.isOverridden();
    }

    @Override
    public @Nullable Long getOverriddenBy() {
        return entry.getOverriddenBy();
    }

    @Override
    public @NotNull JsonObject getData() {
        return entry.getData();
    }
}
