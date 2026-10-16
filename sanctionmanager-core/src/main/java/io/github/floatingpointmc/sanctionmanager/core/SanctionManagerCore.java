package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.SanctionManager;
import io.github.floatingpointmc.sanctionmanager.api.SanctionManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.service.EntryServiceCore;
import io.github.floatingpointmc.sanctionmanager.core.service.StorageService;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    private @Nullable TypeManager typeManager;
    @Getter
    private final @NotNull CoreConfig config;

    private final ArrayList<EntryType> entryTypes = new ArrayList<>();
    /**
     * Checks if the Core is initialized.
     */
    @Getter
    private boolean initialized = false;

    /**
     * Creates a new SanctionManagerCore instance.
     */
    private SanctionManagerCore(@NotNull CoreConfig config) {
        this.config = config;
        entryService = new EntryServiceCore(this);
        entryFactory = new EntryFactoryCore(this);
        storageService = new StorageService(this);
    }

    public SanctionManagerCore types(EntryType @NotNull ... types) {
        validateTypes(types, Collections.emptyList());
        entryTypes.clear();
        entryTypes.addAll(Arrays.asList(types));
        return this;
    }

    public SanctionManagerCore addTypes(EntryType @NotNull ... types) {
        validateTypes(types, Collections.emptyList());
        entryTypes.addAll(Arrays.asList(types));
        return this;
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
        synchronized (this) {
            if (initialized) {
                throw new IllegalStateException("SanctionManagerCore is already initialized");
            }
            storageService.initialize();
            typeManager = new TypeManager(entryTypes);
            initialized = true;
            SanctionManagerAPI.setApi(this);
        }
    }

    /**
     * Shuts down the Core and clears the active API instance.
     * <p>
     * After shutdown, all services will throw {@code IllegalStateException}.
     * </p>
     */
    public void shutdown() {
        synchronized (this) {
//            storageService.shutdown();
            initialized = false;
            SanctionManagerAPI.clearApi();
        }
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
