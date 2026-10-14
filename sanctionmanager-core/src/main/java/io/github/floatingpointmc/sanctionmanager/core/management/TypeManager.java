package io.github.floatingpointmc.sanctionmanager.core.management;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages registered sanction entry types.
 * <p>
 * This class keeps track of which entry types are registered with the system.
 * </p>
 */
public class TypeManager {
    private final Map<String, Boolean> types;

    public TypeManager() {
        this.types = new HashMap<>();
    }

    public TypeManager(Map<String, Boolean> types) {
        this.types = types;
    }

    public boolean contains(String type) {
        return types.containsKey(type);
    }

    public void register(String type) {
        types.put(type, true);
    }
}
