package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * Thrown when a storage operation fails.
 */
public class StorageException extends RuntimeException {
    /**
     * Creates a storage exception with a message.
     *
     * @param message the detail message
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates a storage exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
