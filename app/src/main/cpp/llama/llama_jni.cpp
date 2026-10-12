#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <thread>
#include <atomic>
#include <cstring>
#include <ctime>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global llama context
static llama_context* g_ctx = nullptr;
static llama_model* g_model = nullptr;
static std::vector<llama_token> g_tokens;
static std::atomic<bool> g_is_generating{false};
static std::atomic<bool> g_should_stop{false};
static int g_n_batch = 512;

// Sampling parameters
struct SamplingParams {
    float temp = 0.7f;
    float top_p = 0.9f;
    int top_k = 40;
    float repeat_penalty = 1.1f;
    int repeat_last_n = 64;
    int seed = -1;
    int max_tokens = 128;
};

static SamplingParams g_sampling_params;

// Callback for streaming tokens
static JavaVM* g_jvm = nullptr;
static jobject g_callback = nullptr;
static jmethodID g_on_token = nullptr;
static jmethodID g_on_complete = nullptr;
static jmethodID g_on_error = nullptr;

extern "C" JNIEXPORT jint JNICALL
Java_com_cupcake_ai_LlamaEngine_initModel(
    JNIEnv* env, jobject thiz,
    jstring model_path, jint n_ctx, jint n_threads, jint n_batch
) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    
    llama_backend_init();
    
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only for now
    model_params.use_mmap = true;
    model_params.use_mlock = false;
    
    g_model = llama_load_model_from_file(path, model_params);
    if (!g_model) {
        LOGE("Failed to load model: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return -1;
    }
    
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = n_batch;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return -2;
    }
    
    LOGD("Model loaded successfully: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_setCallback(
    JNIEnv* env, jobject thiz, jobject callback
) {
    env->GetJavaVM(&g_jvm);
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
    }
    g_callback = env->NewGlobalRef(callback);
    jclass cls = env->GetObjectClass(g_callback);
    g_on_token = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    g_on_complete = env->GetMethodID(cls, "onComplete", "(I)V");
    g_on_error = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_setSamplingParams(
    JNIEnv* env, jobject thiz,
    jfloat temp, jfloat top_p, jint top_k,
    jfloat repeat_penalty, jint repeat_last_n, jint seed, jint max_tokens
) {
    g_sampling_params.temp = temp;
    g_sampling_params.top_p = top_p;
    g_sampling_params.top_k = top_k;
    g_sampling_params.repeat_penalty = repeat_penalty;
    g_sampling_params.repeat_last_n = repeat_last_n;
    g_sampling_params.seed = seed;
    g_sampling_params.max_tokens = max_tokens > 0 ? max_tokens : 128;
}

static bool is_valid_utf8(const std::string& str) {
    int expected = 0;
    for (unsigned char c : str) {
        if (expected == 0) {
            if ((c & 0x80) == 0) continue; // 1-byte ASCII
            else if ((c & 0xE0) == 0xC0) expected = 1; // 2-byte
            else if ((c & 0xF0) == 0xE0) expected = 2; // 3-byte
            else if ((c & 0xF8) == 0xF0) expected = 3; // 4-byte
            else return false;
        } else {
            if ((c & 0xC0) != 0x80) return false;
            expected--;
        }
    }
    return expected == 0;
}

static void emit_token(JNIEnv* env, const std::string& token) {
    if (!g_callback || !g_on_token || token.empty()) return;
    
    jbyteArray bytes = env->NewByteArray(token.length());
    if (!bytes) return;
    env->SetByteArrayRegion(bytes, 0, token.length(), (const jbyte*)token.data());
    
    jclass strClass = env->FindClass("java/lang/String");
    jmethodID strCtor = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jstring utf8Charset = env->NewStringUTF("UTF-8");
    jstring jtoken = (jstring)env->NewObject(strClass, strCtor, bytes, utf8Charset);
    
    if (jtoken) {
        env->CallVoidMethod(g_callback, g_on_token, jtoken);
        env->DeleteLocalRef(jtoken);
    }
    env->DeleteLocalRef(utf8Charset);
    env->DeleteLocalRef(strClass);
    env->DeleteLocalRef(bytes);
}

static void emit_complete(JNIEnv* env, int status) {
    if (!g_callback || !g_on_complete) return;
    
    env->CallVoidMethod(g_callback, g_on_complete, status);
}

