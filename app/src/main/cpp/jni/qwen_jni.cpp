#include "qwen_jni.h"
#include "jni_common.h"
#include "qwen/inference.h"

#include <string>
#include <memory>

namespace cupcake {
namespace qwen {

// Global engine instance (single model for now)
static std::unique_ptr<InferenceEngine> g_engine = nullptr;
static JavaVM* g_jvm = nullptr;

// Callback interface for streaming tokens
class JniCallback {
public:
    JniCallback(JNIEnv* env, jobject callback) : callback_(env->NewGlobalRef(callback)) {
        env->GetJavaVM(&g_jvm);
        cls_ = (jclass)env->NewGlobalRef(env->GetObjectClass(callback_));
        onToken_ = env->GetMethodID(cls_, "onToken", "(Ljava/lang/String;)V");
        onComplete_ = env->GetMethodID(cls_, "onComplete", "(I)V");
        onError_ = env->GetMethodID(cls_, "onError", "(Ljava/lang/String;)V");
    }

    ~JniCallback() {
        JNIEnv* env;
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(callback_);
            env->DeleteGlobalRef(cls_);
        }
    }

    void onToken(const std::string& token) {
        JNIEnv* env;
        if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            jstring jtoken = env->NewStringUTF(token.c_str());
            env->CallVoidMethod(callback_, onToken_, jtoken);
            env->DeleteLocalRef(jtoken);
            g_jvm->DetachCurrentThread();
        }
    }

    void onComplete(int status) {
        JNIEnv* env;
        if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            env->CallVoidMethod(callback_, onComplete_, status);
            g_jvm->DetachCurrentThread();
        }
    }

    void onError(const std::string& error) {
        JNIEnv* env;
        if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            jstring jerror = env->NewStringUTF(error.c_str());
            env->CallVoidMethod(callback_, onError_, jerror);
            env->DeleteLocalRef(jerror);
            g_jvm->DetachCurrentThread();
        }
    }

private:
    jobject callback_;
    jclass cls_;
    jmethodID onToken_;
    jmethodID onComplete_;
    jmethodID onError_;
};

} // namespace qwen
} // namespace cupcake

// =============================================================================
// JNI EXPORTS
// =============================================================================

JNIEXPORT jint JNICALL
Java_com_cupcake_native_QwenNative_initModel(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath,
    jstring tokenizerPath,
    jint nThreads,
    jint nCtx
) {
    using namespace cupcake::qwen;

    if (g_engine) {
        LOGW("Model already initialized");
        return -1;
    }

    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char* tokenizer_path = env->GetStringUTFChars(tokenizerPath, nullptr);

    InferenceConfig config;
    config.model_path = model_path;
    config.tokenizer_path = tokenizer_path;
    config.n_threads = nThreads > 0 ? nThreads : 4;
    config.n_ctx = nCtx > 0 ? nCtx : 4096;

    g_engine = std::make_unique<InferenceEngine>();
    int result = g_engine->initialize(config);

    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(tokenizerPath, tokenizer_path);

    if (result != 0) {
        LOGE("Failed to initialize engine: %d", result);
        g_engine.reset();
        return result;
    }

    LOGI("Qwen model initialized successfully");
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_cupcake_native_QwenNative_generateStream(
    JNIEnv* env,
    jobject thiz,
    jstring prompt,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jint maxTokens,
    jobject callback
) {
    using namespace cupcake::qwen;

    if (!g_engine) {
        LOGE("Model not initialized");
        return -1;
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

    SamplingParams params;
    params.temperature = temperature;
    params.top_p = topP;
    params.top_k = topK;
    params.max_tokens = maxTokens;

    JniCallback cb(env, callback);

    int result = g_engine->generate_stream(prompt_str, params, [&cb](const std::string& token) {
        cb.onToken(token);
    });

    env->ReleaseStringUTFChars(prompt, prompt_str);

    cb.onComplete(result);
    return result;
}

JNIEXPORT void JNICALL
Java_com_cupcake_native_QwenNative_releaseModel(
    JNIEnv* env,
    jobject thiz
) {
    using namespace cupcake::qwen;

    if (g_engine) {
        g_engine->shutdown();
        g_engine.reset();
        LOGI("Qwen model released");
    }
}

JNIEXPORT jstring JNICALL
Java_com_cupcake_native_QwenNative_getModelInfo(
    JNIEnv* env,
    jobject thiz
) {
    using namespace cupcake::qwen;

    if (!g_engine) {
        return env->NewStringUTF("Model not loaded");
    }

    std::string info = g_engine->get_model_info_string();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_cupcake_native_QwenNative_isModelLoaded(
    JNIEnv* env,
    jobject thiz
) {
    using namespace cupcake::qwen;

    return g_engine != nullptr ? JNI_TRUE : JNI_FALSE;
}