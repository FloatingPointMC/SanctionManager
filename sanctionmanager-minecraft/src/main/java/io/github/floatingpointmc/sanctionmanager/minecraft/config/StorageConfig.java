package io.github.floatingpointmc.sanctionmanager.minecraft.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConfig {
    private boolean databaseEnabled;
    private boolean redisEnabled;
    private @Nullable DatabaseConfig databaseConfig;
    private @Nullable RedisConfig redisConfig;
    private @Nullable String binaryDataDir;
}