static void emit_error(JNIEnv* env, const std::string& error) {
    if (!g_callback || !g_on_error) return;
    
    jstring jerror = env->NewStringUTF(error.c_str());
    env->CallVoidMethod(g_callback, g_on_error, jerror);
    env->DeleteLocalRef(jerror);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cupcake_ai_LlamaEngine_generate(
    JNIEnv* env, jobject thiz, jstring prompt
) {
    if (!g_ctx || !g_model) {
        return -1;
    }
    
    if (g_is_generating) {
        return -2;
    }
    
    g_is_generating = true;
    g_should_stop = false;

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Tokenize prompt with parse_special = true so <|im_start|>, <|im_end|>, etc. are parsed as control tokens
    const int32_t n_ctx = llama_n_ctx(g_ctx);
    std::vector<llama_token> tokens(n_ctx);
    int n_tokens = llama_tokenize(g_model, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        int needed = -n_tokens;
        tokens.resize(needed);
        n_tokens = llama_tokenize(g_model, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, true);
        if (n_tokens > n_ctx) {
            // Keep the most recent tokens to avoid exceeding context
            int max_keep = n_ctx - 140;
            if (max_keep > 0 && n_tokens > max_keep) {
                tokens.erase(tokens.begin(), tokens.begin() + (n_tokens - max_keep));
                n_tokens = (int)tokens.size();
            }
        }
    }
    if (n_tokens <= 0) {
        LOGE("Tokenization failed or prompt empty");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        g_is_generating = false;
        return -3;
    }
    tokens.resize(n_tokens);
    g_tokens = tokens;
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    // Run generation in background thread
    std::thread([=]() {
        JNIEnv* env;
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            g_is_generating = false;
            return;
        }

        // Build sampler chain (llama.cpp b4122 API): penalties -> top_k -> top_p -> temp -> dist
        llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(smpl,
            llama_sampler_init_penalties(
                llama_n_vocab(g_model),
                llama_token_eos(g_model),
                -1, // linefeed_id (unused, penalize_nl=false)
                g_sampling_params.repeat_last_n,
                g_sampling_params.repeat_penalty > 1.0f ? g_sampling_params.repeat_penalty : 1.15f,
                0.25f, // penalty_freq (prevents repetitive echo loops)
                0.10f, // penalty_present
                false, // penalize_nl
                false  // ignore_eos
            ));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(g_sampling_params.top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(g_sampling_params.top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(g_sampling_params.temp));
        const uint32_t seed = g_sampling_params.seed < 0
            ? (uint32_t) time(nullptr)
            : (uint32_t) g_sampling_params.seed;
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));
        
        // Clear previous KV cache so positions don't conflict or overflow
        llama_kv_cache_clear(g_ctx);

        const int max_tokens = g_sampling_params.max_tokens > 0 ? g_sampling_params.max_tokens : 128;
        int generated = 0;
        
        // Process prompt in chunks of g_n_batch
        bool decode_err = false;
        for (size_t i = 0; i < g_tokens.size(); i += g_n_batch) {
            int32_t chunk = std::min((int32_t)(g_tokens.size() - i), (int32_t)g_n_batch);
            if (llama_decode(g_ctx, llama_batch_get_one(g_tokens.data() + i, chunk))) {
                decode_err = true;
                break;
            }
        }
        if (decode_err) {
            emit_error(env, "Failed to process prompt");
            g_is_generating = false;
            llama_sampler_free(smpl);
            g_jvm->DetachCurrentThread();
            return;
        }
        
        // Generate tokens with UTF-8 buffering and accumulated stop checks
        std::string utf8_buffer;
        std::string accumulated;
        while (generated < max_tokens && !g_should_stop.load()) {
            llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
            
            if (llama_token_is_eog(g_model, id)) {
                break;
            }
            
            // Convert token to piece
            char buf[256];
            int len = llama_token_to_piece(g_model, id, buf, sizeof(buf), 0, true);
            if (len > 0) {
                std::string token_str(buf, len);
                accumulated.append(token_str);
                
                // Stop on ChatML or turn boundaries
                if (accumulated.find("<|im_end|>") != std::string::npos ||
                    accumulated.find("<|endoftext|>") != std::string::npos ||
                    accumulated.find("<|im_start|>") != std::string::npos ||
                    accumulated.find("\nuser\n") != std::string::npos ||
                    accumulated.find("\nUser:") != std::string::npos ||
                    accumulated.find("\nuser:") != std::string::npos ||
                    accumulated.find("\nUser\n") != std::string::npos) {
                    break;
                }
                
                utf8_buffer.append(token_str);
                if (is_valid_utf8(utf8_buffer)) {
                    emit_token(env, utf8_buffer);
                    utf8_buffer.clear();
                }
            }
            
            // Add to context
            g_tokens.push_back(id);
            
            // Decode
            if (llama_decode(g_ctx, llama_batch_get_one(&id, 1))) {
                emit_error(env, "Decode failed");
                break;
            }
            
            generated++;
            
            // Accept token for next iteration
            llama_sampler_accept(smpl, id);
        }
        
        // Flush any remaining buffered bytes if valid
        if (!utf8_buffer.empty()) {
            emit_token(env, utf8_buffer);
            utf8_buffer.clear();
        }
        
        llama_sampler_free(smpl);
        g_is_generating = false;
        emit_complete(env, 0);
        g_jvm->DetachCurrentThread();
    }).detach();
    
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_cancel(JNIEnv* env, jobject thiz) {
    g_should_stop = true;
    g_is_generating = false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_release(JNIEnv* env, jobject thiz) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
    }
    llama_backend_free();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cupcake_ai_LlamaEngine_isLoaded(JNIEnv* env, jobject thiz) {
    return g_ctx != nullptr && g_model != nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cupcake_ai_LlamaEngine_getModelInfo(JNIEnv* env, jobject thiz) {
    if (!g_model) return env->NewStringUTF("Not loaded");
    
    std::string info = "Model: Qwen2.5-0.5B\n";
    info += "Params: " + std::to_string(llama_model_n_params(g_model)) + "\n";
    info += "Vocab: " + std::to_string(llama_n_vocab(g_model)) + "\n";
    info += "Ctx: " + std::to_string(llama_n_ctx(g_ctx)) + "\n";
    
    return env->NewStringUTF(info.c_str());
}