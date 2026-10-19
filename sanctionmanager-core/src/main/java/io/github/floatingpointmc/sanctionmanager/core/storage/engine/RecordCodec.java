package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encodes and decodes Entry records to/from binary format.
 * <p>
 * Entry types are written as numeric IDs. The mapping between type identifiers and numeric IDs is provided
 * by the caller (typically restored from persistent storage) and maintained during encoding. New types
 * encountered during encoding are assigned sequential IDs.
 * </p>
 * <p>
 * The {@code overridden} and {@code version} fields of an entry are not encoded.
 * </p>
 */
public class RecordCodec {
    private static final short RECORD_VERSION = 1;
    private static final short RECORD_FLAGS = 0;
    private final TypeManager typeManager;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    // Type ID registry: identifier -> numeric ID
    private final Map<String, Integer> typeToId;
    private final Map<Integer, String> idToType;
    private int nextTypeId;

    /**
     * Creates a codec with an empty type mapping.
     *
     * @param typeManager type manager used to resolve type identifiers when decoding
     */
    public RecordCodec(TypeManager typeManager) {
        this(typeManager, new HashMap<>());
    }

    /**
     * Creates a codec with a restored type mapping.
     *
     * @param typeManager    type manager used to resolve type identifiers when decoding
     * @param restoredTypeIdMap existing type ID mapping to restore (will be copied)
     */
    public RecordCodec(TypeManager typeManager, Map<String, Integer> restoredTypeIdMap) {
        this.typeManager = typeManager;
        this.typeToId = new HashMap<>(restoredTypeIdMap);
        this.idToType = new HashMap<>();
        int maxId = 0;
        for (Map.Entry<String, Integer> entry : restoredTypeIdMap.entrySet()) {
            idToType.put(entry.getValue(), entry.getKey());
            if (entry.getValue() > maxId) {
                maxId = entry.getValue();
            }
        }
        this.nextTypeId = maxId + 1;
    }

    /**
     * Gets a copy of the current type ID mapping for persistence.
     *
     * @return a copy of the type identifier to numeric ID mapping
     */
    public synchronized Map<String, Integer> getTypeIdMap() {
        return new HashMap<>(typeToId);
    }

    /**
     * Encodes an entry into a record, including a trailing checksum.
     * <p>
     * If the entry's type has not been encoded before by this codec, a new type ID is assigned. A null data
     * object is written as an empty JSON object.
     * </p>
     *
     * @param entry the entry to encode
     * @return the record bytes
     * @throws StorageException declared; not thrown by the current implementation
     */
    public byte[] encode(EntryPersisted entry) throws StorageException {
        // Calculate size
        int size = calculateSize(entry);

        byte[] recordBytes = new byte[size];
        PageBuffer buf = new PageBuffer(recordBytes);

        // Header (4 + 2 + 2 = 8 bytes)
        buf.putInt(size);
        buf.putShort(RECORD_VERSION);
        buf.putShort(RECORD_FLAGS);

        // Fixed fields
        buf.putLong(entry.getId());
        buf.putLong(entry.getRelId());

        // Type ID (mapped from entry type)
        int typeId = mapTypeToId(entry.getType().getIdentifier());
        buf.putInt(typeId);

        // UUIDs
        writeUuid(buf, entry.getTarget());
        writeUuid(buf, entry.getExecutor());

        // Timestamps
        buf.putLong(entry.getExecutingTime().getTime());

        // Reason (nullable string)
        writeNullableString(buf, entry.getReason());

        // Expiry time (nullable)
        if (entry.getExpiryTime() != null) {
            buf.putByte((byte) 1);
            buf.putLong(entry.getExpiryTime().getTime());
        } else {
            buf.putByte((byte) 0);
        }

        // Withdrawn by (nullable UUID)
        if (entry.getWithdrawnBy() != null) {
            buf.putByte((byte) 1);
            writeUuid(buf, entry.getWithdrawnBy());
        } else {
            buf.putByte((byte) 0);
        }

        // Withdrawal reason (nullable string)
        writeNullableString(buf, entry.getWithdrawalReason());

        // Overridden by (nullable long)
        if (entry.getOverriddenBy() != null) {
            buf.putByte((byte) 1);
            buf.putLong(entry.getOverriddenBy());
        } else {
            buf.putByte((byte) 0);
        }

        // Data (JSON)
        byte[] jsonBytes = gson.toJson(entry.getDataInternal() != null ? entry.getDataInternal() : new JsonObject()).getBytes(StandardCharsets.UTF_8);
        buf.putInt(jsonBytes.length);
        buf.put(jsonBytes);

        // CRC (computed over all preceding bytes)
        int crc = Crc32c.compute(recordBytes, 0, size - 4);
        buf.putInt(crc);

        return recordBytes;
    }

