package com.locai.app;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads a single MediaPipe .task model file from HuggingFace.
 * Much simpler than the MLC approach — just one file, no shards.
 *
 */
public class ModelDownloader {

    private static final String TAG      = "LOCAI_Downloader";
    private static final String HF_BASE  = "https://huggingface.co/";
    private static final int    BUF_SIZE = 128 * 1024;           // 128 KB buffer
    private static final int    SPEED_WINDOW_MS = 2000;

    public static final String MODEL_FILE = LLMEngine.MODEL_FILE;

    // ─── Callback ────────────────────────────────────────────────────────────

    public interface DownloadCallback {
        void onStart(long totalBytes);
        void onProgress(long bytesDone, long totalBytes,
                        float speedMbps, long etaSeconds,
                        int percent);
        void onComplete();
        void onError(String message);
    }

    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final AtomicBoolean   cancelled = new AtomicBoolean(false);

    // ─── Public API ──────────────────────────────────────────────────────────

    public void download(ModelInfo model, DownloadCallback cb) {
        cancelled.set(false);
        executor.execute(() -> run(model, cb));
    }

    public void cancel() { cancelled.set(true); }

    // ─── Core ────────────────────────────────────────────────────────────────

    private void run(ModelInfo model, DownloadCallback cb) {
        // Build the HuggingFace direct download URL
        // Format: https://huggingface.co/{repo}/resolve/main/{filename}
        String url = HF_BASE + model.hfRepo + "/resolve/main/" + model.hfFilename;
        Log.d(TAG, "Downloading: " + url);

        File destDir  = new File(LLMEngine.MODEL_PATH);
        File destFile = new File(MODEL_FILE);
        destDir.mkdirs();

        // Resume support — check if partial file exists
        long resumeFrom = 0;
        if (destFile.exists() && destFile.length() > 0
                && destFile.length() < model.sizeBytes) {
            resumeFrom = destFile.length();
            Log.d(TAG, "Resuming from byte " + resumeFrom);
        } else if (destFile.exists() && destFile.length() == model.sizeBytes) {
            // Already complete
            cb.onComplete();
            return;
        }

        HttpURLConnection conn = null;
        InputStream      in   = null;
        FileOutputStream out  = null;

        long windowStart       = System.currentTimeMillis();
        long windowBytes       = 0;
        float smoothedSpeedMbps = 0;

        try {
            conn = openConnection(url, resumeFrom);
            int code = conn.getResponseCode();

            if (code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("HTTP " + code + " from server.\n" +
                        "Check your internet connection or try again later.");
            }

            // Content-Length is the REMAINING bytes, add resume offset for total
            long contentLength = conn.getContentLengthLong();
            long totalBytes    = (contentLength > 0)
                    ? contentLength + resumeFrom
                    : model.sizeBytes;  // fallback to known size

            cb.onStart(totalBytes);

            in  = new BufferedInputStream(conn.getInputStream(), BUF_SIZE);
            // Append if resuming
            out = new FileOutputStream(destFile, resumeFrom > 0);

            byte[] buf         = new byte[BUF_SIZE];
            int    read;
            long   bytesDone   = resumeFrom;
            long   lastCbBytes = resumeFrom;

            while ((read = in.read(buf)) != -1) {
                if (cancelled.get()) {
                    cb.onError("Download cancelled.");
                    return;
                }

                out.write(buf, 0, read);
                bytesDone   += read;
                windowBytes += read;

                long now     = System.currentTimeMillis();
                long elapsed = now - windowStart;
                if (elapsed >= SPEED_WINDOW_MS) {
                    float raw = (windowBytes / (float) elapsed) * 1000f / (1024 * 1024);
                    smoothedSpeedMbps = smoothedSpeedMbps == 0
                            ? raw : smoothedSpeedMbps * 0.55f + raw * 0.45f;
                    windowStart = now;
                    windowBytes = 0;
                }

                // Fire callback every ~512 KB
                if (bytesDone - lastCbBytes >= 512 * 1024) {
                    int  percent   = (int) ((bytesDone * 100L) / totalBytes);
                    long remaining = totalBytes - bytesDone;
                    long eta       = (smoothedSpeedMbps > 0)
                            ? (long) (remaining / (smoothedSpeedMbps * 1024 * 1024))
                            : -1;
                    cb.onProgress(bytesDone, totalBytes,
                            smoothedSpeedMbps, eta, percent);
                    lastCbBytes = bytesDone;
                }
            }

            out.flush();

            if (!cancelled.get()) cb.onComplete();

        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            cb.onError(e.getMessage() != null ? e.getMessage() : "Unknown download error");
        } finally {
            try { if (out  != null) out.close();  } catch (IOException ignored) {}
            try { if (in   != null) in.close();   } catch (IOException ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    // ─── HTTP helpers ────────────────────────────────────────────────────────

    private HttpURLConnection openConnection(String urlStr, long resumeFrom) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(90_000);
        conn.setRequestProperty("User-Agent", "LOCAI-Android/1.0");
        conn.setInstanceFollowRedirects(true);
        if (resumeFrom > 0) {
            conn.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
        }
        return conn;
    }
}
