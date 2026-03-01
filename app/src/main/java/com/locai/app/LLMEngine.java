package com.locai.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LOCAI LLM Engine
 *
 * Wraps the JNI bridge to llama.cpp (locai_jni.cpp → liblocai_jni.so).
 * Runs any GGUF model (Q4_K_M recommended) fully offline on-device.
 *
 * Model file:  /sdcard/locai/model/model.gguf
 */
public class LLMEngine {

    private static final String TAG = "LOCAI_Engine";

    public static final String MODEL_PATH = "/sdcard/locai/model";
    public static final String MODEL_FILE = MODEL_PATH + "/model.gguf";

    // llama.cpp inference config
    private static final int    N_CTX       = 4096;   // context window tokens
    private static final int    N_GPU       = 0;      // 0 = CPU-only (safe default)
    private static final int    MAX_TOKENS  = 512;    // max generation length
    private static final int    CONTEXT_WIN = 30;     // history messages to include

    private static final String SYSTEM_PROMPT =
            "You are LOCAI, a private AI assistant running fully offline on the user's device. " +
            "No data ever leaves the device. Be helpful, concise, and honest.";

    // ─── State ───────────────────────────────────────────────────────────────

    private static LLMEngine instance;
    private        boolean   isReady   = false;
    private        boolean   isLoading = false;

    private final ExecutorService inferThread = Executors.newSingleThreadExecutor();
    private final AtomicBoolean   cancelled   = new AtomicBoolean(false);

    public static synchronized LLMEngine getInstance() {
        if (instance == null) instance = new LLMEngine();
        return instance;
    }

    private LLMEngine() {}

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
                cb.onProgress("Checking model file…", 5);
                if (!new File(MODEL_FILE).exists()) {
                    throw new Exception(
                            "Model not found at:\n" + MODEL_FILE +
                            "\n\nPlease use the model installer to download a model.");
                }

                cb.onProgress("Loading model weights into RAM…", 25);
                cb.onProgress("This may take 15–60 seconds…", 40);

                // LlamaJNI.load() is blocking — can take a while
                boolean ok = LlamaJNI.load(MODEL_FILE, N_CTX, N_GPU);

                if (!ok) throw new Exception(
                        "llama.cpp failed to load the model.\n" +
                        "Possible causes:\n" +
                        "• Not enough free RAM (need 2–5 GB)\n" +
                        "• Corrupted GGUF file\n" +
                        "• Model format not supported");

                cb.onProgress("Ready!", 100);
                isReady   = true;
                isLoading = false;
                cb.onReady();

            } catch (Exception e) {
                Log.e(TAG, "Load error", e);
                isLoading = false;
                cb.onError(e.getMessage() != null ? e.getMessage() : "Unknown load error");
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
                    else    cb.onError("Generation failed — check logcat for details.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Generation error", e);
                cb.onError("Generation error: " + e.getMessage());
            }
        });
    }

    public void cancelGeneration() {
        cancelled.set(true);
    }

    // ─── Prompt builder ──────────────────────────────────────────────────────

    /**
     * Formats a standard ChatML-style prompt that works with most GGUF models:
     * Mistral, Llama 3, Phi-3, Gemma 2, Qwen 2.5
     */
    private String buildPrompt(List<Message> history, String userInput) {
        StringBuilder sb = new StringBuilder();

        // System turn
        sb.append("<|im_start|>system\n")
          .append(SYSTEM_PROMPT)
          .append("<|im_end|>\n");

        // History window
        int start = Math.max(0, history.size() - CONTEXT_WIN);
        for (int i = start; i < history.size(); i++) {
            Message m    = history.get(i);
            String  role = m.isUser() ? "user" : "assistant";
            sb.append("<|im_start|>").append(role).append("\n")
              .append(m.getContent())
              .append("<|im_end|>\n");
        }

        // Current user turn + assistant start
        sb.append("<|im_start|>user\n")
          .append(userInput)
          .append("<|im_end|>\n")
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
