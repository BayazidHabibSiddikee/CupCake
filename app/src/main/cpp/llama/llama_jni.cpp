#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <thread>
#include <atomic>
#include <chrono>
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
// Tokens whose KV cells are currently cached as the reusable prefix (the last prompt).
static std::vector<llama_token> g_cache_tokens;
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

// Optional GBNF grammar for constrained decoding (empty = unconstrained).
// Set from Kotlin via LlamaEngine.setGrammar; copied into the sampler chain per generate().
static std::string g_grammar;
static const char* g_grammar_root = "root";

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
    g_cache_tokens.clear();
    g_grammar.clear();
    g_n_batch = n_batch > 0 ? n_batch : 512;
    
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
    // GetObjectClass hands back a local ref; without this it accumulates on the
    // calling thread until that thread detaches.
    env->DeleteLocalRef(cls);
    // A failed lookup makes every emit_* a silent no-op (the guards below bail out
    // on a null method id), which looks exactly like "the engine never finished".
    // Log it so a lost callback is distinguishable from a wedged decode.
    if (!g_on_token) LOGE("setCallback: onToken(String) not found on callback class");
    if (!g_on_complete) LOGE("setCallback: onComplete(int) not found on callback class");
    if (!g_on_error) LOGE("setCallback: onError(String) not found on callback class");
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

// Constrain decoding to a GBNF grammar. Pass null/empty to clear (unconstrained).
extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_nativeSetGrammar(
    JNIEnv* env, jobject thiz, jstring grammar
) {
    if (grammar == nullptr) {
        g_grammar.clear();
        return;
    }
    const char* str = env->GetStringUTFChars(grammar, nullptr);
    g_grammar = str != nullptr ? str : "";
    env->ReleaseStringUTFChars(grammar, str);
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
    if (!g_callback || !g_on_complete) {
        LOGE("emit_complete(%d) dropped: %s", status,
             !g_callback ? "no callback installed" : "onComplete method id is null");
        return;
    }
    
    env->CallVoidMethod(g_callback, g_on_complete, status);
}

static void emit_error(JNIEnv* env, const std::string& error) {
    if (!g_callback || !g_on_error) {
        LOGE("emit_error('%s') dropped: %s", error.c_str(),
             !g_callback ? "no callback installed" : "onError method id is null");
        return;
    }
    jstring jerror = env->NewStringUTF(error.c_str());
    env->CallVoidMethod(g_callback, g_on_error, jerror);
    env->DeleteLocalRef(jerror);
}

