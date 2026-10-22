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

import java.io.IOException;
import java.util.List;

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
    private final @NotNull TypeManager typeManager;
    /**
     * The configuration this Core was created with.
     */
    @Getter
    private final @NotNull CoreConfig config;
    @Getter
    private boolean initialized;

    /**
     * Creates a new SanctionManagerCore instance and constructs its services.
     *
     * @param config the Core configuration
     * @throws IllegalArgumentException if the cache configuration is invalid (see {@link CacheService})
     */
    SanctionManagerCore(@NotNull CoreConfig config, @NotNull List<EntryType> entryTypes) {
        initialized = false;
        this.config = config;
        entryService = new EntryServiceCore(this);
        entryFactory = new EntryFactoryCore(this);
        storageService = new StorageService(this);
        cacheService = new CacheService(this);

        typeManager = new TypeManager(entryTypes);
        storageService.initialize();
        initialized = true;
        SanctionManagerAPI.setApi(this);
    }

    /**
     * Shuts down the Core and clears the active API instance.
     * <p>
     * After shutdown, service operations throw {@code IllegalStateException}. Storage and cache backend
     * resources are released.
     * </p>
     * <p>
     * CF-03 FIX: Always clears state and closes cache even if storage shutdown fails.
     * </p>
     *
     * @throws IOException if storage shutdown fails
     */
    public void shutdown() throws IOException {
        synchronized (this) {
            IOException storageException = null;
            RuntimeException cacheException = null;

            try {
                storageService.shutdown();
            } catch (IOException e) {
                storageException = e;
            }

            // Always clear state even if storage fails
            SanctionManagerAPI.clearApi();

            try {
                cacheService.shutdown();
            } catch (RuntimeException e) {
                cacheException = e;
            }

            // Throw the first exception, with the second suppressed if both failed
            if (storageException != null) {
                if (cacheException != null) {
                    storageException.addSuppressed(cacheException);
                }
                throw storageException;
            } else if (cacheException != null) {
                throw cacheException;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull EntryService getEntryService() {
        return entryService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull EntryFactory getEntryFactory() {
        return entryFactory;
    }

    /**
     * Gets the storage service facade.
     *
     * @return the storage service
     */
    public @NotNull StorageService getStorageService() {
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
     * Gets the type manager.
     * <p>
     * The type manager remains available after {@link #shutdown()}.
     * </p>
     *
     * @return the type manager
     */
    public @NotNull TypeManager getTypeManager() {
        return typeManager;
    }
}
