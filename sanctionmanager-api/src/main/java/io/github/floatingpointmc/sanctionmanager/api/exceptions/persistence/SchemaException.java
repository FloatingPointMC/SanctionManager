package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when the database schema is missing, incompatible, or cannot be initialized.
 * <p>
 * This exception indicates structural problems with the database, such as missing required tables,
 * columns, or indices, or when the schema version is incompatible with the expected version.
 * </p>
 */
public class SchemaException extends PersistenceException {
    /**
     * Constructs a SchemaException with the given message.
     *
     * @param message the detail message
     */
    public SchemaException(String message) {
        super(message);
    }

    /**
     * Constructs a SchemaException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
