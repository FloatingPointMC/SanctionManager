package io.github.floatingpointmc.sanctionmanager.minecraft.operation;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationSerializationTest {

    @Test
    void testBanOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Duration duration = Duration.ofDays(1);
        BanOperation original = new BanOperation(target, executor, duration, "Cheating");

        BanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getDuration(), deserialized.getDuration());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testBanOperationSerializationWithNullDuration() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        BanOperation original = new BanOperation(target, executor, null, "Permanent ban");

        BanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertNull(deserialized.getDuration());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testUnbanOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UnbanOperation original = new UnbanOperation(target, executor);

        UnbanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
    }

    @Test
    void testMuteOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Duration duration = Duration.ofHours(1);
        MuteOperation original = new MuteOperation(target, executor, duration, "Spam");

        MuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getDuration(), deserialized.getDuration());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testMuteOperationSerializationWithNullDuration() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        MuteOperation original = new MuteOperation(target, executor, null, "Permanent mute");

        MuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertNull(deserialized.getDuration());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testUnmuteOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UnmuteOperation original = new UnmuteOperation(target, executor);

        UnmuteOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
    }

    @Test
    void testWarnOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        WarnOperation original = new WarnOperation(target, executor, "Warning message");

        WarnOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(original.getReason(), deserialized.getReason());
    }

    @Test
    void testUnwarnOperationSerialization() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UnwarnOperation original = new UnwarnOperation(target, executor);

        UnwarnOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
    }

    @Test
    void testBanOperationSerializationWithZeroDuration() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Duration duration = Duration.ZERO;
        BanOperation original = new BanOperation(target, executor, duration, "Zero duration test");

        BanOperation deserialized = serializeAndDeserialize(original);

        assertEquals(original.getTargetUuid(), deserialized.getTargetUuid());
        assertEquals(original.getExecutorUuid(), deserialized.getExecutorUuid());
        assertEquals(Duration.ZERO, deserialized.getDuration());
        assertEquals(original.getReason(), deserialized.getReason());
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
