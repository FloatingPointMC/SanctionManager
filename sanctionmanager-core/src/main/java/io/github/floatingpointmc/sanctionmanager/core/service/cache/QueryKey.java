package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Immutable cache key for query results.
 * <p>
 * Ensures deterministic equality and hash codes for different query types.
 * All parameters that affect query results are included in the key.
 * </p>
 */
public abstract class QueryKey {
    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    /**
     * Cache key for query(UUID target, QueryOptions options).
     */
    public static final class TargetQuery extends QueryKey {
        private final UUID target;
        private final boolean includeExpired;
        private final boolean includeWithdrawn;
        private final boolean includeOverridden;
        private final Set<String> types;
        private final Long startTime;
        private final Long endTime;

        public TargetQuery(@NotNull UUID target, @NotNull QueryOptions options) {
            this.target = target;
            this.includeExpired = options.includeExpired();
            this.includeWithdrawn = options.includeWithdrawn();
            this.includeOverridden = options.includeOverridden();
            this.types = new TreeSet<>(options.getTypes()); // Normalized sorted set
            this.startTime = options.getStartTime() != null ? options.getStartTime().getTime() : null;
            this.endTime = options.getEndTime() != null ? options.getEndTime().getTime() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TargetQuery)) return false;
            TargetQuery that = (TargetQuery) o;
            return includeExpired == that.includeExpired &&
                    includeWithdrawn == that.includeWithdrawn &&
                    includeOverridden == that.includeOverridden &&
                    target.equals(that.target) &&
                    types.equals(that.types) &&
                    Objects.equals(startTime, that.startTime) &&
                    Objects.equals(endTime, that.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, includeExpired, includeWithdrawn, includeOverridden, types, startTime, endTime);
        }
    }

    /**
     * Cache key for queryAll(QueryOptions options, String... types).
     */
    public static final class AllQuery extends QueryKey {
        private final boolean includeExpired;
        private final boolean includeWithdrawn;
        private final boolean includeOverridden;
        private final Set<String> optionsTypes;
        private final Set<String> varargsTypes;
        private final Long startTime;
        private final Long endTime;

        public AllQuery(@NotNull QueryOptions options, String @NotNull ... types) {
            this.includeExpired = options.includeExpired();
            this.includeWithdrawn = options.includeWithdrawn();
            this.includeOverridden = options.includeOverridden();
            this.optionsTypes = new TreeSet<>(options.getTypes());
            this.varargsTypes = types.length > 0 ? new TreeSet<>(Arrays.asList(types)) : Collections.emptySet();
            this.startTime = options.getStartTime() != null ? options.getStartTime().getTime() : null;
            this.endTime = options.getEndTime() != null ? options.getEndTime().getTime() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AllQuery)) return false;
            AllQuery that = (AllQuery) o;
            return includeExpired == that.includeExpired &&
                    includeWithdrawn == that.includeWithdrawn &&
                    includeOverridden == that.includeOverridden &&
                    optionsTypes.equals(that.optionsTypes) &&
                    varargsTypes.equals(that.varargsTypes) &&
                    Objects.equals(startTime, that.startTime) &&
                    Objects.equals(endTime, that.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(includeExpired, includeWithdrawn, includeOverridden,
                    optionsTypes, varargsTypes, startTime, endTime);
        }
    }

    /**
     * Cache key for queryLatestActive(UUID target, String... types).
     */
    public static final class LatestActiveQuery extends QueryKey {
        private final UUID target;
        private final Set<String> types;

        public LatestActiveQuery(@NotNull UUID target, String @NotNull ... types) {
            this.target = target;
            this.types = new TreeSet<>(Arrays.asList(types));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LatestActiveQuery)) return false;
            LatestActiveQuery that = (LatestActiveQuery) o;
            return target.equals(that.target) && types.equals(that.types);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, types);
        }
    }
}
