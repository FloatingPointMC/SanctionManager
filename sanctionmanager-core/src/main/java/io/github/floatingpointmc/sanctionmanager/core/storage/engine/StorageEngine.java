package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main storage engine implementing page-based binary file persistence.
 * Manages pages and records. Lookup indexes (by ID, by type and relId, and by target) are kept only in
 * memory; they are not persisted and are rebuilt by scanning all data pages when an existing file is opened.
 * <p>
 * Records are decoded with a {@link RecordCodec} whose type-ID mapping is held in memory only, so records
 * written by a previous engine instance may fail to decode after a restart.
 * </p>
 * <p>
 * All operations except construction require {@link #initialize()} to have completed first.
 * </p>
 */
@Slf4j
public class StorageEngine {
    private final PageManager pageManager;
    private final RecordCodec recordCodec;
    private Superblock superblock;
    private DataPageManager dataPageManager;

    // In-memory indexes (will be persisted as B+ trees)
    private final Map<Long, EntryRecordLocation> primaryIndex = new ConcurrentHashMap<>(); // id -> location
    private final Map<String, Map<Long, EntryRecordLocation>> typeRelIndex = new ConcurrentHashMap<>(); // type -> (relId -> location)
    private final Map<UUID, List<EntryRecordLocation>> targetIndex = new ConcurrentHashMap<>(); // target UUID -> list of locations

    /**
     * Creates a storage engine. No file is opened until {@link #initialize()} is called.
     *
     * @param filePath       path to the storage file
     * @param pageSize       page size in bytes
     * @param maxCachedPages maximum number of pages held in the page cache
     * @param typeManager    type manager used to resolve entry types when decoding records
     * @throws IOException declared but not thrown by the current implementation
     */
    public StorageEngine(Path filePath, int pageSize, int maxCachedPages, TypeManager typeManager) throws IOException {
        this.pageManager = new PageManager(filePath, pageSize, maxCachedPages);
        this.recordCodec = new RecordCodec(typeManager);
    }

    /**
     * Opens the storage file, creating it if it does not exist.
     * <p>
     * An empty file is initialized with a new superblock. For an existing file, the superblock is read and the
     * in-memory indexes are rebuilt by scanning all pages; pages and records that cannot be read or decoded are
     * skipped and logged.
     * </p>
     *
     * @throws IOException if the file cannot be opened, read, or written
     * @throws StorageException if the superblock is invalid (bad magic, version, page size, page count, or CRC)
     */
    public void initialize() throws IOException {
        pageManager.initialize();

        try {
            if (pageManager.getFilePageCount() == 0) {
                // Create new file
                initializeNewFile();
            } else {
                // Open existing file
                openExistingFile();
            }
        } catch (IOException e) {
            pageManager.shutdown();
            throw e;
        }
    }

    private void initializeNewFile() throws IOException {
        // Create empty superblock
        superblock = Superblock.createEmpty();

        // Allocate and write superblock page
        byte[] superblockPage = new byte[pageManager.getPageSize()];
        superblock.toBytes(superblockPage, pageManager.getPageSize());
        pageManager.writePage(0, superblockPage);

        // Initialize data page manager
        dataPageManager = new DataPageManager(pageManager, superblock);

        pageManager.force();
        log.info("Initialized new storage file with page size {}", pageManager.getPageSize());
    }

    private void openExistingFile() throws IOException {
        // Read superblock
        byte[] superblockPage = pageManager.readPage(0);
        superblock = Superblock.fromBytes(superblockPage, pageManager.getPageSize());

        // Initialize data page manager
        dataPageManager = new DataPageManager(pageManager, superblock);

        // Load indexes from disk (simplified: just load all records)
        loadIndexes();

        log.info("Opened existing storage file with {} pages, next entry ID: {}", superblock.filePageCount, superblock.nextEntryId);
    }

    private void loadIndexes() throws IOException {
        // Scan all data pages and rebuild indexes
        long totalPages = superblock.filePageCount;
        for (long pageId = 1; pageId < totalPages; pageId++) {
            try {
                byte[] pageData = pageManager.readPage(pageId);
                if (isDataPage(pageData)) {
                    loadIndexesFromPage(pageId, pageData);
                }
            } catch (Exception e) {
                // Page may be free or corrupted, skip it
                log.debug("Skipping page {}: {}", pageId, e.getMessage());
            }
        }
    }

    private void loadIndexesFromPage(long pageId, byte[] pageData) throws StorageException {
        PageBuffer buf = new PageBuffer(pageData);
        
        // Skip page header (32 bytes)
        buf.position(32);

        // Read data page header
        short slotCount = buf.getShort();
        short freeStart = buf.getShort();
        // Skip freeEnd and fragmentedBytes
        buf.getShort();
        buf.getShort();

        // Skip reserved
        buf.getInt();

        // Read slot directory and reconstruct records
        for (int i = 0; i < slotCount; i++) {
            buf.position(44 + i * 8);
            short recordOffset = buf.getShort();
            short recordLength = buf.getShort();
            short slotGeneration = buf.getShort();
            byte slotFlags = buf.getByte();

            if (slotFlags == 1) { // USED
                byte[] recordBytes = new byte[recordLength];
                System.arraycopy(pageData, recordOffset, recordBytes, 0, recordLength);
                
                try {
                    EntryPersisted entry = recordCodec.decode(recordBytes);
                    EntryRecordLocation location = new EntryRecordLocation(pageId, i, slotGeneration, 0);
                    indexRecord(entry, location);
                } catch (Exception e) {
                    log.warn("Failed to decode record at page {} slot {}: {}", pageId, i, e.getMessage());
                }
            }
        }
    }

    private boolean isDataPage(byte[] pageData) {
        PageBuffer buf = new PageBuffer(pageData);
        byte pageType = buf.getByte();
        return pageType == 1; // Data page type
    }

    /**
     * Inserts a record and assigns it a new ID.
     * <p>
     * The ID of the given entry is ignored; the record is stored with the next ID from the superblock and with
     * version 0. The given entry object is not modified.
     * </p>
     *
     * @param entry the entry to insert
     * @return the assigned ID
     * @throws IOException if writing to the file fails
     * @throws StorageException if the encoded record is too large for a page
     */
    public long insertRecord(EntryPersisted entry) throws IOException {
        // Assign ID
        long id = superblock.nextEntryId++;
        entry = new EntryPersisted(
            id, entry.getRelId(), entry.getType(), entry.getTarget(), entry.getExecutor(),
            entry.getExpiryTime(), entry.getExecutingTime(), entry.getReason(),
            entry.getDataInternal(), entry.getWithdrawnBy(), entry.getWithdrawalReason(),
            entry.isOverridden(), entry.getOverriddenBy(), 0
        );

        // Encode record
        byte[] recordBytes = recordCodec.encode(entry);

        // Write to data page
        EntryRecordLocation location = dataPageManager.insertRecord(recordBytes);

        // Update indexes
        indexRecord(entry, location);

        // Persist superblock
        persistSuperblock();

        return id;
    }

    /**
     * Reads a record by ID.
     *
     * @param id the record ID
     * @return the decoded record, or empty if the ID is not in the primary index
     * @throws IOException if reading the file fails
     * @throws StorageException if the record cannot be decoded
     */
    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        EntryRecordLocation location = primaryIndex.get(id);
        if (location == null) {
            return Optional.empty();
        }
        return Optional.of(readRecord(location));
    }

    /**
     * Reads all records indexed for the given target.
     * <p>
     * Records that cannot be read are logged and omitted from the result.
     * </p>
     *
     * @param target the target UUID
     * @return the matching records (may be empty)
     * @throws IOException declared but read failures are caught and logged
     */
    public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
        List<EntryRecordLocation> locations = targetIndex.getOrDefault(target, new ArrayList<>());
        List<EntryPersisted> results = new ArrayList<>();
        for (EntryRecordLocation loc : locations) {
            try {
                results.add(readRecord(loc));
            } catch (Exception e) {
                log.warn("Failed to read record at location {}: {}", loc, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Reads the record indexed for the given type identifier and relId.
     * <p>
     * If the record cannot be read, the failure is logged and an empty list is returned.
     * </p>
     *
     * @param typeId the type identifier
     * @param relId  the type-relative ID
     * @return a list containing at most one record
     * @throws IOException declared but read failures are caught and logged
     */
    public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
        Map<Long, EntryRecordLocation> typeMap = typeRelIndex.getOrDefault(typeId, new HashMap<>());
        EntryRecordLocation location = typeMap.get(relId);
        if (location == null) {
            return new ArrayList<>();
        }
        try {
            List<EntryPersisted> results = new ArrayList<>();
            results.add(readRecord(location));
            return results;
        } catch (Exception e) {
            log.warn("Failed to read record at location {}: {}", location, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Replaces the stored record that has the same ID as the given entry, and updates the indexes.
     *
     * @param entry the new record content; its ID selects the record to replace
     * @throws IOException if reading or writing the file fails
     * @throws StorageException if no record with the entry's ID exists, or the record cannot be read or written
     */
    public void updateRecord(EntryPersisted entry) throws IOException {
        // Find existing location
        EntryRecordLocation location = primaryIndex.get(entry.getId());
        if (location == null) {
            throw new StorageException("Record not found: " + entry.getId());
        }

        // Read old entry to remove from secondary indexes if type/target changed
        EntryPersisted oldEntry = readRecord(location);

        // Encode and write
        byte[] recordBytes = recordCodec.encode(entry);
        EntryRecordLocation newLocation = dataPageManager.updateRecord(location, recordBytes);

        // Remove old secondary index entries
        removeFromSecondaryIndexes(oldEntry, location);

        // Update primary index with new location (may be same or different)
        primaryIndex.put(entry.getId(), newLocation);

        // Re-index with new data
        indexSecondaryIndexes(entry, newLocation);

        persistSuperblock();
    }

    /**
     * Deletes a record by ID by marking its slot free and removing it from the indexes.
     * <p>
     * Does nothing if no record with the given ID exists.
     * </p>
     *
     * @param id the record ID
     * @throws IOException if reading or writing the file fails
     * @throws StorageException if the record cannot be read
     */
    public void deleteRecord(long id) throws IOException {
        EntryRecordLocation location = primaryIndex.remove(id);
        if (location == null) {
            return;
        }

        // Read entry before deletion to get type and target for secondary index cleanup
        EntryPersisted entry = readRecord(location);

        // Mark slot as free
        dataPageManager.deleteRecord(location);

        // Remove from secondary indexes
        removeFromSecondaryIndexes(entry, location);

        persistSuperblock();
    }

    private EntryPersisted readRecord(EntryRecordLocation location) throws IOException {
        byte[] pageData = pageManager.readPage(location.pageId);
        PageBuffer buf = new PageBuffer(pageData);
        
        // Skip to slot entry
        buf.position(44 + location.slotIndex * 8);
        short recordOffset = buf.getShort();
        short recordLength = buf.getShort();

        byte[] recordBytes = new byte[recordLength];
        System.arraycopy(pageData, recordOffset, recordBytes, 0, recordLength);
        
        return recordCodec.decode(recordBytes);
    }

    private void indexRecord(EntryPersisted entry, EntryRecordLocation location) {
        primaryIndex.put(entry.getId(), location);
        indexSecondaryIndexes(entry, location);
    }

    private void indexSecondaryIndexes(EntryPersisted entry, EntryRecordLocation location) {
        String typeId = entry.getType().getIdentifier();
        typeRelIndex.computeIfAbsent(typeId, k -> new ConcurrentHashMap<>())
                    .put(entry.getRelId(), location);

        targetIndex.computeIfAbsent(entry.getTarget(), k -> new ArrayList<>())
                   .add(location);
    }

    private void removeFromSecondaryIndexes(EntryPersisted entry, EntryRecordLocation location) {
        // Remove from typeRelIndex
        String typeId = entry.getType().getIdentifier();
        Map<Long, EntryRecordLocation> typeMap = typeRelIndex.get(typeId);
        if (typeMap != null) {
            typeMap.remove(entry.getRelId());
            if (typeMap.isEmpty()) {
                typeRelIndex.remove(typeId);
            }
        }

        // Remove from targetIndex
        List<EntryRecordLocation> targetList = targetIndex.get(entry.getTarget());
        if (targetList != null) {
            targetList.remove(location);
            if (targetList.isEmpty()) {
                targetIndex.remove(entry.getTarget());
            }
        }
    }

    private void persistSuperblock() throws IOException {
        byte[] superblockPage = new byte[pageManager.getPageSize()];
        superblock.toBytes(superblockPage, pageManager.getPageSize());
        pageManager.writePage(0, superblockPage);
        pageManager.force();
    }

    /**
     * Writes the superblock and closes the storage file.
     *
     * @throws IOException if writing or closing the file fails
     */
    public void shutdown() throws IOException {
        persistSuperblock();
        pageManager.shutdown();
    }

    /**
     * Gets the in-memory superblock. The returned object is live, not a copy.
     *
     * @return the superblock, or null if {@link #initialize()} has not run
     */
    public Superblock getSuperblock() {
        return superblock;
    }

    /**
     * Gets the primary index (record ID to location).
     *
     * @return an unmodifiable view of the primary index; it reflects later changes
     */
    public Map<Long, EntryRecordLocation> getPrimaryIndex() {
        return Collections.unmodifiableMap(primaryIndex);
    }

    /**
     * Internal class to track record location in the file.
     * <p>
     * Does not override {@code equals}/{@code hashCode}; instances are compared by identity.
     * </p>
     */
    public static class EntryRecordLocation {
        /** ID of the data page containing the record. */
        public final long pageId;
        /** Index of the record's slot in the page's slot directory. */
        public final int slotIndex;
        /** Slot generation; currently always written as 0. */
        public final short slotGeneration;
        /** Page generation; currently always 0. */
        public final int pageGeneration;

        /**
         * Creates a record location.
         *
         * @param pageId         the data page ID
         * @param slotIndex      the slot index within the page
         * @param slotGeneration the slot generation
         * @param pageGeneration the page generation
         */
        public EntryRecordLocation(long pageId, int slotIndex, short slotGeneration, int pageGeneration) {
            this.pageId = pageId;
            this.slotIndex = slotIndex;
            this.slotGeneration = slotGeneration;
            this.pageGeneration = pageGeneration;
        }

        @Override
        public String toString() {
            return String.format("EntryRecordLocation(page=%d, slot=%d, gen=%d)", pageId, slotIndex, slotGeneration);
        }
    }
}
