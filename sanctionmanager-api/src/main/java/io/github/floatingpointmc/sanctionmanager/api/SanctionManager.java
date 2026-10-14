package io.github.floatingpointmc.sanctionmanager.api;

import io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory;
import io.github.floatingpointmc.sanctionmanager.api.management.EntryManager;
import org.jetbrains.annotations.NotNull;

/**
 * Primary interface for accessing sanction management services.
 * <p>
 * {@code SanctionManager} is obtained via {@link SanctionManagerAPI#getApi()} after the Core has been
 * initialized. It provides access to the {@code EntryFactory} for creating new entries and {@code EntryManager}
 * for persisting and querying entries.
 * </p>
 * <p>
 * <strong>Stability:</strong> Within a single Core lifecycle (from initialization to shutdown), the same
 * {@code SanctionManager} instance is returned. Upon Core shutdown and re-initialization, a new {@code SanctionManager}
 * instance is returned.
 * </p>
 */
public interface SanctionManager {
    /**
     * Gets the {@code EntryManager} for persisting and querying entries.
     * <p>
     * The returned manager is stable for the duration of the current Core lifecycle.
     * After Core shutdown and re-initialization, a new manager instance is obtained via a new
     * {@code SanctionManager} instance.
     * </p>
     *
     * @return the EntryManager, never null
     */
    @NotNull EntryManager getEntryManager();

    /**
     * Gets the {@code EntryFactory} for creating new pending entries.
     * <p>
     * The returned factory is stable for the duration of the current Core lifecycle.
     * Entries created by this factory must be persisted via {@link EntryManager#execute(Entry)}.
     * </p>
     *
     * @return the EntryFactory, never null
     */
    @NotNull EntryFactory getEntryFactory();
}
