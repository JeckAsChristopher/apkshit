package com.locai.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LLMEngine {

    private static final String TAG = "LOCAI_Engine";

    public static final String MODEL_PATH = "/sdcard/locai/model";
    public static final String MODEL_FILE = MODEL_PATH + "/model.gguf";

    private static final int   N_CTX      = 2048; // reduced from 4096 — less RAM needed
    private static final int   MAX_TOKENS = 512;
    private static final int   CONTEXT_WIN = 20;

    private static final String SYSTEM_PROMPT =
            "You are LOCAI, a private AI assistant running fully offline on the user's device. " +
            "Be helpful and concise.";

    // ─── JNI loaded flag ─────────────────────────────────────────────────────
    private static boolean soLoaded = false;
    private static String  soError  = null;

    static {
        try {
            System.loadLibrary("locai_jni");
            soLoaded = true;
            Log.i(TAG, "liblocai_jni.so loaded OK");
        } catch (UnsatisfiedLinkError e) {
            soError = "Native library failed to load:\n" + e.getMessage();
            Log.e(TAG, soError);
        }
    }

    // ─── Singleton ───────────────────────────────────────────────────────────
    private static LLMEngine instance;
    private boolean isReady   = false;
    private boolean isLoading = false;

    private final ExecutorService inferThread = Executors.newSingleThreadExecutor();
    private final AtomicBoolean   cancelled   = new AtomicBoolean(false);

    public static synchronized LLMEngine getInstance() {
        if (instance == null) instance = new LLMEngine();
        return instance;
    }

    // ─── Callbacks ───────────────────────────────────────────────────────────
    public interface LoadCallback {
        void onProgress(String status, int progress);
        void onReady();
        void onError(String error);
    }

    public interface GenerateCallback {
        void onToken(String token);
        void onComplete(String fullResponse);
        void onError(String error);
    }

    // ─── Load ────────────────────────────────────────────────────────────────
    public void load(Context ctx, LoadCallback cb) {
        if (isReady)   { cb.onReady(); return; }
        if (isLoading) return;
        isLoading = true;

        inferThread.execute(() -> {
            try {
                // 1. Check .so loaded
                cb.onProgress("Initialising native engine…", 5);
                if (!soLoaded) {
                    throw new Exception(soError != null ? soError :
                        "liblocai_jni.so not found.\n\nRun build_so.sh first then rebuild the APK.");
                }

                // 2. Check file exists
                cb.onProgress("Checking model file…", 10);
                File f = new File(MODEL_FILE);
                if (!f.exists()) {
                    throw new Exception("Model file not found:\n" + MODEL_FILE);
                }

                // 3. Check file is readable by Java (catches permission issues)
                cb.onProgress("Checking storage access…", 15);
                try {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] header = new byte[4];
                    int read = fis.read(header);
                    fis.close();
                    if (read < 4) {
                        throw new Exception("Model file is empty or unreadable.");
                    }
                    // GGUF magic bytes: 0x47 0x47 0x55 0x46 ("GGUF")
                    if (header[0] != 0x47 || header[1] != 0x47 ||
                        header[2] != 0x55 || header[3] != 0x46) {
                        throw new Exception(
                            "File is not a valid GGUF model.\n\n" +
                            "It may be corrupted or incompletely downloaded.\n" +
                            "Please reinstall the model.");
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("GGUF")) throw e;
                    throw new Exception(
                        "Cannot read model file.\n\n" +
                        "Make sure LOCAI has 'All files access' permission in:\n" +
                        "Settings → Apps → LOCAI → Permissions → Files\n\n" +
                        "Error: " + e.getMessage());
                }

                // 4. Check free RAM roughly
                cb.onProgress("Checking available memory…", 20);
                Runtime rt = Runtime.getRuntime();
                long freeRam = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
                Log.i(TAG, "Free heap: " + (freeRam / 1024 / 1024) + " MB");
                // Don't block on this — just log. llama.cpp uses native malloc not JVM heap.

                // 5. Load via JNI — this is the slow blocking call
                cb.onProgress("Loading model weights into RAM…", 30);
                cb.onProgress("This may take 30–90 seconds…", 40);

                boolean ok = LlamaJNI.load(MODEL_FILE, N_CTX, 0); // 0 = CPU only

                if (!ok) {
                    throw new Exception(
                        "llama.cpp failed to load the model.\n\n" +
                        "Possible causes:\n" +
                        "• Not enough RAM (need 2–5 GB free)\n" +
                        "• Model file is corrupted\n" +
                        "• Unsupported GGUF format\n\n" +
                        "Try: reinstall a smaller model (Qwen 2.5 1.5B)");
                }

                isReady   = true;
                isLoading = false;
                cb.onProgress("Ready!", 100);
                cb.onReady();

            } catch (Exception e) {
                Log.e(TAG, "Load failed", e);
                isLoading = false;
                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                cb.onError(msg);
            }
        });
    }

    // ─── Generate ────────────────────────────────────────────────────────────
    public void generate(List<Message> history, String userInput, GenerateCallback cb) {
        if (!isReady) { cb.onError("Model not loaded."); return; }
        cancelled.set(false);

        inferThread.execute(() -> {
            try {
                String prompt = buildPrompt(history, userInput);
                StringBuilder full = new StringBuilder();

                boolean ok = LlamaJNI.generate(prompt, MAX_TOKENS, piece -> {
                    if (cancelled.get()) return;
                    full.append(piece);
                    cb.onToken(piece);
                });

                if (!cancelled.get()) {
                    if (ok) cb.onComplete(full.toString().trim());
                    else    cb.onError("Generation failed.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Generate error", e);
                cb.onError("Error: " + e.getMessage());
            }
        });
    }

    public void cancelGeneration() { cancelled.set(true); }

    // ─── Prompt ──────────────────────────────────────────────────────────────
    private String buildPrompt(List<Message> history, String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n").append(SYSTEM_PROMPT).append("<|im_end|>\n");

        int start = Math.max(0, history.size() - CONTEXT_WIN);
        for (int i = start; i < history.size(); i++) {
            Message m = history.get(i);
            sb.append("<|im_start|>").append(m.isUser() ? "user" : "assistant").append("\n")
              .append(m.getContent()).append("<|im_end|>\n");
        }
        sb.append("<|im_start|>user\n").append(userInput).append("<|im_end|>\n")
          .append("<|im_start|>assistant\n");
        return sb.toString();
    }

    // ─── State ───────────────────────────────────────────────────────────────
    public boolean isReady()   { return isReady; }
    public boolean isLoading() { return isLoading; }

    public void unload() {
        inferThread.execute(() -> {
            LlamaJNI.unload();
            isReady = false;
        });
    }
}
