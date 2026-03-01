package com.locai.app;

/**
 * Available GGUF models downloadable from HuggingFace.
 * Llamatik uses llama.cpp which runs any GGUF Q4 model directly.
 * Single file download — no shards, no special format conversion.
 */
public class ModelInfo {

    public enum Speed { VERY_FAST, FAST, MEDIUM }

    public final String id;
    public final String name;
    public final String subtitle;
    public final String hfRepo;
    public final String hfFilename;
    public final long   sizeBytes;
    public final Speed  speed;
    public final int    qualityStars;
    public final String description;

    public ModelInfo(String id, String name, String subtitle,
                     String hfRepo, String hfFilename,
                     long sizeBytes, Speed speed,
                     int qualityStars, String description) {
        this.id           = id;
        this.name         = name;
        this.subtitle     = subtitle;
        this.hfRepo       = hfRepo;
        this.hfFilename   = hfFilename;
        this.sizeBytes    = sizeBytes;
        this.speed        = speed;
        this.qualityStars = qualityStars;
        this.description  = description;
    }

    public String getSizeLabel() {
        double gb = sizeBytes / (1024.0 * 1024 * 1024);
        if (gb >= 1.0) return String.format("%.1f GB", gb);
        return String.format("%.0f MB", sizeBytes / (1024.0 * 1024));
    }

    public String getSpeedLabel() {
        switch (speed) {
            case VERY_FAST: return "Very fast";
            case FAST:      return "Fast";
            default:        return "Medium";
        }
    }

    // ─── Available models (GGUF Q4, single file) ─────────────────────────────

    public static final ModelInfo[] ALL = {
        new ModelInfo(
            "qwen2_5_1b",
            "Qwen 2.5 1.5B",
            "Q4_K_M · 1.5B params",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            986_000_000L,
            Speed.VERY_FAST,
            3,
            "Alibaba's tiny but capable model. Fastest inference, great for quick answers. Needs only ~1 GB."
        ),
        new ModelInfo(
            "phi3_mini",
            "Phi-3 Mini",
            "Q4_K_M · 3.8B params",
            "microsoft/Phi-3-mini-4k-instruct-gguf",
            "Phi-3-mini-4k-instruct-q4.gguf",
            2_200_000_000L,
            Speed.FAST,
            4,
            "Microsoft's compact powerhouse. Best balance of speed and quality. Recommended for most devices."
        ),
        new ModelInfo(
            "gemma2_2b",
            "Gemma 2 2B",
            "Q4_K_M · 2.6B params",
            "bartowski/gemma-2-2b-it-GGUF",
            "gemma-2-2b-it-Q4_K_M.gguf",
            1_600_000_000L,
            Speed.FAST,
            4,
            "Google's Gemma 2 in GGUF format. Excellent instruction following, great for chat."
        ),
        new ModelInfo(
            "llama3_2_3b",
            "Llama 3.2 3B",
            "Q4_K_M · 3.2B params",
            "bartowski/Llama-3.2-3B-Instruct-GGUF",
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            1_900_000_000L,
            Speed.FAST,
            5,
            "Meta's latest small model. Top-tier quality for its size. Best reasoning of the bunch."
        )
    };
}
