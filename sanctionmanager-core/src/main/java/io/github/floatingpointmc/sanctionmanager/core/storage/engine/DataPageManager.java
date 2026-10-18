package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages data pages including slot allocation, record insertion, and page compaction.
 * Implements slotted page layout with forward-growing slot directory and backward-growing record area.
 * <p>
 * The constructor is Lombok-generated and takes the page manager and the superblock, whose
 * {@code filePageCount} is incremented when a new page is allocated.
 * </p>
 */
@RequiredArgsConstructor
public class DataPageManager {
    private final PageManager pageManager;
    private final Superblock superblock;

    private static final byte PAGE_TYPE_DATA = 1;
    private static final byte PAGE_TYPE_FREE = 5;
    private static final int PAGE_HEADER_SIZE = 32;
    private static final int DATA_PAGE_HEADER_SIZE = 12;
    private static final int SLOT_ENTRY_SIZE = 8;

    /**
     * Inserts a record into an available data page.
     * Creates a new page if necessary.
     *
     * @param recordBytes the encoded record
     * @return the location of the inserted record
     * @throws IOException if reading or writing pages fails
     * @throws StorageException if the record is larger than the maximum record size for the page size
     */
    public StorageEngine.EntryRecordLocation insertRecord(byte[] recordBytes) throws IOException {
        if (recordBytes.length > getMaxRecordSize()) {
            throw new StorageException("Record too large: " + recordBytes.length);
        }

        // Try to find a page with enough space
        long currentPageId = findPageWithSpace(recordBytes.length);
        if (currentPageId < 0) {
            currentPageId = allocatePage();
        }

        return insertIntoPage(currentPageId, recordBytes);
    }

    private StorageEngine.EntryRecordLocation insertIntoPage(long pageId, byte[] recordBytes) throws IOException {
        byte[] pageData = pageManager.readPage(pageId);
        PageBuffer buf = new PageBuffer(pageData);

        // Read page header
        buf.position(PAGE_HEADER_SIZE);
        short slotCount = buf.getShort();
        short freeStart = buf.getShort();
        short freeEnd = buf.getShort();

        // Calculate space needed
        int recordSize = recordBytes.length;
        int neededSlots = 1;
        int slotDirSize = SLOT_ENTRY_SIZE * neededSlots;

        // Check if we have enough space
        if (freeStart + slotDirSize + recordSize > freeEnd) {
            compactPage(pageId);
            pageData = pageManager.readPage(pageId);
            buf = new PageBuffer(pageData);
            buf.position(PAGE_HEADER_SIZE);
            slotCount = buf.getShort();
            freeStart = buf.getShort();
            freeEnd = buf.getShort();
        }

        // Find a free slot
        int slotIndex = findFreeSlot(pageData, slotCount);
        if (slotIndex >= 0) {
            // Reuse existing free slot
        } else {
            slotIndex = slotCount;
        }

        // Calculate record offset (from end of page going backward)
        short recordOffset = (short) (freeEnd - recordSize);

        // Write record data
        System.arraycopy(recordBytes, 0, pageData, recordOffset, recordSize);

        // Write slot entry
        int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + slotIndex * SLOT_ENTRY_SIZE;
        buf.position(slotPos);
        buf.putShort(recordOffset);
        buf.putShort((short) recordSize);
        buf.putShort((short) 0); // slotGeneration
        buf.putByte((byte) 1); // slotFlags USED
        buf.putByte((byte) 0); // reserved

        // Update page header
        short newFreeStart = (short) (PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + (slotIndex + 1) * SLOT_ENTRY_SIZE);
        short newFreeEnd = recordOffset;

        buf.position(PAGE_HEADER_SIZE);
        if (slotIndex >= slotCount) {
            buf.putShort((short) (slotIndex + 1)); // slotCount
        } else {
            buf.putShort(slotCount);
        }
        buf.putShort(newFreeStart);
        buf.putShort(newFreeEnd);

        // Compute page CRC
        writePageCrc(pageData);

        // Write page back
        pageManager.writePage(pageId, pageData);

        return new StorageEngine.EntryRecordLocation(pageId, slotIndex, (short) 0, 0);
    }

