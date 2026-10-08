cmake_minimum_required(VERSION 3.22.1)
project("cupcake_native" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build types
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Android-specific settings
if(ANDROID)
    set(CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION "clang")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
endif()

# Output directories
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${ANDROID_ABI})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${ANDROID_ABI})

# =============================================================================
# LLAMA.CPP (Primary inference engine)
# =============================================================================
# llama.cpp is added as a subdirectory - it handles its own GGML internally.
# NOTE: ggml backends must be STATIC on Android. Shared ggml .so files fail
# to link (hidden-visibility symbols such as ggml_aligned_malloc are not
# exported across the .so boundary). The JNI libs below stay SHARED.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp)

# =============================================================================
# TOKENIZERS-CPP (Optional - for advanced tokenization)
# =============================================================================
# add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/tokenizers-cpp)

# =============================================================================
# JNI BRIDGE FOR LLAMA
# =============================================================================
add_library(llama_jni SHARED
    llama/llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/src
)

target_link_libraries(llama_jni PRIVATE
    llama
    ${CMAKE_DL_LIBS}
    ${CMAKE_THREAD_LIBS_INIT}
    log
    android
)

target_compile_options(llama_jni PRIVATE
    -Wall -Wextra -Wpedantic
    -Wno-unused-parameter
    -DGGML_USE_ACCELERATE=OFF
    -DGGML_USE_METAL=OFF
    -DGGML_USE_CUDA=OFF
    -DGGML_USE_VULKAN=ON
    -DGGML_USE_SYCL=OFF
    -DGGML_USE_RPC=OFF
    -DGGML_USE_OPENMP=OFF
    -DGGML_USE_THREADS=ON
)

# =============================================================================
# QWEN JNI (compatibility shim over llama.cpp for com.cupcake.native.QwenNative)
# =============================================================================
add_library(qwen_jni SHARED
    jni/qwen_jni.cpp
    jni/jni_common.cpp
    qwen/inference.cpp
)

target_include_directories(qwen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/qwen
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/src
)

target_link_libraries(qwen_jni PRIVATE
    llama
    ${CMAKE_DL_LIBS}
    ${CMAKE_THREAD_LIBS_INIT}
    log
    android
)

target_compile_options(qwen_jni PRIVATE
    -Wall -Wextra
    -Wno-unused-parameter
)

# =============================================================================
# TESTS
# =============================================================================
enable_testing()

# =============================================================================
# INSTALL
# =============================================================================
install(TARGETS llama_jni qwen_jni
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
)