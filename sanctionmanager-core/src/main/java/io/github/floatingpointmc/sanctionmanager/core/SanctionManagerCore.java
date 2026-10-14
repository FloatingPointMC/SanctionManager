package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.management.EntryManager;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.EntryManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
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
    private final EntryManager entryManager;
    private final EntryFactory entryFactory;
    @Getter
    private final TypeManager typeManager;
    private boolean initialized = false;

    /**
     * Creates a new SanctionManagerCore instance.
     */
    public SanctionManagerCore() {
        this.entryManager = new EntryManagerCore(this);
        this.entryFactory = new EntryFactoryCore();
        this.typeManager = new TypeManager(new HashMap<String, Boolean>());
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

    /**
     * Checks if the Core is initialized.
     *
     * @return {@code true} if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public @NotNull EntryManager getEntryManager() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryManager;
    }

    @Override
    public @NotNull EntryFactory getEntryFactory() {
        if (!initialized) {
            throw new IllegalStateException("SanctionManagerCore is not initialized");
        }
        return entryFactory;
    }
}
