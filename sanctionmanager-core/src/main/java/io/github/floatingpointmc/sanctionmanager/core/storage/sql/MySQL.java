package io.github.floatingpointmc.sanctionmanager.core.storage.sql;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

public class MySQL implements IDatabase {
    private static final String CREATE_GLOBAL = "CREATE TABLE sm_parent (" +
            "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
            "target BINARY(16) NOT NULL," +
            "type VARCHAR(32) NOT NULL," +
            "PRIMARY KEY (id)," +
            "KEY idx_target (target)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    private static byte[] uuidToBytes(@NotNull UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static @NotNull UUID bytesToUuid(byte @NotNull [] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID must be exactly 16 bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Override
    public void initialize() {

    }
}
