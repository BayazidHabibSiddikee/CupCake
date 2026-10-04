#ifndef INFERENCE_H
#define INFERENCE_H

#include <string>
#include <vector>
#include <functional>
#include <memory>

#include "ggml.h"
#include "gguf.h"

namespace cupcake {
namespace qwen {

struct InferenceConfig {
    std::string model_path;
    std::string tokenizer_path;
    int n_threads = 4;
    int n_ctx = 4096;
    int n_batch = 512;
    int n_ubatch = 512;
    bool use_mmap = true;
    bool use_mlock = false;
    bool vocab_only = false;
};

struct SamplingParams {
    float temperature = 0.7f;
    float top_p = 0.9f;
    int top_k = 40;
    float min_p = 0.0f;
    int max_tokens = 2048;
    float repeat_penalty = 1.1f;
    int repeat_last_n = 64;
    float frequency_penalty = 0.0f;
    float presence_penalty = 0.0f;
    int seed = -1;
    std::vector<int> grammar_trigger_tokens;
};

struct ModelInfo {
    std::string name;
    std::string version;
    std::string architecture;
    int n_vocab = 0;
    int n_ctx_train = 0;
    int n_embd = 0;
    int n_layer = 0;
    int n_head = 0;
    int n_rot = 0;
    std::string quantization;
    size_t file_size = 0;
    size_t memory_required = 0;
};

using TokenCallback = std::function<void(const std::string& token)>;

class InferenceEngine {
public:
    InferenceEngine();
    ~InferenceEngine();

    // Non-copyable
    InferenceEngine(const InferenceEngine&) = delete;
    InferenceEngine& operator=(const InferenceEngine&) = delete;

    // Movable
    InferenceEngine(InferenceEngine&&) noexcept;
    InferenceEngine& operator=(InferenceEngine&&) noexcept;

    // Initialize the engine with model and tokenizer
    int initialize(const InferenceConfig& config);

    // Generate text stream from prompt
    int generate_stream(
        const std::string& prompt,
        const SamplingParams& params,
        TokenCallback callback
    );

    // Shutdown and release resources
    void shutdown();

    // Check if initialized
    bool is_initialized() const { return initialized_; }

    // Get model info
    ModelInfo get_model_info() const;

    // Get model info as string
    std::string get_model_info_string() const;

private:
    struct Impl;
    std::unique_ptr<Impl> pimpl_;
    bool initialized_ = false;
};

} // namespace qwen
} // namespace cupcake

#endif // INFERENCE_H