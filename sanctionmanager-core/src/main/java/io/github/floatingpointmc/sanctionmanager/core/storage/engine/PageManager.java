package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Manages page-based I/O for the binary storage file.
 * Implements page caching with LRU eviction.
 */
@RequiredArgsConstructor
public class PageManager {
    private final Path filePath;
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

    public void initialize() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
        this.raf = new RandomAccessFile(filePath.toFile(), "rw");
        this.channel = raf.getChannel();
    }

    public void shutdown() throws IOException {
        if (channel != null) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
    }

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

    public long getFilePageCount() throws IOException {
        return (raf.length() + pageSize - 1) / pageSize;
    }

    public void setFileLength(long bytes) throws IOException {
        raf.setLength(bytes);
    }

    public void force() throws IOException {
        channel.force(false);
    }

    public void clearCache() {
        pageCache.clear();
    }

    public int getPageSize() {
        return pageSize;
    }
}
