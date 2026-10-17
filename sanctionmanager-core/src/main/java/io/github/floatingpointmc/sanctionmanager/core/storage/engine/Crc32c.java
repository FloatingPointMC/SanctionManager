package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

/**
 * CRC32C (Castagnoli) checksum computation.
 * Uses polynomial 0x1EDC6F41 (the one used by iSCSI, btrfs, etc.).
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

    public static int compute(byte[] data, int offset, int length) {
        int crc = 0xFFFFFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = (crc >>> 8) ^ LOOKUP_TABLE[(crc ^ data[i]) & 0xFF];
        }
        return crc ^ 0xFFFFFFFF;
    }

    public static int compute(byte[] data) {
        return compute(data, 0, data.length);
    }

    private Crc32c() {
        // Utility class
    }
}
