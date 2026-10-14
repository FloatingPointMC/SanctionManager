package io.github.floatingpointmc.sanctionmanager.core.factory;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryTempSnapshot;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * Core implementation of EntryFactory.
 * <p>
 * Creates new pending Entry instances wrapped in {@code EntryWrapper} containing {@code EntryTempSnapshot}.
 * </p>
 */
public class EntryFactoryCore implements EntryFactory {
    @Override
    public @NotNull Entry create(
            @NotNull String type,
            @NotNull UUID target,
            @NotNull UUID executor,
            @Nullable String reason,
            @Nullable Duration duration,
            @Nullable JsonObject data
    ) {
        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }
        if (target == null) {
            throw new NullPointerException("target cannot be null");
        }
        if (executor == null) {
            throw new NullPointerException("executor cannot be null");
        }
        if (type.isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        if (duration != null && duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }

        return new EntryWrapper(new EntryTempSnapshot(type, target, executor, duration, reason, data));
    }
}
