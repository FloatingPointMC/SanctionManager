package io.github.floatingpointmc.sanctionmanager.api.exceptions;

/**
 * Thrown when concurrent modification of the same entry is detected.
 * <p>
 * This exception indicates that another thread or process has modified an entry while this thread
 * was also attempting to modify it. It employs optimistic locking semantics: the entry is not modified,
 * and the caller must decide whether to retry, reload the entry, or take other action.
 * </p>
 * <p>
 * Unlike {@code PersistenceException}, this exception directly extends {@code RuntimeException}
 * to distinguish concurrent conflicts from other persistence failures.
 * </p>
 */
public class ConcurrentDataModificationException extends RuntimeException {
    /**
     * Constructs a ConcurrentDataModificationException with the given message.
     *
     * @param message the detail message
     */
    public ConcurrentDataModificationException(String message) {
        super(message);
    }

    /**
     * Constructs a ConcurrentDataModificationException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConcurrentDataModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
