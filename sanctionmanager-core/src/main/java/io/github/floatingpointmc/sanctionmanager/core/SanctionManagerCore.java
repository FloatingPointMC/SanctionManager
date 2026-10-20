package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.service.CacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.EntryServiceCore;
import io.github.floatingpointmc.sanctionmanager.core.service.StorageService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Core implementation of SanctionManager.
 * <p>
 * This class manages the lifecycle of the sanction management system and provides access
 * to the entry factory, entry service, storage service, and cache service.
 * </p>
 */
@Slf4j
public class SanctionManagerCore implements SanctionManager {
    private final @NotNull EntryServiceCore entryService;
    private final @NotNull EntryFactoryCore entryFactory;
    private final @NotNull StorageService storageService;
    private final @NotNull CacheService cacheService;
    private @Nullable TypeManager typeManager;
    /**
     * The configuration this Core was created with.
     */
    @Getter
    private final @NotNull CoreConfig config;

    private final ArrayList<EntryType> entryTypes = new ArrayList<>();
    /**
     * Whether the Core is currently initialized. Set by {@link #initialize()} and cleared by {@link #shutdown()}.
     */
    @Getter
    private boolean initialized = false;

    /**
     * Creates a new SanctionManagerCore instance and constructs its services.
     *
     * @param config the Core configuration
     * @throws IllegalArgumentException if the cache configuration is invalid (see {@link CacheService})
     */
    private SanctionManagerCore(@NotNull CoreConfig config) {
        this.config = config;
        entryService = new EntryServiceCore(this);
        entryFactory = new EntryFactoryCore(this);
        storageService = new StorageService(this);
        cacheService = new CacheService(this);
    }

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
    public static SanctionManagerCore create(@NotNull CoreConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        return new SanctionManagerCore(config);
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
    public SanctionManagerCore types(EntryType @NotNull ... types) {
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
    public SanctionManagerCore addTypes(EntryType @NotNull ... types) {
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
    public void initialize() {
        synchronized (this) {
            if (initialized) {
                throw new IllegalStateException("SanctionManagerCore is already initialized");
            }
            // Create typeManager before initializing storage, as storage needs it
            typeManager = new TypeManager(entryTypes);
            storageService.initialize();

            // Restore relId counters from persistent storage
            entryService.restoreRelIdCounters(storageService.getStorageService());

            initialized = true;
            SanctionManagerAPI.setApi(this);
        }
    }

    /**
     * Shuts down the Core and clears the active API instance.
     * <p>
     * After shutdown, service operations throw {@code IllegalStateException}. Storage and cache backend
     * resources are released.
     * </p>
     */
    public void shutdown() throws IOException {
        synchronized (this) {
            storageService.shutdown();
            initialized = false;
            SanctionManagerAPI.clearApi();
            cacheService.shutdown();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the Core is not initialized
     */
    @Override
    public @NotNull EntryService getEntryService() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryService;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the Core is not initialized
     */
    @Override
    public @NotNull EntryFactory getEntryFactory() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryFactory;
    }

    /**
     * Gets the storage service facade.
     *
     * @return the storage service
     * @throws IllegalStateException if the Core is not initialized
     */
    public @NotNull StorageService getStorageService() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return storageService;
    }

    /**
     * Gets the cache service. Unlike the other service getters, this does not check initialization.
     *
     * @return the cache service
     */
    public @NotNull CacheService getCacheService() {
        return cacheService;
    }

    /**
     * Gets the type manager built by the most recent call to {@link #initialize()}.
     * <p>
     * The type manager remains available after {@link #shutdown()}.
     * </p>
     *
     * @return the type manager
     * @throws IllegalStateException if {@link #initialize()} has never been called
     */
    public @NotNull TypeManager getTypeManager() {
        if (typeManager == null) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return typeManager;
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
