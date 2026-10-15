package io.github.floatingpointmc.sanctionmanager.minecraft.config;

import lombok.*;
import org.jetbrains.annotations.NotNull;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TranslationContext {
    private @NotNull String pluginName;
    private @NotNull String pluginVersion;

    /** Message-only context; all domain fields come from the committed Entry snapshot. */
    public EntryMessage forEntry(io.github.floatingpointmc.sanctionmanager.api.model.EntrySnapshot entry,
                                 String targetName, String operatorName) {
        return new EntryMessage(entry, targetName, operatorName, pluginName, pluginVersion);
    }

    @Getter
    public static final class EntryMessage {
        private final io.github.floatingpointmc.sanctionmanager.api.model.EntrySnapshot entry;
        private final String targetName;
        private final String operatorName;
        private final String pluginName;
        private final String pluginVersion;

        private EntryMessage(io.github.floatingpointmc.sanctionmanager.api.model.EntrySnapshot entry,
                             String targetName, String operatorName, String pluginName, String pluginVersion) {
            this.entry = java.util.Objects.requireNonNull(entry, "entry");
            this.targetName = targetName;
            this.operatorName = operatorName;
            this.pluginName = java.util.Objects.requireNonNull(pluginName, "pluginName");
            this.pluginVersion = java.util.Objects.requireNonNull(pluginVersion, "pluginVersion");
        }
    }

    public static MessageContextBuilder builder() {
        return new MessageContextBuilder();
    }

    public static class MessageContextBuilder {
        private @NotNull String pluginName = "SanctionManager";
        private @NotNull String pluginVersion = "unknown";

        public MessageContextBuilder pluginName(@NotNull String pluginName) {
            this.pluginName = pluginName;
            return this;
        }

        public MessageContextBuilder pluginVersion(@NotNull String pluginVersion) {
            this.pluginVersion = pluginVersion;
            return this;
        }

        public TranslationContext build() {
            return new TranslationContext(pluginName, pluginVersion);
        }
    }
}