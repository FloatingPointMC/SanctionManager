package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when a required entry is not found in the database.
 * <p>
 * This exception is thrown when an operation expects a specific entry to exist (e.g., updating,
 * withdrawing, or deleting by ID) but the entry has been physically deleted or never existed.
 * </p>
 */
public class EntryNotFoundException extends PersistenceException {
    /**
     * Constructs an EntryNotFoundException with the given message.
     *
     * @param message the detail message
     */
    public EntryNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs an EntryNotFoundException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public EntryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
