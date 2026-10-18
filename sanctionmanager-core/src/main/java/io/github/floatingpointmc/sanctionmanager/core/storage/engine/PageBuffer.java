package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility for reading and writing binary data with big-endian byte order.
 * <p>
 * Thin wrapper around a {@link ByteBuffer}. Relative methods advance the position; absolute (indexed) methods
 * do not. Methods throw the same unchecked exceptions as the corresponding {@code ByteBuffer} methods
 * (for example {@code BufferOverflowException}, {@code BufferUnderflowException},
 * {@code IndexOutOfBoundsException}, or {@code IllegalArgumentException} for an invalid position).
 * </p>
 */
public class PageBuffer {
    private final ByteBuffer buffer;

    /**
     * Wraps an existing array. Writes go directly to the array.
     *
     * @param data the backing array
     */
    public PageBuffer(byte[] data) {
        this.buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Allocates a new zero-filled buffer.
     *
     * @param capacity the capacity in bytes
     */
    public PageBuffer(int capacity) {
        this.buffer = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Gets the backing array (not a copy).
     *
     * @return the backing array
     */
    public byte[] getBytes() {
        return buffer.array();
    }

    /**
     * Sets the position.
     *
     * @param pos the new position
     */
    public void position(int pos) {
        buffer.position(pos);
    }

    /**
     * Gets the position.
     *
     * @return the current position
     */
    public int position() {
        return buffer.position();
    }

    /**
     * Gets the number of bytes between the position and the end of the buffer.
     *
     * @return the remaining byte count
     */
    public int remaining() {
        return buffer.remaining();
    }

    /**
     * Writes a byte at the position and advances it.
     *
     * @param value the value
     */
    public void putByte(byte value) {
        buffer.put(value);
    }

    /**
     * Reads a byte at the position and advances it.
     *
     * @return the value
     */
    public byte getByte() {
        return buffer.get();
    }

    /**
     * Writes a byte at the given index without changing the position.
     *
     * @param index the absolute index
     * @param value the value
     */
    public void putByte(int index, byte value) {
        buffer.put(index, value);
    }

    /**
     * Reads a byte at the given index without changing the position.
     *
     * @param index the absolute index
     * @return the value
     */
    public byte getByte(int index) {
        return buffer.get(index);
    }

    /**
     * Writes a short at the position and advances it.
     *
     * @param value the value
     */
    public void putShort(short value) {
        buffer.putShort(value);
    }

    /**
     * Reads a short at the position and advances it.
     *
     * @return the value
     */
    public short getShort() {
        return buffer.getShort();
    }

    /**
     * Writes a short at the given index without changing the position.
     *
     * @param index the absolute index
     * @param value the value
     */
    public void putShort(int index, short value) {
        buffer.putShort(index, value);
    }

    /**
     * Reads a short at the given index without changing the position.
     *
     * @param index the absolute index
     * @return the value
     */
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    /**
     * Writes a int at the position and advances it.
     *
     * @param value the value
     */
    public void putInt(int value) {
        buffer.putInt(value);
    }

    /**
     * Reads a int at the position and advances it.
     *
     * @return the value
     */
    public int getInt() {
        return buffer.getInt();
    }

    /**
     * Writes a int at the given index without changing the position.
     *
     * @param index the absolute index
     * @param value the value
     */
    public void putInt(int index, int value) {
        buffer.putInt(index, value);
    }

    /**
     * Reads a int at the given index without changing the position.
     *
     * @param index the absolute index
     * @return the value
     */
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    /**
     * Writes a long at the position and advances it.
     *
     * @param value the value
     */
    public void putLong(long value) {
        buffer.putLong(value);
    }

    /**
     * Reads a long at the position and advances it.
     *
     * @return the value
     */
    public long getLong() {
        return buffer.getLong();
    }

    /**
     * Writes a long at the given index without changing the position.
     *
     * @param index the absolute index
     * @param value the value
     */
    public void putLong(int index, long value) {
        buffer.putLong(index, value);
    }

    /**
     * Reads a long at the given index without changing the position.
     *
     * @param index the absolute index
     * @return the value
     */
    public long getLong(int index) {
        return buffer.getLong(index);
    }

    /**
     * Writes all bytes of an array at the position and advances it.
     *
     * @param data the bytes to write
     */
    public void put(byte[] data) {
        buffer.put(data);
    }

    /**
     * Writes a range of an array at the position and advances it.
     *
     * @param data   the source array
     * @param offset start index in {@code data}
     * @param length number of bytes
     */
    public void put(byte[] data, int offset, int length) {
        buffer.put(data, offset, length);
    }

    /**
     * Reads bytes at the position into the whole array and advances the position.
     *
     * @param data the destination array
     */
    public void get(byte[] data) {
        buffer.get(data);
    }

    /**
     * Reads bytes at the position into a range of the array and advances the position.
     *
     * @param data   the destination array
     * @param offset start index in {@code data}
     * @param length number of bytes
     */
    public void get(byte[] data, int offset, int length) {
        buffer.get(data, offset, length);
    }

    /**
     * Creates a big-endian duplicate of the underlying buffer. The duplicate shares content but has an
     * independent position.
     *
     * @return the duplicate buffer
     */
    public ByteBuffer toByteBuffer() {
        return buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
    }
}
