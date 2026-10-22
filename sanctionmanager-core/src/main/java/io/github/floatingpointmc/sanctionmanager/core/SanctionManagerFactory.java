package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.service.CacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SanctionManagerFactory {
    private final CoreConfig coreConfig;
    private final ArrayList<EntryType> entryTypes = new ArrayList<>();
    private volatile boolean initialized = false;

    /**
     * Creates a new SanctionManagerCore instance with the specified configuration.
     * <p>
     * After creation, call {@link #types(EntryType...)} or {@link #addTypes(EntryType...)}
     * to register entry types, then call {@link #initialize()} to activate the Core.
     * </p>
     *
     * @param config the Core configuration. Cannot be null.
     * @return a new uninitialized Core instance
     * @throws NullPointerException if {@code config} is null
     * @throws IllegalArgumentException if the cache configuration is invalid (see {@link CacheService})
     */
    public static SanctionManagerFactory create(@NotNull CoreConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        return new SanctionManagerFactory(config);
    }



    /**
     * Replaces the list of entry types to register on {@link #initialize()}.
     *
     * @param types the entry types. Cannot be null and must not contain null or duplicate elements.
     * @return this Core, for chaining
     * @throws IllegalStateException if the Core is currently initialized
     * @throws NullPointerException if {@code types} or any element is null
     * @throws IllegalArgumentException if {@code types} contains duplicates (compared by identifier)
     */
    public SanctionManagerFactory types(EntryType @NotNull ... types) {
        validateTypes(types, Collections.emptyList());
        entryTypes.clear();
        entryTypes.addAll(Arrays.asList(types));
        return this;
    }

    /**
     * Appends entry types to the list of types to register on {@link #initialize()}.
     * <p>
     * Duplicates are only detected within the given array; types added by earlier calls are not
     * compared. If an identifier ends up registered more than once, the last occurrence is used.
     * </p>
     *
     * @param types the entry types to add. Cannot be null and must not contain null or duplicate elements.
     * @return this Core, for chaining
     * @throws IllegalStateException if the Core is currently initialized
     * @throws NullPointerException if {@code types} or any element is null
     * @throws IllegalArgumentException if {@code types} itself contains duplicates (compared by identifier)
     */
    public SanctionManagerFactory addTypes(EntryType @NotNull ... types) {
        validateTypes(types, Collections.emptyList());
        entryTypes.addAll(Arrays.asList(types));
        return this;
    }



    /**
     * Initializes the Core and registers it as the active API instance.
     * <p>
     * This method must be called before using any services. It builds the {@link TypeManager} from the
     * registered types, initializes storage, and registers this Core with the global {@code SanctionManagerAPI}.
     * </p>
     *
     * @throws IllegalStateException if the Core has already been initialized
     * @throws UnsupportedOperationException if database storage is configured (not implemented)
     * @throws IllegalArgumentException if the binary file configuration is invalid
     * @throws RuntimeException if storage initialization fails
     */
    public SanctionManagerCore initialize() {
        synchronized (this) {
            if (initialized) {
                throw new IllegalStateException("SanctionManagerCore is already initialized");
            }
            // Create Core
            initialized = true;
            return new SanctionManagerCore(coreConfig, entryTypes);
        }
    }

    private void validateTypes(
            final EntryType @NotNull [] types,
            final Collection<EntryType> existing
    ) {
        if (initialized) {
            throw new IllegalStateException("SanctionManagerCore is already initialized");
        }
        final HashSet<EntryType> seen = new HashSet<>(existing);

        for (final EntryType type : types) {
            Objects.requireNonNull(type, "Entry type cannot be null");

            if (!seen.add(type)) {
                throw new IllegalArgumentException(
                        "Duplicate entry type: " + type.getIdentifier()
                );
            }
        }
    }
}
