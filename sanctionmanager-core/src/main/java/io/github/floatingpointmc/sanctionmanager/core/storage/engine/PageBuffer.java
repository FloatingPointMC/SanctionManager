package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility for reading and writing binary data with big-endian byte order.
 */
public class PageBuffer {
    private final ByteBuffer buffer;

    public PageBuffer(byte[] data) {
        this.buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
    }

    public PageBuffer(int capacity) {
        this.buffer = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN);
    }

    public byte[] getBytes() {
        return buffer.array();
    }

    public void position(int pos) {
        buffer.position(pos);
    }

    public int position() {
        return buffer.position();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public void putByte(byte value) {
        buffer.put(value);
    }

    public byte getByte() {
        return buffer.get();
    }

    public void putByte(int index, byte value) {
        buffer.put(index, value);
    }

    public byte getByte(int index) {
        return buffer.get(index);
    }

    public void putShort(short value) {
        buffer.putShort(value);
    }

    public short getShort() {
        return buffer.getShort();
    }

    public void putShort(int index, short value) {
        buffer.putShort(index, value);
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public void putInt(int value) {
        buffer.putInt(value);
    }

    public int getInt() {
        return buffer.getInt();
    }

    public void putInt(int index, int value) {
        buffer.putInt(index, value);
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public void putLong(long value) {
        buffer.putLong(value);
    }

    public long getLong() {
        return buffer.getLong();
    }

    public void putLong(int index, long value) {
        buffer.putLong(index, value);
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public void put(byte[] data) {
        buffer.put(data);
    }

    public void put(byte[] data, int offset, int length) {
        buffer.put(data, offset, length);
    }

    public void get(byte[] data) {
        buffer.get(data);
    }

    public void get(byte[] data, int offset, int length) {
        buffer.get(data, offset, length);
    }

    public ByteBuffer toByteBuffer() {
        return buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
    }
}
