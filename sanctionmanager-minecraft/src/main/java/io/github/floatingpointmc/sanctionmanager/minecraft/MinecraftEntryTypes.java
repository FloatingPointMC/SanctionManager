package io.github.floatingpointmc.sanctionmanager.minecraft;

import io.github.floatingpointmc.sanctionmanager.api.model.Identifier;

/** Platform-owned type identifiers registered before Core initialization. */
public final class MinecraftEntryTypes {
    public static final Identifier BAN = new Identifier("minecraft:ban");
    public static final Identifier MUTE = new Identifier("minecraft:mute");

    private MinecraftEntryTypes() {}
}

