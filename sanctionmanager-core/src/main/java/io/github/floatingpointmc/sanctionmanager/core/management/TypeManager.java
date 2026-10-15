package io.github.floatingpointmc.sanctionmanager.core.management;

import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Manages registered sanction entry types.
 * <p>
 * This class keeps track of which entry types are registered with the system.
 * </p>
 */
@RequiredArgsConstructor
public class TypeManager {
    private final Map<String, Boolean> types;

    public boolean contains(String type) {
        return types.containsKey(type);
    }

    public boolean requireExtraData(String type) {
        return types.get(type);
    }
}
