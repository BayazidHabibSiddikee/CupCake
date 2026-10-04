#include "jni_common.h"

// This file exists to provide a compilation unit for jni_common.h
// All implementations are inline in the header

namespace cupcake {
namespace jni {

// Version info for native library
const char* get_native_version() {
    return "1.0.0";
}

const char* get_native_build_date() {
    return __DATE__ " " __TIME__;
}

} // namespace jni
} // namespace cupcake