static int common_prefix_len(const std::vector<llama_token>& a, const std::vector<llama_token>& b) {
    const int n = (int) std::min(a.size(), b.size());
    int i = 0;
    while (i < n && a[i] == b[i]) i++;
    return i;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cupcake_ai_LlamaEngine_generate(
    JNIEnv* env, jobject thiz, jstring prompt
) {
    if (!g_ctx || !g_model) {
        // Previously a silent `-1`: Kotlin only saw a non-zero return and had no
        // reason string. Report it through the callback so the UI can say why.
        LOGE("generate: model not loaded");
        emit_error(env, "Model is not loaded");
        return -1;
    }

    // Claim the single generation slot. A previous worker (often one we just
    // cancelled) may still be unwinding on its thread; instead of failing with -2
    // we wait briefly for it to finish, then atomically take the slot. This is what
    // stops two workers from ever decoding on the same context.
    bool claimed = false;
    for (int i = 0; i < 300; ++i) {   // up to ~3s
        if (!g_is_generating.exchange(true)) {
            claimed = true;
            break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    if (!claimed) {
        // Do NOT emit_error here: Kotlin treats -2 as a recoverable "busy" signal
        // and retries on the return value. An onError would close the caller's
        // stream and turn the retry that eventually succeeds into a failed one.
        LOGE("generate: previous generation still running after wait; refusing (-2, caller may retry)");
        return -2;
    }

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
        // No retry path exists for -3 in Kotlin, so reporting the reason here is safe
        // and replaces the opaque "Generate failed: -3" with something actionable.
        LOGE("generate: tokenization failed or prompt empty");
        emit_error(env, "Prompt could not be tokenized (empty prompt or context too small)");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        g_is_generating = false;
        return -3;
    }
    tokens.resize(n_tokens);
    g_tokens = tokens;
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    // Run generation in background thread
    std::thread([=]() {
        JNIEnv* env = nullptr;
        // Attaching can fail transiently under memory pressure. Without an env we
        // cannot call back into Kotlin at all, so retry briefly; if it still fails
        // the caller is left waiting on its watchdog, and this log is the only
        // explanation available.
        for (int attempt = 0; attempt < 3; ++attempt) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK && env != nullptr) {
                break;
            }
            env = nullptr;
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        if (env == nullptr) {
            LOGE("generate: worker could not attach to the JVM; no completion will be emitted");
            g_is_generating = false;
            return;
        }

        // Build sampler chain (llama.cpp b4122 API): grammar -> penalties -> top_k -> top_p -> temp -> dist
        llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        // Grammar must come first so its logit mask is applied before any truncation
        // (top_k/top_p) can hide an in-grammar token.
        if (!g_grammar.empty()) {
            llama_sampler* grammar_smpl =
                llama_sampler_init_grammar(g_model, g_grammar.c_str(), g_grammar_root);
            if (grammar_smpl) {
                llama_sampler_chain_add(smpl, grammar_smpl);
            } else {
                LOGE("Failed to initialize grammar; generating unconstrained");
            }
        }
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
        
        const int max_tokens = g_sampling_params.max_tokens > 0 ? g_sampling_params.max_tokens : 128;
        int generated = 0;

        // ---- KV prefix cache: reuse the longest shared prefix with the previous prompt ----
        // The chat prompt is stable at the front (system + older turns) and only grows at
        // the tail, so we can skip re-decoding that whole shared prefix every turn.
        int n_match = common_prefix_len(g_cache_tokens, g_tokens);
        // Always decode at least the final token so fresh logits are produced.
        if (n_match >= (int) g_tokens.size()) {
            n_match = (int) g_tokens.size() - 1;
        }
        if (n_match > 0) {
            if (!llama_kv_cache_seq_rm(g_ctx, 0, n_match, -1)) {
                llama_kv_cache_clear(g_ctx);
                n_match = 0;
            }
        } else {
            llama_kv_cache_clear(g_ctx);
            n_match = 0;
        }

        // Decode only the new suffix; positions continue automatically from the cache.
        bool decode_err = false;
        for (size_t i = (size_t) n_match; i < g_tokens.size(); i += g_n_batch) {
            int32_t chunk = std::min((int32_t)(g_tokens.size() - i), (int32_t)g_n_batch);
            if (llama_decode(g_ctx, llama_batch_get_one(g_tokens.data() + i, chunk))) {
                decode_err = true;
                break;
            }
        }
        if (decode_err) {
            g_cache_tokens.clear();
            emit_error(env, "Failed to process prompt");
            g_is_generating = false;
            llama_sampler_free(smpl);
            g_jvm->DetachCurrentThread();
            return;
        }
        // Remember this prompt as the reusable prefix for the next turn.
        g_cache_tokens = g_tokens;
        
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
        // Emit completion BEFORE releasing the slot: callers wait for the slot to
        // free (g_is_generating) and only then install their own callback — if the
        // completion fired after the release it could land in the NEXT request's
        // callback and falsely complete it before its generation even starts.
        emit_complete(env, 0);
        g_is_generating = false;
        g_jvm->DetachCurrentThread();
    }).detach();
    
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_cancel(JNIEnv* env, jobject thiz) {
    // Only ask the worker to stop. The worker thread owns g_is_generating and clears
    // it when it actually exits — clearing it here let a fresh generate() spawn a
    // second worker on the same llama_context while the first was still decoding,
    // which corrupted state and surfaced to the user as "Generate failed: -2".
    g_should_stop = true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cupcake_ai_LlamaEngine_isGenerating(JNIEnv* env, jobject thiz) {
    return g_is_generating.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_release(JNIEnv* env, jobject thiz) {
    g_cache_tokens.clear();
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