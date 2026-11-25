#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "OfflineAI-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    std::mutex g_mutex;
    llama_model * g_model = nullptr;
    llama_context * g_ctx = nullptr;
    std::atomic<bool> g_stop_flag(false);
    bool g_backend_initialized = false;

    bool is_valid_utf8(const std::string & s) {
        size_t i = 0;
        const size_t n = s.size();
        while (i < n) {
            unsigned char c = static_cast<unsigned char>(s[i]);
            if (c <= 0x7F) {
                i += 1;
            } else if ((c & 0xE0) == 0xC0) {
                if (i + 1 >= n || (static_cast<unsigned char>(s[i + 1]) & 0xC0) != 0x80) return false;
                i += 2;
            } else if ((c & 0xF0) == 0xE0) {
                if (i + 2 >= n ||
                    (static_cast<unsigned char>(s[i + 1]) & 0xC0) != 0x80 ||
                    (static_cast<unsigned char>(s[i + 2]) & 0xC0) != 0x80) return false;
                i += 3;
            } else if ((c & 0xF8) == 0xF0) {
                if (i + 3 >= n ||
                    (static_cast<unsigned char>(s[i + 1]) & 0xC0) != 0x80 ||
                    (static_cast<unsigned char>(s[i + 2]) & 0xC0) != 0x80 ||
                    (static_cast<unsigned char>(s[i + 3]) & 0xC0) != 0x80) return false;
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }

    std::string format_prompt(
        const llama_model * model,
        const std::string & system_prompt,
        const std::string & user_prompt
    ) {
        std::vector<llama_chat_message> msgs;
        if (!system_prompt.empty()) {
            msgs.push_back({"system", system_prompt.c_str()});
        }
        msgs.push_back({"user", user_prompt.c_str()});

        const char * tmpl = llama_model_chat_template(model, nullptr);
        if (tmpl != nullptr) {
            int req = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, nullptr, 0);
            if (req > 0) {
                std::vector<char> buf(req + 1, 0);
                int written = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), buf.size());
                if (written > 0) {
                    return std::string(buf.data(), written);
                }
            }
        }

        // Fallback to ChatML (native for Qwen 2.5 / Qwen 3 / Qwen 3.5)
        std::string out;
        if (!system_prompt.empty()) {
            out += "<|im_start|>system\n" + system_prompt + "<|im_end|>\n";
        }
        out += "<|im_start|>user\n" + user_prompt + "<|im_end|>\n<|im_start|>assistant\n";
        return out;
    }

    void free_internal() {
        if (g_ctx) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlineai_assistant_engine_LlamaEngine_nativeLoadModel(
    JNIEnv * env,
    jobject /* thiz */,
    jstring modelPath,
    jint nCtx,
    jint nThreads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    free_internal();

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    LOGI("Loading GGUF model: %s (ctx=%d, threads=%d)", path, nCtx, nThreads);

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load GGUF model.");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx > 512 ? nCtx : 2048);
    cparams.n_batch = 512;
    cparams.n_threads = nThreads > 0 ? nThreads : 4;
    cparams.n_threads_batch = cparams.n_threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to initialize llama_context.");
        free_internal();
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineai_assistant_engine_LlamaEngine_nativeStop(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_stop_flag.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineai_assistant_engine_LlamaEngine_nativeUnload(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_stop_flag.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    free_internal();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlineai_assistant_engine_LlamaEngine_nativeGenerate(
    JNIEnv * env,
    jobject /* thiz */,
    jstring systemPrompt,
    jstring userPrompt,
    jint maxTokens,
    jfloat temperature,
    jobject callback
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_stop_flag.store(false);

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: No model loaded.");
    }

    const char * sys_c = env->GetStringUTFChars(systemPrompt, nullptr);
    const char * usr_c = env->GetStringUTFChars(userPrompt, nullptr);
    std::string sys_str(sys_c ? sys_c : "");
    std::string usr_str(usr_c ? usr_c : "");
    if (sys_c) env->ReleaseStringUTFChars(systemPrompt, sys_c);
    if (usr_c) env->ReleaseStringUTFChars(userPrompt, usr_c);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    // Clear KV cache for clean per-turn RAG + history context
    llama_memory_clear(llama_get_memory(g_ctx), true);

    std::string formatted = format_prompt(g_model, sys_str, usr_str);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    int max_prompt_tokens = static_cast<int>(llama_n_ctx(g_ctx)) - 128;
    if (max_prompt_tokens < 256) max_prompt_tokens = 256;

    std::vector<llama_token> tokens(formatted.size() + 32);
    int n_tokens = llama_tokenize(
        vocab,
        formatted.c_str(),
        static_cast<int32_t>(formatted.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            vocab,
            formatted.c_str(),
            static_cast<int32_t>(formatted.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
    }
    if (n_tokens <= 0) {
        return env->NewStringUTF("Error: Tokenization failed.");
    }
    if (n_tokens > max_prompt_tokens) {
        n_tokens = max_prompt_tokens;
    }
    tokens.resize(n_tokens);

    // Prefill prompt in safe batch chunks
    const int n_batch = 256;
    for (int i = 0; i < n_tokens; i += n_batch) {
        if (g_stop_flag.load()) {
            return env->NewStringUTF("");
        }
        int n_eval = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_get_one(&tokens[i], n_eval);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed during prefill.");
            return env->NewStringUTF("Error: Context decode failed (try shorter prompt or smaller model).");
        }
    }

    // Build sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature > 0.0f ? temperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string full_response;
    std::string utf8_buffer;
    const int limit = maxTokens > 0 ? maxTokens : 512;
    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));

    for (int i = 0; i < limit; ++i) {
        if (g_stop_flag.load()) break;
        if (n_tokens + i >= n_ctx - 4) break;

        llama_token next_token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, next_token)) {
            break;
        }

        char piece_buf[256];
        int piece_len = llama_token_to_piece(vocab, next_token, piece_buf, sizeof(piece_buf), 0, false);
        if (piece_len > 0) {
            utf8_buffer.append(piece_buf, piece_len);
            if (is_valid_utf8(utf8_buffer)) {
                full_response += utf8_buffer;
                if (onTokenMethod) {
                    jstring jPiece = env->NewStringUTF(utf8_buffer.c_str());
                    if (jPiece) {
                        env->CallVoidMethod(callback, onTokenMethod, jPiece);
                        env->DeleteLocalRef(jPiece);
                    }
                }
                utf8_buffer.clear();
            }
        }

        llama_batch next_batch = llama_batch_get_one(&next_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            LOGE("llama_decode failed during generation.");
            break;
        }
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(full_response.c_str());
}
