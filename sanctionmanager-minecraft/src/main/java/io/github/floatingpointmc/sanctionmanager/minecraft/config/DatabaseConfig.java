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
public class DatabaseConfig {
    private @NotNull String driver;
    private @NotNull String host;
    private int port;
    private @NotNull String database;
    private @NotNull String user;
    private @NotNull String password;

    public @NotNull String getJdbcUrl() {
        if (driver.contains("mysql")) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
        }
        if (driver.contains("mariadb")) {
            return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
        }
        if (driver.contains("postgresql")) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
        if (driver.contains("sqlite")) {
            return "jdbc:sqlite:" + database;
        }
        return "jdbc://" + host + ":" + port + "/" + database;
    }

    public static @NotNull DatabaseConfig defaults() {
        return DatabaseConfig.builder()
                .driver("com.mysql.cj.jdbc.Driver")
                .host("localhost")
                .port(3306)
                .database("sanctionmanager")
                .user("root")
                .password("")
                .build();
    }
}