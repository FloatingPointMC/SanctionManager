package io.github.floatingpointmc.sanctionmanager.core.management;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages registered sanction entry types.
 * <p>
 * This class stores and provides access to entry types registered with the Core.
 * It maintains an immutable map of identifier-to-type mappings for efficient lookup.
 * </p>
 * <p>
 * <strong>Thread Safety:</strong> This class is immutable after construction and thread-safe.
 * </p>
 */
@RequiredArgsConstructor
public class TypeManager {
    /** Immutable map of type identifier to EntryType. */
    private final Map<String, EntryType> types;

    /**
     * Constructs a TypeManager with the given entry types.
     *
     * @param types the list of entry types to manage. Cannot be null.
     */
    public TypeManager(@NotNull List<EntryType> types) {
        HashMap<String, EntryType> typeMap = new HashMap<>();
        types.forEach(type -> typeMap.put(type.getIdentifier(), type));
        this.types = Collections.unmodifiableMap(typeMap);
    }

    /**
     * Checks whether an entry type with the given identifier is registered.
     *
     * @param type the type identifier to check
     * @return {@code true} if the type is registered, {@code false} otherwise
     */
    public boolean contains(@NotNull String type) {
        return types.containsKey(type);
    }

    /**
     * Gets the entry type for the given identifier.
     *
     * @param type the type identifier
     * @return the registered entry type, or {@code null} if not found
     */
    public @NotNull EntryType getType(@NotNull String type) {
        return types.get(type);
    }
}
