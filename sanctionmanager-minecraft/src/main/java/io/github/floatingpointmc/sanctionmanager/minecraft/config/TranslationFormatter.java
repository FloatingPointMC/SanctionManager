package io.github.floatingpointmc.sanctionmanager.minecraft.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TranslationFormatter {
    public static boolean isTemporary(@NotNull TranslationContext.EntryMessage context) {
        return context.getEntry().getExpiryTime() != null;
    }

    public static @NotNull String replaceVariables(@NotNull String line,
                                                   @NotNull TranslationContext.EntryMessage context) {
        String result = line;
        result = result.replace("%id%", String.valueOf(context.getEntry().getId()));
        result = result.replace("%reason%", context.getEntry().getReason() != null ? context.getEntry().getReason() : "");
        result = result.replace("%name%", context.getTargetName() != null ? context.getTargetName() : context.getEntry().getTarget().toString());
        result = result.replace("%uuid%", context.getEntry().getTarget().toString());
        result = result.replace("%operator%", context.getOperatorName() != null ? context.getOperatorName() : "[Console]");
        result = result.replace("%plugin%", context.getPluginName());
        result = result.replace("%version%", context.getPluginVersion());
        if (context.getEntry().getExpiryTime() != null) {
            result = result.replace("%time%", java.time.LocalDateTime.ofInstant(context.getEntry().getExpiryTime().toInstant(), java.time.ZoneId.systemDefault()).toString());
            Duration duration = Duration.between(java.time.Instant.now(), context.getEntry().getExpiryTime().toInstant());
            result = result.replace("%duration%", formatDuration(duration));
        } else {
            result = result.replace("%time%", "permanent");
            result = result.replace("%duration%", "permanent");
        }
        return result;
    }

    public static @NotNull String replaceVariables(@NotNull String line,
                                                   @NotNull TranslationContext context) {
        String result = line;
        result = result.replace("%plugin%", context.getPluginName());
        result = result.replace("%version%", context.getPluginVersion());
        return result;
    }

    public static @NotNull String format(@NotNull List<String> lines,
                                         @NotNull TranslationContext.EntryMessage context) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(replaceVariables(line, context));
        }
        return sb.toString();
    }

    public static @NotNull List<String> formatLines(@NotNull List<String> lines,
                                                    @NotNull TranslationContext.EntryMessage context) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replaceVariables(line, context));
        }
        return result;
    }

    public static @NotNull List<String> formatLines(@NotNull List<String> lines,
                                                    @NotNull TranslationContext context) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replaceVariables(line, context));
        }
        return result;
    }

    public static @NotNull String formatDuration(@Nullable Duration duration) {
        if (duration == null) {
            return "permanent";
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }
}