package io.github.floatingpointmc.sanctionmanager.api.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Defines the type of sanction entry.
 * <p>
 * Entry types categorize sanctions (e.g., "ban", "mute"). Each type has a unique identifier
 * and optionally supports extended data in JSON format.
 * </p>
 * <p>
 * Types are created via static factory methods and registered with the Core before initialization.
 * The identifier must match the pattern {@code [a-z][a-z0-9_]*}.
 * </p>
 */
public final class EntryType {
    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    /**
     * Unique identifier for this entry type.
     * <p>
     * The identifier is case-sensitive and must match the pattern {@code [a-z][a-z0-9_]*}.
     * </p>
     */
    @Getter
    private final @NotNull String identifier;
    private final boolean withExtraData;

    /**
     * Constructs an EntryType with validation.
     *
     * @param identifier    the type identifier. Must match {@code [a-z][a-z0-9_]*}.
     * @param withExtraData whether extended data support is enabled
     * @throws IllegalArgumentException if the identifier is invalid
     */
    private EntryType(@NotNull String identifier, boolean withExtraData) {
        validate(identifier);
        this.identifier = identifier;
        this.withExtraData = withExtraData;
    }

    /**
     * Indicates whether this entry type supports extended data.
     * <p>
     * If {@code true}, entries of this type may have extended data stored in a JSON object.
     * If {@code false}, extended data is not supported and will be empty.
     * </p>
     *
     * @return {@code true} if extended data is supported, {@code false} otherwise
     */
    public boolean withExtraData() {
        return withExtraData;
    }

    /**
     * Compares two entry types by their identifiers.
     * <p>
     * Two entry types are equal if they have the same identifier, regardless of their
     * extended data support setting.
     * </p>
     *
     * @param obj the object to compare
     * @return {@code true} if both are EntryType instances with the same identifier
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof EntryType)) return false;
        return identifier.equals(((EntryType) obj).getIdentifier());
    }

    /**
     * Returns a hash code based on the identifier.
     *
     * @return hash code for this entry type
     */
    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    /**
     * Creates an entry type without extended data support.
     *
     * @param identifier the type identifier. Must be non-empty and match {@code [a-z][a-z0-9_]*}.
     * @return a new EntryType instance with extended data disabled
     * @throws IllegalArgumentException if the identifier is invalid
     * @throws NullPointerException if {@code identifier} is null
     */
    public static EntryType of(@NotNull String identifier) {
        return new EntryType(identifier, false);
    }

    /**
     * Creates an entry type with optional extended data support.
     *
     * @param identifier    the type identifier. Must be non-empty and match {@code [a-z][a-z0-9_]*}.
     * @param withExtraData {@code true} to enable extended data support, {@code false} to disable it
     * @return a new EntryType instance
     * @throws IllegalArgumentException if the identifier is invalid
     * @throws NullPointerException if {@code identifier} is null
     */
    public static EntryType of(@NotNull String identifier, boolean withExtraData) {
        return new EntryType(identifier, withExtraData);
    }

    /**
     * Validates that the identifier matches the required pattern.
     *
     * @param identifier the identifier to validate
     * @throws IllegalArgumentException if the identifier is invalid
     */
    private static void validate(@NotNull String identifier) {
        if (!TYPE_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid entry type: " + identifier);
        }
    }
}
