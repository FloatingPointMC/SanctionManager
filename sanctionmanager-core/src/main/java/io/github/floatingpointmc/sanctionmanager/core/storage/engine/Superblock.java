package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * In-memory representation of the superblock (page 0).
 * Contains file-level metadata and root page IDs for B+ tree indexes.
 */
public class Superblock {
    private static final String MAGIC = "SMBFILE1";
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_SIZE = 96;

    // Superblock fields
    public long fileGeneration;
    public long filePageCount;
    public long nextEntryId;
    public long recordRootPageId;
    public long typeRelRootPageId;
    public long targetRootPageId;
    public long freePageHead;
    public long walCheckpointLsn;
    public long committedLsn;
    public int flags;

    public static Superblock createEmpty() {
        Superblock sb = new Superblock();
        sb.fileGeneration = 0;
        sb.filePageCount = 1;
        sb.nextEntryId = 1;
        sb.recordRootPageId = -1;
        sb.typeRelRootPageId = -1;
        sb.targetRootPageId = -1;
        sb.freePageHead = -1;
        sb.walCheckpointLsn = 0;
        sb.committedLsn = 0;
        sb.flags = 0;
        return sb;
    }

    public static Superblock fromBytes(byte[] data, int pageSize) throws StorageException {
        PageBuffer buf = new PageBuffer(data);

        // Read magic
        byte[] magicBytes = new byte[8];
        buf.get(magicBytes);
        String magic = new String(magicBytes);
        if (!magic.equals(MAGIC)) {
            throw new StorageException("Invalid magic: " + magic);
        }

        // Read format version
        int version = buf.getInt();
        if (version != FORMAT_VERSION) {
            throw new StorageException("Unsupported format version: " + version);
        }

        // Read page size
        int pageSizeRead = buf.getInt();
        if (pageSizeRead != pageSize) {
            throw new StorageException("Page size mismatch: expected " + pageSize + ", got " + pageSizeRead);
        }

        Superblock sb = new Superblock();
        sb.fileGeneration = buf.getLong();
        sb.filePageCount = buf.getLong();
        
        if (sb.filePageCount <= 0) {
            throw new StorageException("Invalid file page count: " + sb.filePageCount);
        }

        sb.nextEntryId = buf.getLong();
        sb.recordRootPageId = buf.getLong();
        sb.typeRelRootPageId = buf.getLong();
        sb.targetRootPageId = buf.getLong();
        sb.freePageHead = buf.getLong();
        sb.walCheckpointLsn = buf.getLong();
        sb.committedLsn = buf.getLong();
        sb.flags = buf.getInt();

        // Verify CRC
        int storedCrc = buf.getInt();
        int computedCrc = Crc32c.compute(data, 0, 92);
        if (storedCrc != computedCrc) {
            throw new StorageException("Superblock CRC mismatch");
        }

        return sb;
    }

    public void toBytes(byte[] data, int pageSize) throws StorageException {
        if (data.length < HEADER_SIZE) {
            throw new StorageException("Superblock buffer too small");
        }

        PageBuffer buf = new PageBuffer(data);

        // Write magic
        buf.put(MAGIC.getBytes());

        // Write format version
        buf.putInt(FORMAT_VERSION);

        // Write page size
        buf.putInt(pageSize);

        // Write fields
        buf.putLong(fileGeneration);
        buf.putLong(filePageCount);
        buf.putLong(nextEntryId);
        buf.putLong(recordRootPageId);
        buf.putLong(typeRelRootPageId);
        buf.putLong(targetRootPageId);
        buf.putLong(freePageHead);
        buf.putLong(walCheckpointLsn);
        buf.putLong(committedLsn);
        buf.putInt(flags);

        // Compute and write CRC
        int crc = Crc32c.compute(data, 0, 92);
        buf.putInt(crc);

        // Zero-fill reserved area
        while (buf.position() < data.length) {
            buf.putByte((byte) 0);
        }
    }
}