    /**
     * Updates a record in a data page.
     * <p>
     * If the new record has the same length, it is overwritten in place. Otherwise the old slot is freed and
     * the record is reinserted into the same page (compacting it if needed); it is never moved to another page.
     * The implementation does not check that the record fits after compaction.
     * </p>
     *
     * @param location       the current location of the record
     * @param newRecordBytes the new encoded record
     * @return the location of the updated record (may be different from input location)
     * @throws IOException if reading or writing pages fails
     */
    public StorageEngine.EntryRecordLocation updateRecord(StorageEngine.EntryRecordLocation location, byte[] newRecordBytes) throws IOException {
        byte[] pageData = pageManager.readPage(location.pageId);

        // Read current slot
        int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + location.slotIndex * SLOT_ENTRY_SIZE;
        PageBuffer buf = new PageBuffer(pageData);
        buf.position(slotPos);
        short oldOffset = buf.getShort();
        short oldLength = buf.getShort();

        // Check if new record fits in the same location
        if (newRecordBytes.length == oldLength) {
            // In-place update
            System.arraycopy(newRecordBytes, 0, pageData, oldOffset, newRecordBytes.length);
            writePageCrc(pageData);
            pageManager.writePage(location.pageId, pageData);
            return location; // Same location
        } else {
            // Mark old slot as free and insert as new record
            markSlotFree(pageData, location.slotIndex);
            pageManager.writePage(location.pageId, pageData);

            // Insert new record (may go to different page) and return new location
            return insertIntoPage(location.pageId, newRecordBytes);
        }
    }

    /**
     * Deletes a record by marking its slot as free. The record bytes are not cleared.
     *
     * @param location the location of the record
     * @throws IOException if reading or writing the page fails
     */
    public void deleteRecord(StorageEngine.EntryRecordLocation location) throws IOException {
        byte[] pageData = pageManager.readPage(location.pageId);
        markSlotFree(pageData, location.slotIndex);
        writePageCrc(pageData);
        pageManager.writePage(location.pageId, pageData);
    }

    private int findFreeSlot(byte[] pageData, short slotCount) {
        for (int i = 0; i < slotCount; i++) {
            int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + i * SLOT_ENTRY_SIZE;
            byte slotFlags = pageData[slotPos + 6];
            if (slotFlags == 0) { // FREE
                return i;
            }
        }
        return -1;
    }

    private byte getSlotFlags(byte[] pageData, int slotIndex) {
        int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + slotIndex * SLOT_ENTRY_SIZE;
        return pageData[slotPos + 6];
    }

    private void markSlotFree(byte[] pageData, int slotIndex) {
        int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + slotIndex * SLOT_ENTRY_SIZE;
        pageData[slotPos + 6] = 0; // slotFlags = FREE
    }

    private long findPageWithSpace(int recordSize) throws IOException {
        // For now, just return the first data page if it has space
        // In production, would scan pages more intelligently
        long totalPages = pageManager.getFilePageCount();
        for (long pageId = 1; pageId < totalPages; pageId++) {
            try {
                byte[] pageData = pageManager.readPage(pageId);
                if (canFitRecord(pageData, recordSize)) {
                    return pageId;
                }
            } catch (Exception e) {
                // Page may be free or corrupted, skip
            }
        }
        return -1;
    }

    private boolean canFitRecord(byte[] pageData, int recordSize) {
        PageBuffer buf = new PageBuffer(pageData);
        buf.position(PAGE_HEADER_SIZE);
        short slotCount = buf.getShort();
        short freeStart = buf.getShort();
        short freeEnd = buf.getShort();

        int slotDirSize = SLOT_ENTRY_SIZE;
        return freeStart + slotDirSize + recordSize <= freeEnd;
    }

