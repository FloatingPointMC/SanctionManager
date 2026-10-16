package io.github.floatingpointmc.sanctionmanager.core.service.storage;

/**
 * Interface for storage layer implementations.
 * <p>
 * Concrete implementations handle persistence via database or binary file storage.
 * </p>
 */
public interface IStorageService {
    /**
     * Initializes the storage implementation.
     * <p>
     * This method sets up the persistence layer, such as creating database connections,
     * initializing file resources, or preparing schemas.
     * </p>
     */
    void initialize();
}
