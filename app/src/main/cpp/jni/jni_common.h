#ifndef JNI_COMMON_H
#define JNI_COMMON_H

#include <jni.h>
#include <string>
#include <cstdint>

// =============================================================================
// LOGGING
// =============================================================================
#include <android/log.h>

#define LOG_TAG "CupCake_Qwen"

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// =============================================================================
// UTILITY MACROS
// =============================================================================

#define JNI_CHECK_NULL(env, obj, ret) \
    do { \
        if (!(obj)) { \
            LOGE("Null pointer at %s:%d", __FILE__, __LINE__); \
            return (ret); \
        } \
    } while (0)

#define JNI_THROW(env, exception, msg) \
    do { \
        jclass clazz = (env)->FindClass(exception); \
        if (clazz) (env)->ThrowNew(clazz, msg); \
    } while (0)

#define JNI_THROW_IF(env, condition, exception, msg) \
    do { \
        if (condition) { \
            JNI_THROW(env, exception, msg); \
            return; \
        } \
    } while (0)

#define JNI_THROW_IF_RET(env, condition, exception, msg, ret) \
    do { \
        if (condition) { \
            JNI_THROW(env, exception, msg); \
            return (ret); \
        } \
    } while (0)

// =============================================================================
// STRING CONVERSION
// =============================================================================

inline std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

inline jstring string_to_jstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

// =============================================================================
// EXCEPTION HANDLING
// =============================================================================

inline bool jni_check_exception(JNIEnv* env, const char* file, int line) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("JNI exception at %s:%d", file, line);
        return true;
    }
    return false;
}

#define JNI_CHECK_EXCEPTION(env) jni_check_exception(env, __FILE__, __LINE__)

// =============================================================================
// SCOPED PRIMITIVE ARRAYS
// =============================================================================

template <typename T>
class ScopedPrimitiveArray {
public:
    ScopedPrimitiveArray(JNIEnv* env, jarray array, jboolean* isCopy = nullptr)
        : env_(env), array_(array), elements_(nullptr) {
        if (array_) {
            elements_ = get_elements(env_, array_, isCopy);
        }
    }

    ~ScopedPrimitiveArray() {
        if (array_ && elements_) {
            release_elements(env_, array_, elements_, 0);
        }
    }

    T* get() const { return elements_; }
    T& operator[](size_t i) const { return elements_[i]; }
    size_t size() const { return array_ ? env_->GetArrayLength(array_) : 0; }

    // Non-copyable
    ScopedPrimitiveArray(const ScopedPrimitiveArray&) = delete;
    ScopedPrimitiveArray& operator=(const ScopedPrimitiveArray&) = delete;

    // Movable
    ScopedPrimitiveArray(ScopedPrimitiveArray&& other) noexcept
        : env_(other.env_), array_(other.array_), elements_(other.elements_) {
        other.array_ = nullptr;
        other.elements_ = nullptr;
    }

    ScopedPrimitiveArray& operator=(ScopedPrimitiveArray&& other) noexcept {
        if (this != &other) {
            if (array_ && elements_) {
                release_elements(env_, array_, elements_, 0);
            }
            env_ = other.env_;
            array_ = other.array_;
            elements_ = other.elements_;
            other.array_ = nullptr;
            other.elements_ = nullptr;
        }
        return *this;
    }

private:
    JNIEnv* env_;
    jarray array_;
    T* elements_;

    static T* get_elements(JNIEnv* env, jarray array, jboolean* isCopy);
    static void release_elements(JNIEnv* env, jarray array, T* elements, jint mode);
};

template <>
inline jbyte* ScopedPrimitiveArray<jbyte>::get_elements(JNIEnv* env, jarray array, jboolean* isCopy) {
    return env->GetByteArrayElements(static_cast<jbyteArray>(array), isCopy);
}

template <>
inline void ScopedPrimitiveArray<jbyte>::release_elements(JNIEnv* env, jarray array, jbyte* elements, jint mode) {
    env->ReleaseByteArrayElements(static_cast<jbyteArray>(array), elements, mode);
}

template <>
inline jint* ScopedPrimitiveArray<jint>::get_elements(JNIEnv* env, jarray array, jboolean* isCopy) {
    return env->GetIntArrayElements(static_cast<jintArray>(array), isCopy);
}

template <>
inline void ScopedPrimitiveArray<jint>::release_elements(JNIEnv* env, jarray array, jint* elements, jint mode) {
    env->ReleaseIntArrayElements(static_cast<jintArray>(array), elements, mode);
}

template <>
inline jfloat* ScopedPrimitiveArray<jfloat>::get_elements(JNIEnv* env, jarray array, jboolean* isCopy) {
    return env->GetFloatArrayElements(static_cast<jfloatArray>(array), isCopy);
}

template <>
inline void ScopedPrimitiveArray<jfloat>::release_elements(JNIEnv* env, jarray array, jfloat* elements, jint mode) {
    env->ReleaseFloatArrayElements(static_cast<jfloatArray>(array), elements, mode);
}

template <>
inline jdouble* ScopedPrimitiveArray<jdouble>::get_elements(JNIEnv* env, jarray array, jboolean* isCopy) {
    return env->GetDoubleArrayElements(static_cast<jdoubleArray>(array), isCopy);
}

template <>
inline void ScopedPrimitiveArray<jdouble>::release_elements(JNIEnv* env, jarray array, jdouble* elements, jint mode) {
    env->ReleaseDoubleArrayElements(static_cast<jdoubleArray>(array), elements, mode);
}

// =============================================================================
// SCOPED STRING
// =============================================================================

class ScopedString {
public:
    ScopedString(JNIEnv* env, jstring str) : env_(env), str_(str), chars_(nullptr) {
        if (str_) {
            chars_ = env_->GetStringUTFChars(str_, nullptr);
        }
    }

    ~ScopedString() {
        if (str_ && chars_) {
            env_->ReleaseStringUTFChars(str_, chars_);
        }
    }

    const char* get() const { return chars_; }
    operator const char*() const { return chars_; }
    operator std::string() const { return chars_ ? std::string(chars_) : std::string(); }

    ScopedString(const ScopedString&) = delete;
    ScopedString& operator=(const ScopedString&) = delete;

private:
    JNIEnv* env_;
    jstring str_;
    const char* chars_;
};

#endif // JNI_COMMON_H