    private long allocatePage() throws IOException {
        long newPageId = pageManager.getFilePageCount();
        superblock.filePageCount++;

        byte[] pageData = new byte[pageManager.getPageSize()];
        initializePage(pageData, newPageId, PAGE_TYPE_DATA);
        pageManager.writePage(newPageId, pageData);

        return newPageId;
    }

    private void initializePage(byte[] pageData, long pageId, byte pageType) {
        PageBuffer buf = new PageBuffer(pageData);

        // Common header (32 bytes)
        buf.putByte(pageType);
        buf.putByte((byte) 0); // pageFlags
        buf.putShort((short) 32); // headerLength
        buf.putLong(pageId);
        buf.putLong(0); // pageLsn
        buf.putInt(0); // pageCrc32c (will be updated)
        buf.putInt(0); // generation
        buf.putInt(0); // reserved

        if (pageType == PAGE_TYPE_DATA) {
            // Data page header
            buf.putShort((short) 0); // slotCount
            buf.putShort((short) (PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE)); // freeStart
            buf.putShort((short) pageManager.getPageSize()); // freeEnd
            buf.putShort((short) 0); // fragmentedBytes
            buf.putInt(0); // reserved
        }

        writePageCrc(pageData);
    }

    private void writePageCrc(byte[] pageData) {
        // Zero out CRC field first
        PageBuffer buf = new PageBuffer(pageData);
        buf.position(20);
        buf.putInt(0);

        // Compute CRC over entire page
        int crc = Crc32c.compute(pageData);

        // Write CRC
        buf.position(20);
        buf.putInt(crc);
    }

    private void compactPage(long pageId) throws IOException {
        byte[] pageData = pageManager.readPage(pageId);
        PageBuffer buf = new PageBuffer(pageData);

        // Read current state
        buf.position(PAGE_HEADER_SIZE);
        short slotCount = buf.getShort();

        // Collect all used records
        List<byte[]> usedRecords = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            buf.position(PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + i * SLOT_ENTRY_SIZE);
            short offset = buf.getShort();
            short length = buf.getShort();
            byte flags = getSlotFlags(pageData, i);

            if (flags == 1) { // USED
                byte[] recordData = new byte[length];
                System.arraycopy(pageData, offset, recordData, 0, length);
                usedRecords.add(recordData);
            }
        }

        // Reinitialize page
        byte[] newPageData = new byte[pageManager.getPageSize()];
        initializePage(newPageData, pageId, PAGE_TYPE_DATA);

        // Reinsert records
        int slotIndex = 0;
        short freeEnd = (short) pageManager.getPageSize();
        for (byte[] recordData : usedRecords) {
            freeEnd -= recordData.length;
            System.arraycopy(recordData, 0, newPageData, freeEnd, recordData.length);

            // Write slot entry
            int slotPos = PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + slotIndex * SLOT_ENTRY_SIZE;
            buf = new PageBuffer(newPageData);
            buf.position(slotPos);
            buf.putShort(freeEnd);
            buf.putShort((short) recordData.length);
            buf.putShort((short) 0);
            buf.putByte((byte) 1);
            buf.putByte((byte) 0);

            slotIndex++;
        }

        // Update page header
        buf.position(PAGE_HEADER_SIZE);
        buf.putShort((short) slotIndex);
        buf.putShort((short) (PAGE_HEADER_SIZE + DATA_PAGE_HEADER_SIZE + slotIndex * SLOT_ENTRY_SIZE));
        buf.putShort(freeEnd);

        writePageCrc(newPageData);
        pageManager.writePage(pageId, newPageData);
    }

    private int getMaxRecordSize() {
        return pageManager.getPageSize() - PAGE_HEADER_SIZE - DATA_PAGE_HEADER_SIZE - 100;
    }
}
