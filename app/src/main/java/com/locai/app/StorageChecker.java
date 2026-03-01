package com.locai.app;

import android.os.Environment;
import android.os.StatFs;

/**
 * Checks available storage before loading the app.
 * We need at least 1.6 GB free for model weights + runtime headroom.
 */
public class StorageChecker {

    private static final long MIN_BYTES = 1_000L * 1024 * 1024; // 1.6 GB

    /** Returns available bytes on external / primary storage. */
    public static long getAvailableBytes() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            // Fall back to internal storage
            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    public static boolean hasSufficientStorage() {
        return getAvailableBytes() >= MIN_BYTES;
    }

    /** Human-readable format. */
    public static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        } else {
            return String.format("%.0f MB", bytes / (1024.0 * 1024));
        }
    }
}
