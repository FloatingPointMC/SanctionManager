package io.github.floatingpointmc.sanctionmanager.core.factory;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryWrapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * Core implementation of EntryFactory.
 * <p>
 * Creates new pending Entry instances wrapped in {@code EntryWrapper} containing {@code EntrySnapshot}.
 * </p>
 */
@RequiredArgsConstructor
public class EntryFactoryCore implements EntryFactory {
    private final SanctionManagerCore core;

    @Override
    public @NotNull Entry create(
            @NotNull String type,
            @NotNull UUID target,
            @NotNull UUID executor,
            @Nullable String reason,
            @Nullable Duration duration,
            @Nullable JsonObject data
    ) {
        type = type.trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        if (!core.getTypeManager().contains(type)) {
            throw new IllegalArgumentException(String.format("type is not registered(type=%s)", type));
        }
        boolean requireExtraData = core.getTypeManager().requireExtraData(type);
        if (requireExtraData == (data == null)) {
            throw new IllegalArgumentException(String.format("extra data is not compatible(type=%s, requireExtraData=%s, data=%s)", type, requireExtraData, data));
        }
        if (duration != null && duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }

        return new EntryWrapper(new EntrySnapshot(type, target, executor, duration, reason, data));
    }
}
