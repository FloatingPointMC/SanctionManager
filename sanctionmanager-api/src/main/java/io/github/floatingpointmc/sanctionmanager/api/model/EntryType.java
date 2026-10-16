package io.github.floatingpointmc.sanctionmanager.api.model;

/**
 * Defines the type of a sanction entry.
 * <p>
 * Entry types categorize sanctions (e.g., "BAN", "MUTE"). Each type has a unique identifier
 * and optionally supports extended data in JSON format.
 * </p>
 * <p>
 * Types are created via static factory methods and registered with the Core before initialization.
 * The identifier must match the pattern {@code [a-z][a-z0-9_]*}.
 * </p>
 */
public interface EntryType {
    /**
     * Gets the unique identifier for this entry type.
     * <p>
     * The identifier is case-sensitive and must match the pattern {@code [a-z][a-z0-9_]*}.
     * </p>
     *
     * @return the identifier, never null
     */
    String getIdentifier();

    /**
     * Indicates whether this entry type supports extended data.
     * <p>
     * If {@code true}, entries of this type may have extended data stored in a JSON object.
     * If {@code false}, extended data is not supported and will be empty.
     * </p>
     *
     * @return {@code true} if extended data is supported, {@code false} otherwise
     */
    boolean withExtraData();

    /**
     * Creates an entry type without extended data support.
     *
     * @param identifier the type identifier. Must be non-empty and match {@code [a-z][a-z0-9_]*}.
     * @return a new EntryType instance with extended data disabled
     * @throws IllegalArgumentException if the identifier is invalid
     */
    static EntryType of(String identifier) {
        return new EntryTypeImpl(identifier, false);
    }

    /**
     * Creates an entry type with optional extended data support.
     *
     * @param identifier    the type identifier. Must be non-empty and match {@code [a-z][a-z0-9_]*}.
     * @param withExtraData {@code true} to enable extended data support, {@code false} to disable it
     * @return a new EntryType instance
     * @throws IllegalArgumentException if the identifier is invalid
     */
    static EntryType of(String identifier, boolean withExtraData) {
        return new EntryTypeImpl(identifier, withExtraData);
    }
}
