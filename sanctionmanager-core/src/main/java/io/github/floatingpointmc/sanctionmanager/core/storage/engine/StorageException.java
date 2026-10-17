package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * Thrown when a storage operation fails.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
