package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.service.EntryServiceCore;
import io.github.floatingpointmc.sanctionmanager.core.service.StorageService;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * Core implementation of SanctionManager.
 * <p>
 * This class manages the lifecycle of the sanction management system and provides access
 * to entry factory and manager services.
 * </p>
 */
public class SanctionManagerCore implements SanctionManager {
    private final @NotNull EntryService entryService;
    private final @NotNull EntryFactory entryFactory;
    private final @NotNull StorageService storageService;
    @Getter
    private final @NotNull TypeManager typeManager;
    @Getter
    private final @NotNull CoreConfig config;
    /**
     * Checks if the Core is initialized.
     */
    @Getter
    private boolean initialized = false;

    /**
     * Creates a new SanctionManagerCore instance.
     */
    private SanctionManagerCore(@NotNull CoreConfig config) {
        this.entryService = new EntryServiceCore(this);
        this.entryFactory = new EntryFactoryCore(this);
        this.typeManager = new TypeManager(new HashMap<>());
        this.config = config;
        StorageConfig storageConfig = config.getStorageConfig();
        storageService = new StorageService(this, storageConfig);
    }

    /**
     * Initializes the Core and registers it as the active API instance.
     * <p>
     * This method must be called before using any services. It registers this Core
     * with the global {@code SanctionManagerAPI}.
     * </p>
     *
     * @throws IllegalStateException if the Core has already been initialized
     */
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("SanctionManagerCore is already initialized");
        }
        initialized = true;
        SanctionManagerAPI.setApi(this);
    }

    /**
     * Shuts down the Core and clears the active API instance.
     * <p>
     * After shutdown, all services will throw {@code IllegalStateException}.
     * </p>
     */
    public void shutdown() {
        initialized = false;
        SanctionManagerAPI.clearApi();
    }

    @Override
    public @NotNull EntryService getEntryService() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryService;
    }

    @Override
    public @NotNull EntryFactory getEntryFactory() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryFactory;
    }

    public @NotNull StorageService getStorageService() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return storageService;
    }
}
