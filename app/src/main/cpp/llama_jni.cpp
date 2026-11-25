#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <memory>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static int g_nctx = 4096;
static std::atomic<bool> g_stop{false};
static bool g_backend = false;

static void unload_all() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

// Length of the prefix of s that contains only complete UTF-8 characters.
static size_t utf8_complete_len(const std::string &s) {
    size_t n = s.size(), i = n;
    int back = 0;
    while (i > 0 && back < 4) {
        i--; back++;
        unsigned char c = (unsigned char) s[i];
        if ((c & 0xC0) != 0x80) {
            int need = c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : c >= 0xC0 ? 2 : 1;
            return need > back ? i : n;
        }
    }
    return n;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlineai_app_LlamaBridge_nativeLoad(JNIEnv *env, jobject, jstring jpath, jint nctx, jint nthreads) {
    if (!g_backend) { llama_backend_init(); g_backend = true; }
    unload_all();
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.use_mmap = true;
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) return JNI_FALSE;

    g_nctx = nctx;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nctx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = nthreads;
    cp.n_threads_batch = nthreads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { unload_all(); return JNI_FALSE; }
    LOGI("model loaded, ctx=%d threads=%d", nctx, nthreads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlineai_app_LlamaBridge_nativeGenerate(JNIEnv *env, jobject, jbyteArray jprompt, jint maxNew, jobject cb) {
    if (!g_model || !g_ctx) return env->NewStringUTF("Model not loaded");
    g_stop = false;

    jclass cls = env->GetObjectClass(cb);
    jmethodID mid = env->GetMethodID(cls, "onToken", "([B)V");
    if (!mid) return env->NewStringUTF("callback not found");

    jsize plen = env->GetArrayLength(jprompt);
    std::string prompt((size_t) plen, '\0');
    env->GetByteArrayRegion(jprompt, 0, plen, reinterpret_cast<jbyte *>(&prompt[0]));

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    int n = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (n <= 0) return env->NewStringUTF("tokenize failed");
    std::vector<llama_token> toks((size_t) n);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), n, true, true) < 0)
        return env->NewStringUTF("tokenize failed");

    int budget = g_nctx - n - 1;
    if (budget < 16) return env->NewStringUTF("Prompt too long for context. Clear chat or shorten input.");
    if (maxNew > budget) maxNew = budget;

    llama_memory_clear(llama_get_memory(g_ctx), true);

    for (int i = 0; i < n; i += 512) {
        if (g_stop) return env->NewStringUTF("");
        int c = n - i < 512 ? n - i : 512;
        llama_batch b = llama_batch_get_one(toks.data() + i, c);
        if (llama_decode(g_ctx, b) != 0) return env->NewStringUTF("decode failed (prefill)");
    }

    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> smpl(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    llama_sampler_chain_add(smpl.get(), llama_sampler_init_top_k(20));
    llama_sampler_chain_add(smpl.get(), llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(smpl.get(), llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string pending;
    auto emit = [&](const std::string &s) {
        if (s.empty()) return;
        jbyteArray arr = env->NewByteArray((jsize) s.size());
        env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
        env->CallVoidMethod(cb, mid, arr);
        env->DeleteLocalRef(arr);
        if (env->ExceptionCheck()) env->ExceptionClear();
    };

    for (int i = 0; i < maxNew && !g_stop; i++) {
        llama_token tok = llama_sampler_sample(smpl.get(), g_ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) pending.append(buf, (size_t) len);
        size_t ok = utf8_complete_len(pending);
        if (ok > 0) { emit(pending.substr(0, ok)); pending.erase(0, ok); }
        llama_batch nb = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, nb) != 0) return env->NewStringUTF("decode failed");
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineai_app_LlamaBridge_nativeStop(JNIEnv *, jobject) { g_stop = true; }

extern "C" JNIEXPORT void JNICALL
Java_com_offlineai_app_LlamaBridge_nativeUnload(JNIEnv *, jobject) { unload_all(); }
