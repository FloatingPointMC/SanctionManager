package io.github.floatingpointmc.sanctionmanager.api.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable query criteria for filtering entry results.
 * <p>
 * {@code QueryOptions} allows filtering entries by state (expired, withdrawn, overridden), type,
 * and time range. It is immutable; methods return a new instance with the modification applied.
 * </p>
 * <p>
 * <strong>State Filtering:</strong> Three boolean flags control which entry states are included:
 * <ul>
 *   <li>{@code includeExpired} - includes entries whose {@code expiryTime} has passed</li>
 *   <li>{@code includeWithdrawn} - includes entries that have been withdrawn</li>
 *   <li>{@code includeOverridden} - includes entries marked with the permanent overridden flag</li>
 * </ul>
 * Flags are combined with OR semantics: if any flag is enabled, entries matching that state are included.
 * By default, all three flags are {@code false}, returning only active entries.
 * </p>
 * <p>
 * <strong>Type Filtering:</strong> If types are specified, only entries of those types are returned.
 * Types use exact string matching (case-sensitive, no whitespace trimming).
 * </p>
 * <p>
 * <strong>Time Range:</strong> Entries can be filtered by executing time using a half-open interval [start, end).
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QueryOptions {
    private boolean includeExpired = false;
    private boolean includeWithdrawn = false;
    private boolean includeOverridden = false;
    private final Set<String> types = new HashSet<>();
    private @Nullable Date startTime;
    private @Nullable Date endTime;

    /**
     * Creates a new {@code QueryOptions} builder with default settings.
     * <p>
     * Default: only active entries, no type or time restrictions.
     * </p>
     *
     * @return a new QueryOptions instance
     */
    @NotNull
    public static QueryOptions create() {
        return new QueryOptions();
    }

    /**
     * Indicates whether expired entries are included.
     *
     * @return {@code true} if expired entries are included
     */
    public boolean includeExpired() {
        return includeExpired;
    }

    /**
     * Sets whether to include expired entries.
     * <p>
     * Returns a new {@code QueryOptions} instance with the setting applied.
     * </p>
     *
     * @param includeExpired {@code true} to include expired entries
     * @return a new QueryOptions with the updated setting
     */
    @NotNull
    public QueryOptions includeExpired(boolean includeExpired) {
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = includeExpired;
        copy.includeWithdrawn = this.includeWithdrawn;
        copy.includeOverridden = this.includeOverridden;
        copy.types.addAll(this.types);
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    /**
     * Indicates whether withdrawn entries are included.
     *
     * @return {@code true} if withdrawn entries are included
     */
    public boolean includeWithdrawn() {
        return includeWithdrawn;
    }

    /**
     * Sets whether to include withdrawn entries.
     * <p>
     * Returns a new {@code QueryOptions} instance with the setting applied.
     * </p>
     *
     * @param includeWithdrawn {@code true} to include withdrawn entries
     * @return a new QueryOptions with the updated setting
     */
    @NotNull
    public QueryOptions includeWithdrawn(boolean includeWithdrawn) {
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = this.includeExpired;
        copy.includeWithdrawn = includeWithdrawn;
        copy.includeOverridden = this.includeOverridden;
        copy.types.addAll(this.types);
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    /**
     * Indicates whether overridden entries are included.
     *
     * @return {@code true} if overridden entries are included
     */
    public boolean includeOverridden() {
        return includeOverridden;
    }

    /**
     * Sets whether to include entries with the permanent overridden flag.
     * <p>
     * Returns a new {@code QueryOptions} instance with the setting applied.
     * </p>
     *
     * @param includeOverridden {@code true} to include overridden entries
     * @return a new QueryOptions with the updated setting
     */
    @NotNull
    public QueryOptions includeOverridden(boolean includeOverridden) {
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = this.includeExpired;
        copy.includeWithdrawn = this.includeWithdrawn;
        copy.includeOverridden = includeOverridden;
        copy.types.addAll(this.types);
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    /**
     * Gets the set of types to filter by.
     * <p>
     * An empty set means no type restriction (all types are included).
     * </p>
     *
     * @return an unmodifiable set of type names
     */
    @NotNull
    public Set<String> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    /**
     * Sets the types to filter by.
     * <p>
     * Replaces the current type set. Duplicate types are automatically removed.
     * Each type must be non-empty and registered.
     * </p>
     *
     * @param types the type names to include. Null elements cause {@code NullPointerException}.
     * @return a new QueryOptions with the updated types
     * @throws NullPointerException if {@code types} is null or contains null
     * @throws IllegalArgumentException if any type is empty or unregistered
     */
    @NotNull
    public QueryOptions types(String @NotNull ... types) {
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = this.includeExpired;
        copy.includeWithdrawn = this.includeWithdrawn;
        copy.includeOverridden = this.includeOverridden;
        for (String type : types) {
            if (type.trim().isEmpty()) {
                throw new IllegalArgumentException("type cannot be empty");
            }
            copy.types.add(type);
        }
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    /**
     * Adds additional types to filter by.
     * <p>
     * Does not replace existing types; instead adds to them. Duplicates are ignored.
     * </p>
     *
     * @param type the type name to add. Cannot be null or empty.
     * @return a new QueryOptions with the type added
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code type} is empty or unregistered
     */
    @NotNull
    public QueryOptions addType(@NotNull String type) {
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = this.includeExpired;
        copy.includeWithdrawn = this.includeWithdrawn;
        copy.includeOverridden = this.includeOverridden;
        copy.types.addAll(this.types);
        copy.types.add(type);
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    /**
     * Gets the start of the time range (inclusive).
     *
     * @return the start time, or {@code null} if not set
     */
    @Nullable
    public Date getStartTime() {
        return startTime != null ? new Date(startTime.getTime()) : null;
    }

    /**
     * Gets the end of the time range (exclusive).
     *
     * @return the end time, or {@code null} if not set
     */
    @Nullable
    public Date getEndTime() {
        return endTime != null ? new Date(endTime.getTime()) : null;
    }

    /**
     * Sets the time range [start, end) for entry executing times.
     * <p>
     * Only entries with {@code executingTime} in the range are included.
     * If {@code start >= end}, the query returns empty results (after other filters are applied).
     * </p>
     *
     * @param start the start of the range (inclusive), or {@code null} for no lower bound
     * @param end   the end of the range (exclusive), or {@code null} for no upper bound
     * @return a new QueryOptions with the time range set
     */
    @NotNull
    public QueryOptions timeRange(@Nullable Date start, @Nullable Date end) {
        QueryOptions copy = new QueryOptions();
        copy.includeExpired = this.includeExpired;
        copy.includeWithdrawn = this.includeWithdrawn;
        copy.includeOverridden = this.includeOverridden;
        copy.types.addAll(this.types);
        copy.startTime = start != null ? new Date(start.getTime()) : null;
        copy.endTime = end != null ? new Date(end.getTime()) : null;
        return copy;
    }
}
