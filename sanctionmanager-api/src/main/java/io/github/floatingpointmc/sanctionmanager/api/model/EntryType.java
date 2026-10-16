package io.github.floatingpointmc.sanctionmanager.api.model;

public interface EntryType {
    String getIdentifier();

    boolean withExtraData();

    static EntryType of(String identifier) {
        return new EntryTypeImpl(identifier, false);
    }

    static EntryType of(String identifier, boolean withExtraData) {
        return new EntryTypeImpl(identifier, withExtraData);
    }
}
