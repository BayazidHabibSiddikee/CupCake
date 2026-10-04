#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global llama context
static llama_context* g_ctx = nullptr;
static llama_model* g_model = nullptr;
static std::vector<llama_token> g_tokens;
static bool g_is_generating = false;

// Sampling parameters
struct SamplingParams {
    float temp = 0.7f;
    float top_p = 0.9f;
    int top_k = 40;
    float repeat_penalty = 1.1f;
    int repeat_last_n = 64;
    int seed = -1;
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
    jfloat repeat_penalty, jint repeat_last_n, jint seed
) {
    g_sampling_params.temp = temp;
    g_sampling_params.top_p = top_p;
    g_sampling_params.top_k = top_k;
    g_sampling_params.repeat_penalty = repeat_penalty;
    g_sampling_params.repeat_last_n = repeat_last_n;
    g_sampling_params.seed = seed;
}

static void emit_token(const std::string& token) {
    if (!g_callback || !g_on_token) return;
    
    JNIEnv* env;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    
    jstring jtoken = env->NewStringUTF(token.c_str());
    env->CallVoidMethod(g_callback, g_on_token, jtoken);
    env->DeleteLocalRef(jtoken);
    g_jvm->DetachCurrentThread();
}

static void emit_complete(int status) {
    if (!g_callback || !g_on_complete) return;
    
    JNIEnv* env;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    
    env->CallVoidMethod(g_callback, g_on_complete, status);
    g_jvm->DetachCurrentThread();
}

static void emit_error(const std::string& error) {
    if (!g_callback || !g_on_error) return;
    
    JNIEnv* env;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    
    jstring jerror = env->NewStringUTF(error.c_str());
    env->CallVoidMethod(g_callback, g_on_error, jerror);
    env->DeleteLocalRef(jerror);
    g_jvm->DetachCurrentThread();
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
    
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Tokenize prompt
    std::vector<llama_token> tokens(llama_n_ctx(g_ctx));
    int n_tokens = llama_tokenize(g_model, prompt_str, strlen(prompt_str), tokens.data(), tokens.size(), true, false);
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        g_is_generating = false;
        return -3;
    }
    tokens.resize(n_tokens);
    g_tokens = tokens;
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    // Run generation in background thread
    std::thread([=]() {
        llama_sampling_params sparams = llama_sampling_default_params();
        sparams.temp = g_sampling_params.temp;
        sparams.top_p = g_sampling_params.top_p;
        sparams.top_k = g_sampling_params.top_k;
        sparams.penalty_repeat = g_sampling_params.repeat_penalty;
        sparams.penalty_last_n = g_sampling_params.repeat_last_n;
        sparams.seed = g_sampling_params.seed;
        
        llama_sampler* smpl = llama_sampler_chain_init(sparams);
        
        const int max_tokens = 2048;
        int generated = 0;
        
        // Process prompt
        if (llama_decode(g_ctx, llama_batch_get_one(g_tokens.data(), g_tokens.size()))) {
            emit_error("Failed to process prompt");
            g_is_generating = false;
            llama_sampler_free(smpl);
            return;
        }
        
        // Generate tokens
        while (generated < max_tokens && !g_jvm->GetEnv(nullptr, JNI_VERSION_1_6)) {
            llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
            
            if (llama_token_is_eog(g_model, id)) {
                break;
            }
            
            // Add to context
            g_tokens.push_back(id);
            
            // Decode
            if (llama_decode(g_ctx, llama_batch_get_one(&id, 1))) {
                emit_error("Decode failed");
                break;
            }
            
            // Convert token to string
            char buf[256];
            int len = llama_token_to_piece(g_model, id, buf, sizeof(buf), 0, true);
            if (len > 0) {
                std::string token_str(buf, len);
                emit_token(token_str);
            }
            
            generated++;
            
            // Accept token for next iteration
            llama_sampler_accept(smpl, id, true);
        }
        
        llama_sampler_free(smpl);
        g_is_generating = false;
        emit_complete(0);
    }).detach();
    
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cupcake_ai_LlamaEngine_cancel(JNIEnv* env, jobject thiz) {
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