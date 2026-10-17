package io.github.floatingpointmc.sanctionmanager.minecraft.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisConfig {
    private @NotNull String host;
    private int port;
    private @NotNull String password;

    public static @NotNull RedisConfig defaults() {
        return RedisConfig.builder()
                .host("localhost")
                .port(6379)
                .password("")
                .build();
    }
}