package io.github.floatingpointmc.sanctionmanager.api.model;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;

import java.util.regex.Pattern;

@ApiStatus.Internal
final class EntryTypeImpl implements EntryType {
    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    @Getter
    private final String identifier;
    private final boolean withExtraData;

    EntryTypeImpl(String identifier, boolean withExtraData) {
        validate(identifier);
        this.identifier = identifier;
        this.withExtraData = withExtraData;
    }

    @Override
    public boolean withExtraData() {
        return withExtraData;
    }

    private static void validate(String identifier) {
        if (!TYPE_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid entry type: " + identifier);
        }
    }
}
