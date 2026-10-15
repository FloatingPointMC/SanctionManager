package io.github.floatingpointmc.sanctionmanager.api.factory;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * Factory for creating new {@code Entry} instances.
 * <p>
 * Entries created by this factory are managed by the underlying Core implementation
 * and must be persisted via {@link io.github.floatingpointmc.sanctionmanager.api.management.EntryManager#execute(Entry)}.
 * </p>
 * <p>
 * <strong>Lifecycle:</strong> Newly created entries are in a pending state. Key attributes such as
 * ID, relation ID, and executing time are not assigned until the entry is successfully persisted.
 * Attempting to access these before persistence throws {@code IllegalStateException}.
 * </p>
 */
public interface EntryFactory {
    /**
     * Creates a new entry with optional extended data.
     * <p>
     * The entry is not automatically persisted. To persist, call
     * {@link io.github.floatingpointmc.sanctionmanager.api.management.EntryManager#execute(Entry)} with the returned entry.
     * </p>
     *
     * @param type      the entry type (e.g., "BAN", "MUTE"). Cannot be null, must be non-empty and registered with the Core.
     * @param target    the UUID of the target player or entity. Cannot be null.
     * @param executor  the UUID of the player or system issuing the sanction. Cannot be null.
     * @param reason    the reason for the sanction. May be null.
     * @param duration  the duration of the sanction. {@code null} means permanent (never expires).
     *                  {@code Duration.ZERO} means immediate expiration. Negative durations are rejected.
     * @param data      extended data as a JSON object. May be null, which defaults to an empty object.
     *                  If provided, the object is stored as-is; modifications to the original do not affect the entry.
     *
     * @return a new pending Entry instance
     * @throws NullPointerException if {@code type}, {@code target}, or {@code executor} is null, or data is null when extra data is required
     * @throws IllegalArgumentException if type is empty, unregistered, or has invalid format, or if duration is negative
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    @NotNull Entry create(
            @NotNull String type,
            @NotNull UUID target,
            @NotNull UUID executor,
            @Nullable String reason,
            @Nullable Duration duration,
            @Nullable JsonObject data
    );

    /**
     * Creates a new entry without extended data.
     * <p>
     * Convenience method; equivalent to
     * {@link #create(String, UUID, UUID, String, Duration, JsonObject)} with {@code data = null}.
     * </p>
     *
     * @param type     the entry type. Cannot be null, must be non-empty and registered with the Core.
     * @param target   the UUID of the target player or entity. Cannot be null.
     * @param executor the UUID of the player or system issuing the sanction. Cannot be null.
     * @param reason   the reason for the sanction. May be null.
     * @param duration the duration of the sanction. {@code null} means permanent. {@code Duration.ZERO} means immediate expiration.
     *
     * @return a new pending Entry instance
     * @throws NullPointerException if {@code type}, {@code target}, or {@code executor} is null
     * @throws IllegalArgumentException if type is empty, unregistered, or has invalid format, or if duration is negative
     * @throws IllegalStateException if the Core is not initialized or has been shut down
     */
    default @NotNull Entry create(
            @NotNull String type,
            @NotNull UUID target,
            @NotNull UUID executor,
            @Nullable String reason,
            @Nullable Duration duration
    ) {
        return create(type, target, executor, reason, duration, null);
    }
}
