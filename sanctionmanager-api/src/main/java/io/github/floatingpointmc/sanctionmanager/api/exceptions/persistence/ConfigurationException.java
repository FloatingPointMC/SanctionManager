package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when the persistence configuration is missing, invalid, or incomplete.
 * <p>
 * This exception indicates problems with Core configuration, such as missing database connection
 * parameters, invalid credentials, or unsupported database types.
 * </p>
 */
public class ConfigurationException extends PersistenceException {
    /**
     * Constructs a ConfigurationException with the given message.
     *
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a ConfigurationException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
