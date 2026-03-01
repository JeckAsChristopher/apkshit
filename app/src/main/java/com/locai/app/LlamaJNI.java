package com.locai.app;

/**
 * JNI bridge to llama.cpp via liblocai_jni.so
 *
 * The .so is built by CMakeLists.txt from app/src/main/cpp/locai_jni.cpp
 * using the llama.cpp source cloned into app/src/main/cpp/llama.cpp/
 */
public final class LlamaJNI {

    static {
        System.loadLibrary("locai_jni");
    }

    /**
     * Callback fired for each generated token during inference.
     * Called from the C++ JNI layer on the inference thread.
     */
    public interface TokenCallback {
        void onToken(String piece);
    }

    /**
     * Load a GGUF model file into memory.
     *
     * @param modelPath    Absolute path to the .gguf file
     * @param nCtx         Context window size (e.g. 4096)
     * @param nGpuLayers   GPU layers (0 = CPU-only, 99 = all on GPU if available)
     * @return true if load succeeded
     */
    public static native boolean load(String modelPath, int nCtx, int nGpuLayers);

    /**
     * Generate a response for the given prompt, streaming tokens via callback.
     *
     * @param prompt     Full formatted prompt string (system + history + user)
     * @param maxTokens  Max tokens to generate
     * @param callback   Receives each token piece as it is sampled
     * @return true if generation completed without error
     */
    public static native boolean generate(String prompt, int maxTokens, TokenCallback callback);

    /**
     * Free the model and context from memory.
     */
    public static native void unload();
}
