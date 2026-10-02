package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationSerializationTest {

    @Test
    void testBanOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        BanOperation original = new BanOperation(target, "Player1", executor, "Admin1", expiry, "Cheating");

        BanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
        assertEquals(original.getExpiryTime(), deserialized.getExpiryTime());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testBanOperationSerializationWithNullExpiry() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        BanOperation original = new BanOperation(target, "Player1", executor, "Admin1", null, "Permanent ban");

        BanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
        assertNull(deserialized.getExpiryTime());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testUnbanOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UnbanOperation original = new UnbanOperation(target, "Player1", executor, "Admin1");

        UnbanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
    }

    @Test
    void testMuteOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);
        MuteOperation original = new MuteOperation(target, "Player1", executor, "Admin1", expiry, "Spam");

        MuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
        assertEquals(original.getExpiryTime(), deserialized.getExpiryTime());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testMuteOperationSerializationWithNullExpiry() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        MuteOperation original = new MuteOperation(target, "Player1", executor, "Admin1", null, "Permanent mute");

        MuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
        assertNull(deserialized.getExpiryTime());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testUnmuteOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UnmuteOperation original = new UnmuteOperation(target, "Player1", executor, "Admin1");

        UnmuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getTargetName(), deserialized.getTargetName());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getExecutorName(), deserialized.getExecutorName());
    }

    private <T extends Serializable> T serializeAndDeserialize(T object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        @SuppressWarnings("unchecked")
        T result = (T) ois.readObject();
        ois.close();

        return result;
    }
}
