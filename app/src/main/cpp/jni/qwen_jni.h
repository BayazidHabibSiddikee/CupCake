#ifndef QWEN_JNI_H
#define QWEN_JNI_H

#include <jni.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Initialize the Qwen model from assets
// Returns 0 on success, negative on error
JNIEXPORT jint JNICALL
Java_com_cupcake_jni_QwenNative_initModel(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath,
    jstring tokenizerPath,
    jint nThreads,
    jint nCtx
);

// Generate text stream from prompt
// Callback: onToken(jstring token), onComplete(jint status)
JNIEXPORT jint JNICALL
Java_com_cupcake_jni_QwenNative_generateStream(
    JNIEnv* env,
    jobject thiz,
    jstring prompt,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jint maxTokens,
    jobject callback
);

// Release model resources
JNIEXPORT void JNICALL
Java_com_cupcake_jni_QwenNative_releaseModel(
    JNIEnv* env,
    jobject thiz
);

// Get model info
JNIEXPORT jstring JNICALL
Java_com_cupcake_jni_QwenNative_getModelInfo(
    JNIEnv* env,
    jobject thiz
);

// Check if model is loaded
JNIEXPORT jboolean JNICALL
Java_com_cupcake_jni_QwenNative_isModelLoaded(
    JNIEnv* env,
    jobject thiz
);

#ifdef __cplusplus
}
#endif

#endif // QWEN_JNI_H