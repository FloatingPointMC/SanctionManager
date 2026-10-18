package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages page-based I/O for the binary storage file.
 * Implements page caching with LRU eviction.
 * <p>
 * Writes go to the file immediately and update the cache (write-through); cached pages are copied on read and
 * write. The constructor is Lombok-generated and takes the file path, page size and maximum number of cached
 * pages. Methods other than {@link #initialize()}, {@link #shutdown()}, {@link #clearCache()} and
 * {@link #getPageSize()} require {@link #initialize()} to have been called.
 * </p>
 */
@RequiredArgsConstructor
public class PageManager {
    private final Path filePath;
    /**
     *  The page size.
     */
    @Getter
    private final int pageSize;
    private final int maxCachedPages;
    
    private RandomAccessFile raf;
    private FileChannel channel;
    private final Map<Long, byte[]> pageCache = new LinkedHashMap<Long, byte[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > maxCachedPages;
        }
    };

    /**
     * Opens the file for reading and writing, creating it if it does not exist.
     *
     * @throws IOException if the file cannot be created or opened
     */
    public void initialize() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
        this.raf = new RandomAccessFile(filePath.toFile(), "rw");
        this.channel = raf.getChannel();
    }

    /**
     * Closes the file. Does nothing if the file was never opened.
     *
     * @throws IOException if closing fails
     */
    public void shutdown() throws IOException {
        if (channel != null) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
    }

    /**
     * Reads a page, from the cache if present.
     *
     * @param pageId the page ID
     * @return a copy of the page bytes
     * @throws IOException if reading the file fails
     * @throws StorageException if the page ID is negative or the page lies beyond the end of the file
     */
    public byte[] readPage(long pageId) throws IOException {
        if (pageId < 0) {
            throw new StorageException("Invalid page ID: " + pageId);
        }

        // Check cache
        if (pageCache.containsKey(pageId)) {
            return pageCache.get(pageId).clone();
        }

        // Read from file
        long offset = pageId * (long) pageSize;
        if (offset < 0) {
            throw new StorageException("Page offset overflow");
        }

        if (offset + pageSize > raf.length()) {
            throw new StorageException("Page " + pageId + " beyond file length");
        }

        byte[] pageData = new byte[pageSize];
        ByteBuffer buffer = ByteBuffer.wrap(pageData);
        
        int bytesRead = 0;
        while (bytesRead < pageSize) {
            int n = channel.read(buffer, offset + bytesRead);
            if (n < 0) {
                throw new StorageException("Unexpected EOF reading page " + pageId);
            }
            bytesRead += n;
        }

        // Cache the page
        pageCache.put(pageId, pageData.clone());
        return pageData;
    }

    /**
     * Writes a page to the file and updates the cache. Data is not forced to disk; see {@link #force()}.
     *
     * @param pageId   the page ID
     * @param pageData the page bytes; must be exactly one page long
     * @throws IOException if writing the file fails
     * @throws StorageException if the page ID is negative or the data length is not the page size
     */
    public void writePage(long pageId, byte[] pageData) throws IOException {
        if (pageId < 0) {
            throw new StorageException("Invalid page ID: " + pageId);
        }

        if (pageData.length != pageSize) {
            throw new StorageException("Page data size mismatch");
        }

        long offset = pageId * (long) pageSize;
        if (offset < 0) {
            throw new StorageException("Page offset overflow");
        }

        ByteBuffer buffer = ByteBuffer.wrap(pageData);
        int bytesWritten = 0;
        
        while (bytesWritten < pageSize) {
            int n = channel.write(buffer, offset + bytesWritten);
            bytesWritten += n;
        }

        // Update cache
        pageCache.put(pageId, pageData.clone());
    }

    /**
     * Gets the number of pages in the file, based on its length rounded up to whole pages.
     *
     * @return the page count
     * @throws IOException if the file length cannot be read
     */
    public long getFilePageCount() throws IOException {
        return (raf.length() + pageSize - 1) / pageSize;
    }

    /**
     * Sets the file length. The page cache is not updated.
     *
     * @param bytes the new length in bytes
     * @throws IOException if the length cannot be set
     */
    public void setFileLength(long bytes) throws IOException {
        raf.setLength(bytes);
    }

    /**
     * Forces file content (not metadata) to the storage device.
     *
     * @throws IOException if forcing fails
     */
    public void force() throws IOException {
        channel.force(false);
    }

    /**
     * Removes all pages from the cache.
     */
    public void clearCache() {
        pageCache.clear();
    }

}
