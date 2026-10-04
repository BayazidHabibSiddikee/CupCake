cmake_minimum_required(VERSION 3.22.1)
project("qwen_native" LANGUAGES C CXX)

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

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/qwen
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tokenizers-cpp/include
)

# =============================================================================
# THIRD PARTY: GGML (Git submodule)
# =============================================================================
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/ggml)

# =============================================================================
# THIRD PARTY: tokenizers-cpp (Git submodule)
# =============================================================================
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/tokenizers-cpp)

# =============================================================================
# QWEN ENGINE
# =============================================================================
add_library(qwen_engine STATIC
    qwen/qwen_model.cpp
    qwen/tokenizer.cpp
    qwen/inference.cpp
    qwen/quantization.cpp
    qwen/ggml_backend.cpp
)

target_include_directories(qwen_engine PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/qwen
)

target_link_libraries(qwen_engine PUBLIC
    ggml
    tokenizers_cpp
    ${CMAKE_DL_LIBS}
    ${CMAKE_THREAD_LIBS_INIT}
    log
)

target_compile_options(qwen_engine PRIVATE
    -Wall -Wextra -Wpedantic
    -Wno-unused-parameter
    -Wno-sign-compare
    -DGGML_USE_ACCELERATE=OFF
    -DGGML_USE_METAL=OFF
    -DGGML_USE_CUDA=OFF
    -DGGML_USE_VULKAN=OFF
    -DGGML_USE_SYCL=OFF
    -DGGML_USE_RPC=OFF
    -DGGML_USE_OPENMP=OFF
    -DGGML_USE_THREADS=ON
)

# =============================================================================
# UTILS
# =============================================================================
add_library(qwen_utils STATIC
    utils/logger.cpp
    utils/memory.cpp
    utils/threading.cpp
)

target_include_directories(qwen_utils PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
)

target_compile_options(qwen_utils PRIVATE
    -Wall -Wextra -Wpedantic
)

# =============================================================================
# JNI BRIDGE
# =============================================================================
add_library(qwen_jni SHARED
    jni/qwen_jni.cpp
    jni/jni_common.cpp
)

target_include_directories(qwen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

target_link_libraries(qwen_jni PRIVATE
    qwen_engine
    qwen_utils
    ${CMAKE_DL_LIBS}
    log
)

target_compile_options(qwen_jni PRIVATE
    -Wall -Wextra -Wpedantic
    -Wno-unused-parameter
)

# =============================================================================
# BLE JNI (Optional - for direct BLE from native if needed)
# =============================================================================
# add_library(ble_jni SHARED
#     jni/ble_jni.cpp
#     jni/jni_common.cpp
# )
# target_link_libraries(ble_jni PRIVATE log)

# =============================================================================
# TESTS
# =============================================================================
enable_testing()

add_executable(test_tokenizer
    test/test_tokenizer.cpp
)
target_link_libraries(test_tokenizer PRIVATE qwen_engine qwen_utils tokenizers_cpp)
add_test(NAME test_tokenizer COMMAND test_tokenizer)

add_executable(test_inference
    test/test_inference.cpp
)
target_link_libraries(test_inference PRIVATE qwen_engine qwen_utils ggml)
add_test(NAME test_inference COMMAND test_inference)

# =============================================================================
# INSTALL (for prebuilt distribution)
# =============================================================================
install(TARGETS qwen_jni
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
)

install(FILES
    ${CMAKE_CURRENT_SOURCE_DIR}/jni/qwen_jni.h
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/qwen
)