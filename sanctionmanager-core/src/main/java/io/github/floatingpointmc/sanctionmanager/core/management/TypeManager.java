package io.github.floatingpointmc.sanctionmanager.core.management;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages registered sanction entry types.
 * <p>
 * This class stores and provides access to entry types registered with the Core.
 * It maintains a map of identifier-to-type mappings for lookup.
 * </p>
 * <p>
 * Two constructors exist: {@link #TypeManager(List)}, which builds an unmodifiable map from the list,
 * and a Lombok-generated {@code TypeManager(Map)} constructor, which stores the given map directly
 * without copying or wrapping it.
 * </p>
 * <p>
 * <strong>Thread Safety:</strong> When constructed from a list, the internal map is never modified after
 * construction. When constructed from a map, the caller must not modify that map afterwards.
 * </p>
 */
@RequiredArgsConstructor
public class TypeManager {
    /**
     * Map of type identifier to EntryType. Unmodifiable when built via {@link #TypeManager(List)}.
     */
    private final Map<String, EntryType> types;

    /**
     * Constructs a TypeManager with the given entry types.
     * <p>
     * Duplicates are not rejected here; if several types share an identifier, the last one wins.
     * </p>
     *
     * @param types the list of entry types to manage. Cannot be null.
     * @throws NullPointerException if {@code types} or any element is null
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
     * <p>
     * Note: despite the {@code @NotNull} annotation on the return type, this method returns {@code null}
     * for unregistered identifiers. Callers should check {@link #contains(String)} first.
     * </p>
     *
     * @param type the type identifier
     * @return the registered entry type, or {@code null} if not found
     */
    public @NotNull EntryType getType(@NotNull String type) {
        return types.get(type);
    }

    public @NotNull Collection<EntryType> getTypes() {
        return Collections.unmodifiableCollection(types.values());
    }
}
