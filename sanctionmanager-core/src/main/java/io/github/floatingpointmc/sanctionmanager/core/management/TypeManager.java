package io.github.floatingpointmc.sanctionmanager.core.management;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages registered sanction entry types.
 * <p>
 * This class keeps track of which entry types are registered with the system.
 * </p>
 */
@RequiredArgsConstructor
public class TypeManager {
    private final Map<String, EntryType> types;

    public TypeManager(@NotNull List<EntryType> types) {
        HashMap<String, EntryType> typeMap = new HashMap<>();
        types.forEach(type -> typeMap.put(type.getIdentifier(), type));
        this.types = Collections.unmodifiableMap(typeMap);
    }

    public boolean contains(@NotNull String type) {
        return types.containsKey(type);
    }

    public @NotNull EntryType getType(@NotNull String type) {
        return types.get(type);
    }
}
