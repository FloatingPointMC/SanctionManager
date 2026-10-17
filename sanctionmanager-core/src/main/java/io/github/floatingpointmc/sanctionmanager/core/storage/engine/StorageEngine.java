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
 * Manages pages, records, and B+ tree indexes.
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

    public StorageEngine(Path filePath, int pageSize, int maxCachedPages, TypeManager typeManager) throws IOException {
        this.pageManager = new PageManager(filePath, pageSize, maxCachedPages);
        this.recordCodec = new RecordCodec(typeManager);
    }

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

    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        EntryRecordLocation location = primaryIndex.get(id);
        if (location == null) {
            return Optional.empty();
        }
        return Optional.of(readRecord(location));
    }

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

    public void updateRecord(EntryPersisted entry) throws IOException {
        // Find existing location
        EntryRecordLocation location = primaryIndex.get(entry.getId());
        if (location == null) {
            throw new StorageException("Record not found: " + entry.getId());
        }

        // Encode and write
        byte[] recordBytes = recordCodec.encode(entry);
        dataPageManager.updateRecord(location, recordBytes);

        // Update indexes if needed
        primaryIndex.put(entry.getId(), location);

        persistSuperblock();
    }

    public void deleteRecord(long id) throws IOException {
        EntryRecordLocation location = primaryIndex.remove(id);
        if (location == null) {
            return;
        }

        // Mark slot as free
        dataPageManager.deleteRecord(location);

        // Remove from secondary indexes
        // TODO: Remove from type/rel and target indexes

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
        
        String typeId = entry.getType().getIdentifier();
        typeRelIndex.computeIfAbsent(typeId, k -> new ConcurrentHashMap<>())
                    .put(entry.getRelId(), location);
        
        targetIndex.computeIfAbsent(entry.getTarget(), k -> new ArrayList<>())
                   .add(location);
    }

    private void persistSuperblock() throws IOException {
        byte[] superblockPage = new byte[pageManager.getPageSize()];
        superblock.toBytes(superblockPage, pageManager.getPageSize());
        pageManager.writePage(0, superblockPage);
        pageManager.force();
    }

    public void shutdown() throws IOException {
        persistSuperblock();
        pageManager.shutdown();
    }

    public Superblock getSuperblock() {
        return superblock;
    }

    public Map<Long, EntryRecordLocation> getPrimaryIndex() {
        return Collections.unmodifiableMap(primaryIndex);
    }

    /**
     * Internal class to track record location in the file.
     */
    public static class EntryRecordLocation {
        public final long pageId;
        public final int slotIndex;
        public final short slotGeneration;
        public final int pageGeneration;

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
