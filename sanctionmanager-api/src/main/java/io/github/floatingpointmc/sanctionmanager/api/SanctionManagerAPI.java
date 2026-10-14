package io.github.floatingpointmc.sanctionmanager.api;

import org.jetbrains.annotations.NotNull;

/**
 * Global entry point for accessing the SanctionManager API.
 * <p>
 * After the Core has been initialized, use {@link #getApi()} to obtain the {@code SanctionManager}
 * for creating and managing sanction entries.
 * </p>
 * <p>
 * <strong>Initialization Contract:</strong> {@code getApi()} throws {@code IllegalStateException}
 * if the Core has not been initialized. Once initialized, the same {@code SanctionManager} instance
 * is returned throughout the Core's lifecycle. Upon Core shutdown and re-initialization,
 * a new {@code SanctionManager} instance is returned.
 * </p>
 */
public final class SanctionManagerAPI {
    private static SanctionManager instance;

    /**
     * Gets the current {@code SanctionManager} instance.
     * <p>
     * This method returns the manager from the currently active Core instance.
     * The same instance is returned as long as the Core remains initialized.
     * </p>
     *
     * @return the SanctionManager, never null
     * @throws IllegalStateException if the Core has not been initialized or has been shut down
     */
    @NotNull
    public static SanctionManager getApi() {
        if (instance == null) {
            throw new IllegalStateException("SanctionManager Core has not been initialized");
        }
        return instance;
    }

    /**
     * Internal: Sets the active SanctionManager instance.
     * <p>
     * This method is called by the Core implementation during initialization.
     * </p>
     *
     * @param manager the SanctionManager to activate
     * @throws NullPointerException if {@code manager} is null
     */
    public static void setApi(@NotNull SanctionManager manager) {
        instance = manager;
    }

    /**
     * Internal: Clears the active SanctionManager instance.
     * <p>
     * This method is called by the Core implementation during shutdown.
     * </p>
     */
    public static void clearApi() {
        instance = null;
    }

    private SanctionManagerAPI() {
        // Utility class, non-instantiable
    }
}