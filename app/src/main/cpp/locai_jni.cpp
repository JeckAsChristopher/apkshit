#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>

#include "llama.cpp/include/llama.h"
#include "llama.cpp/ggml/include/ggml.h"

#define LOG(...) fprintf(stderr, "LOCAI: " __VA_ARGS__)

static llama_model*   g_model   = nullptr;
static llama_context* g_ctx     = nullptr;
static llama_sampler* g_sampler = nullptr;
static JavaVM*        g_jvm     = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    llama_backend_init();
    LOG("llama backend ready\n");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    llama_backend_free();
}

static std::vector<llama_token> tokenize(const std::string& text, bool add_bos) {
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n = -llama_tokenize(vocab, text.c_str(), (int)text.size(), nullptr, 0, add_bos, true);
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, text.c_str(), (int)text.size(), tokens.data(), n, add_bos, true);
    return tokens;
}

static std::string token_to_piece(llama_token token) {
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    char buf[256] = {};
    int  n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    return n > 0 ? std::string(buf, n) : "";
}

// ─── Free and recreate context — works across ALL llama.cpp versions ──────────
static bool reset_context(int n_ctx) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx     = (uint32_t)n_ctx;
    cp.n_threads = 4;
    g_ctx = llama_init_from_model(g_model, cp);
    return g_ctx != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_locai_app_LlamaJNI_load(JNIEnv* env, jclass,
                                  jstring jpath, jint n_ctx) {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);              g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);      g_model   = nullptr; }

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    LOG("Loading: %s\n", path);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) { LOG("Model load failed\n"); return JNI_FALSE; }

    if (!reset_context(n_ctx)) {
        llama_model_free(g_model); g_model = nullptr;
        LOG("Context create failed\n"); return JNI_FALSE;
    }

    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOG("Model loaded OK\n");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_locai_app_LlamaJNI_generate(JNIEnv* env, jclass,
                                      jstring jprompt, jint max_tokens,
                                      jobject callback) {
    if (!g_model || !g_ctx || !g_sampler) return JNI_FALSE;

    jclass    cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    const char* ps = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(ps);
    env->ReleaseStringUTFChars(jprompt, ps);

    // Reset context by recreating it — no KV-cache API needed, works on all versions
    reset_context(llama_n_ctx(g_ctx));
    llama_sampler_reset(g_sampler);

    std::vector<llama_token> tokens = tokenize(prompt, true);
    if (tokens.empty()) return JNI_FALSE;

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) return JNI_FALSE;

    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    for (int i = 0; i < max_tokens; i++) {
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        std::string piece = token_to_piece(tok);
        if (!piece.empty()) {
            jstring jp = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, jp);
            env->DeleteLocalRef(jp);
        }

        batch = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_locai_app_LlamaJNI_unload(JNIEnv*, jclass) {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);              g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);      g_model   = nullptr; }
    LOG("Unloaded\n");
}
