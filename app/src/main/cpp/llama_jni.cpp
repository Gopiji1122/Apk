#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "AuraAI-JNI"
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

    std::string format_messages(
        const llama_model * model,
        const std::vector<std::string> & roles,
        const std::vector<std::string> & contents
    ) {
        std::vector<llama_chat_message> msgs;
        msgs.reserve(roles.size());
        for (size_t i = 0; i < roles.size(); ++i) {
            msgs.push_back({roles[i].c_str(), contents[i].c_str()});
        }

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

        std::string out;
        for (size_t i = 0; i < roles.size(); ++i) {
            out += "<|im_start|>" + roles[i] + "\n" + contents[i] + "<|im_end|>\n";
        }
        out += "<|im_start|>assistant\n";
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
Java_com_gopiji_auraai_engine_LlamaEngine_nativeLoadModel(
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

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load GGUF model.");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx >= 512 ? nCtx : 2048);
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
Java_com_gopiji_auraai_engine_LlamaEngine_nativeStop(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_stop_flag.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gopiji_auraai_engine_LlamaEngine_nativeUnload(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    g_stop_flag.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    free_internal();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gopiji_auraai_engine_LlamaEngine_nativeGenerateChat(
    JNIEnv * env,
    jobject /* thiz */,
    jobjectArray jRoles,
    jobjectArray jContents,
    jint maxTokens,
    jfloat temperature,
    jint topK,
    jfloat topP,
    jobject callback
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_stop_flag.store(false);

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: No model loaded.");
    }

    jsize count = env->GetArrayLength(jRoles);
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    roles.reserve(count);
    contents.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        jstring rObj = static_cast<jstring>(env->GetObjectArrayElement(jRoles, i));
        jstring cObj = static_cast<jstring>(env->GetObjectArrayElement(jContents, i));
        const char * rStr = env->GetStringUTFChars(rObj, nullptr);
        const char * cStr = env->GetStringUTFChars(cObj, nullptr);
        roles.emplace_back(rStr ? rStr : "user");
        contents.emplace_back(cStr ? cStr : "");
        if (rStr) env->ReleaseStringUTFChars(rObj, rStr);
        if (cStr) env->ReleaseStringUTFChars(cObj, cStr);
        env->DeleteLocalRef(rObj);
        env->DeleteLocalRef(cObj);
    }

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    llama_memory_clear(llama_get_memory(g_ctx), true);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));
    const int max_prompt_tokens = std::max(256, n_ctx - 256);

    std::vector<llama_token> tokens;
    while (true) {
        std::string formatted = format_messages(g_model, roles, contents);
        tokens.resize(formatted.size() + 32);
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
        if (n_tokens <= max_prompt_tokens || roles.size() <= 2) {
            if (n_tokens > max_prompt_tokens) n_tokens = max_prompt_tokens;
            tokens.resize(n_tokens);
            break;
        }
        roles.erase(roles.begin() + 1);
        contents.erase(contents.begin() + 1);
    }

    const int n_tokens = static_cast<int>(tokens.size());
    const int n_batch = 256;
    for (int i = 0; i < n_tokens; i += n_batch) {
        if (g_stop_flag.load()) {
            return env->NewStringUTF("");
        }
        int n_eval = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_get_one(&tokens[i], n_eval);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed during prefill.");
            return env->NewStringUTF("Error: Context decode failed. Try reducing Context Window in Settings.");
        }
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK > 0 ? topK : 40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP > 0.05f ? topP : 0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature >= 0.0f ? temperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string full_response;
    std::string utf8_buffer;
    const int limit = maxTokens > 0 ? maxTokens : 512;

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
