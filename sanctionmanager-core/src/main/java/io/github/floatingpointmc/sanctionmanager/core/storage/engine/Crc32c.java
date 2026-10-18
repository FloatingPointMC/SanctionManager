package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * CRC32-style checksum computation used for records, pages and the superblock.
 * <p>
 * Uses the Castagnoli polynomial constant 0x1EDC6F41 (normal form) in a reflected (LSB-first) table algorithm.
 * Standard CRC-32C uses the reflected constant 0x82F63B78 in that algorithm, so the results of this class do
 * not match standard CRC-32C values. It is only used to check data written by this same class.
 * </p>
 */
public final class Crc32c {
    private static final int POLY = 0x1EDC6F41;
    private static final int[] LOOKUP_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLY;
                } else {
                    crc >>>= 1;
                }
            }
            LOOKUP_TABLE[i] = crc;
        }
    }

    /**
     * Computes the checksum of a byte range.
     *
     * @param data   the data
     * @param offset start index (inclusive)
     * @param length number of bytes
     * @return the checksum
     * @throws ArrayIndexOutOfBoundsException if the range is outside {@code data}
     */
    public static int compute(byte[] data, int offset, int length) {
        int crc = 0xFFFFFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = (crc >>> 8) ^ LOOKUP_TABLE[(crc ^ data[i]) & 0xFF];
        }
        return ~crc;
    }

    /**
     * Computes the checksum of an entire array.
     *
     * @param data the data
     * @return the checksum
     */
    public static int compute(byte[] data) {
        return compute(data, 0, data.length);
    }

    private Crc32c() {
        // Utility class
    }
}
