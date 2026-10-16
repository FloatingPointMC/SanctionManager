package io.github.floatingpointmc.sanctionmanager.api.model;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;

import java.util.regex.Pattern;

/**
 * Internal implementation of {@code EntryType}.
 * <p>
 * This class validates that entry type identifiers follow the pattern {@code [a-z][a-z0-9_]*}
 * and implements equality based on the identifier.
 * </p>
 * <p>
 * This is an internal class; use {@link EntryType#of(String)} to create instances.
 * </p>
 */
@ApiStatus.Internal
final class EntryTypeImpl implements EntryType {
    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    @Getter
    private final String identifier;
    private final boolean withExtraData;

    /**
     * Constructs an EntryTypeImpl with validation.
     *
     * @param identifier    the type identifier. Must match {@code [a-z][a-z0-9_]*}.
     * @param withExtraData whether extended data support is enabled
     * @throws IllegalArgumentException if the identifier is invalid
     */
    EntryTypeImpl(String identifier, boolean withExtraData) {
        validate(identifier);
        this.identifier = identifier;
        this.withExtraData = withExtraData;
    }

    @Override
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
     * Validates that the identifier matches the required pattern.
     *
     * @param identifier the identifier to validate
     * @throws IllegalArgumentException if the identifier is invalid
     */
    private static void validate(String identifier) {
        if (!TYPE_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid entry type: " + identifier);
        }
    }
}