    /**
     * Decodes a record.
     * <p>
     * The returned entry always has {@code overridden} set to {@code false} and {@code version} set to 0, since
     * these are not stored. The data is always decoded as a JSON object, even for types without extra data.
     * </p>
     *
     * @param recordBytes the record bytes
     * @return the decoded entry
     * @throws StorageException if the length, version, flags, type ID, type, string or data length, or checksum
     *                          is invalid
     */
    public EntryPersisted decode(byte[] recordBytes) throws StorageException {
        PageBuffer buf = new PageBuffer(recordBytes);

        // Verify length
        int recordLength = buf.getInt();
        if (recordLength != recordBytes.length) {
            throw new StorageException("Record length mismatch: " + recordLength + " vs " + recordBytes.length);
        }

        short version = buf.getShort();
        if (version != RECORD_VERSION) {
            throw new StorageException("Unsupported record version: " + version);
        }

        short flags = buf.getShort();
        if (flags != RECORD_FLAGS) {
            throw new StorageException("Unsupported record flags: " + flags);
        }

        // Fixed fields
        long id = buf.getLong();
        long relId = buf.getLong();

        // Type ID
        int typeId = buf.getInt();
        String typeIdentifier = mapIdToTypeIdentifier(typeId);
        EntryType type = typeManager.getType(typeIdentifier);
        if (type == null) {
            throw new StorageException("Unknown entry type: " + typeIdentifier);
        }

        // UUIDs
        UUID target = readUuid(buf);
        UUID executor = readUuid(buf);

        // Timestamps
        long executingTimeMillis = buf.getLong();
        Date executingTime = new Date(executingTimeMillis);

        // Reason
        String reason = readNullableString(buf);

        // Expiry time
        Date expiryTime = null;
        if (buf.getByte() != 0) {
            expiryTime = new Date(buf.getLong());
        }

        // Withdrawn by
        UUID withdrawnBy = null;
        if (buf.getByte() != 0) {
            withdrawnBy = readUuid(buf);
        }

        // Withdrawal reason
        String withdrawalReason = readNullableString(buf);

        // Overridden by
        Long overriddenBy = null;
        if (buf.getByte() != 0) {
            overriddenBy = buf.getLong();
        }

        // Data
        int dataLength = buf.getInt();
        if (dataLength < 0 || buf.remaining() < dataLength + 4) {
            throw new StorageException("Invalid data length: " + dataLength);
        }
        byte[] jsonBytes = new byte[dataLength];
        buf.get(jsonBytes);
        JsonObject data = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);

        // Verify CRC
        int storedCrc = buf.getInt();
        int computedCrc = Crc32c.compute(recordBytes, 0, recordLength - 4);
        if (storedCrc != computedCrc) {
            throw new StorageException("Record CRC mismatch");
        }

        return new EntryPersisted(id, relId, type, target, executor, expiryTime, executingTime, reason, data, withdrawnBy, withdrawalReason, false, overriddenBy, 0);
    }

    private int calculateSize(EntryPersisted entry) {
        int size = 0;
        size += 4; // recordLength
        size += 2; // recordVersion
        size += 2; // recordFlags
        size += 8; // id
        size += 8; // relId
        size += 4; // typeId
        size += 16; // target UUID
        size += 16; // executor UUID
        size += 8; // executingTimeMillis
        size += 1 + stringLength(entry.getReason()); // reason presence + data
        size += 1 + (entry.getExpiryTime() != null ? 8 : 0); // expiry presence + data
        size += 1 + (entry.getWithdrawnBy() != null ? 16 : 0); // withdrawnBy presence + data
        size += 1 + stringLength(entry.getWithdrawalReason()); // withdrawalReason presence + data
        size += 1 + (entry.getOverriddenBy() != null ? 8 : 0); // overriddenBy presence + data

        String jsonStr = gson.toJson(entry.getDataInternal() != null ? entry.getDataInternal() : new JsonObject());
        byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        size += 4 + jsonBytes.length; // dataLength + data
        size += 4; // CRC

        return size;
    }

    private int stringLength(String str) {
        if (str == null) return 0;
        return 4 + str.getBytes(StandardCharsets.UTF_8).length;
    }

    private void writeNullableString(PageBuffer buf, String str) {
        if (str == null) {
            buf.putByte((byte) 0);
        } else {
            buf.putByte((byte) 1);
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buf.putInt(bytes.length);
            buf.put(bytes);
        }
    }

    private String readNullableString(PageBuffer buf) throws StorageException {
        byte present = buf.getByte();
        if (present == 0) {
            return null;
        }
        int length = buf.getInt();
        if (length < 0 || buf.remaining() < length) {
            throw new StorageException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeUuid(PageBuffer buf, UUID uuid) {
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
    }

    private UUID readUuid(PageBuffer buf) {
        long msb = buf.getLong();
        long lsb = buf.getLong();
        return new UUID(msb, lsb);
    }

    private synchronized int mapTypeToId(String typeIdentifier) {
        // Return existing ID if already mapped
        if (typeToId.containsKey(typeIdentifier)) {
            return typeToId.get(typeIdentifier);
        }

        // Assign new ID
        int id = nextTypeId++;
        typeToId.put(typeIdentifier, id);
        idToType.put(id, typeIdentifier);
        return id;
    }

    private String mapIdToTypeIdentifier(int typeId) throws StorageException {
        if (idToType.containsKey(typeId)) {
            return idToType.get(typeId);
        }
        throw new StorageException("Unknown type ID: " + typeId);
    }
}

