package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * In-memory representation of the superblock (page 0).
 * Contains file-level metadata. The format also reserves root page IDs for persistent indexes, a free-page
 * list head and WAL positions, but these are not used: no persistent indexes, free-page list or WAL exist,
 * and these fields keep their initial values.
 * <p>
 * All fields are public and mutable.
 * </p>
 */
public class Superblock {
    private static final String MAGIC = "SMBFILE1";
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_SIZE = 96;

    // Superblock fields
    /** File generation counter. Currently never changed from 0. */
    public long fileGeneration;
    /** Number of pages in use, including the superblock page. Incremented when a data page is allocated. */
    public long filePageCount;
    /** ID to assign to the next inserted record. */
    public long nextEntryId;
    /** Reserved root page ID for a persistent primary index; unused (-1). */
    public long recordRootPageId;
    /** Reserved root page ID for a persistent type/relId index; unused (-1). */
    public long typeRelRootPageId;
    /** Reserved root page ID for a persistent target index; unused (-1). */
    public long targetRootPageId;
    /** Reserved head of a free-page list; unused (-1). */
    public long freePageHead;
    /** Reserved WAL checkpoint position; unused (0). */
    public long walCheckpointLsn;
    /** Reserved committed WAL position; unused (0). */
    public long committedLsn;
    /** Reserved flags; unused (0). */
    public int flags;

    /**
     * Creates a superblock for a new, empty file.
     *
     * @return a superblock with one page, next entry ID 1, and reserved fields at their initial values
     */
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

    /**
     * Decodes a superblock from page bytes.
     *
     * @param data     the page bytes
     * @param pageSize the expected page size
     * @return the decoded superblock
     * @throws StorageException if the magic, format version, page size, page count or CRC is invalid
     */
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

    /**
     * Encodes this superblock into the given buffer and zero-fills the rest of it.
     *
     * @param data     the destination buffer, normally one page
     * @param pageSize the page size to record
     * @throws StorageException if {@code data} is smaller than the superblock header
     */
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
