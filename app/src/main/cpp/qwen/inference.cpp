// InferenceEngine implementation backed by llama.cpp (pinned: b4122).
//
// This is the "qwen_jni" compatibility engine: it exposes the legacy
// QwenNative JNI surface while running inference through the same
// llama.cpp core as llama_jni. The separate tokenizer file argument is
// accepted for API compatibility but unused - the GGUF model carries
// its own vocabulary.

#include "qwen/inference.h"

#include <atomic>
#include <cstdio>
#include <cstring>
#include <ctime>

#include "llama.h"

namespace cupcake {
namespace qwen {
namespace {

// llama_backend_init/free are process-wide, but both native libraries
// (llama_jni and qwen_jni) may be loaded in one process. Reference-count
// here so an early shutdown on one engine cannot pull the backend out
// from under the other.
std::atomic<int> g_backend_refs{0};

void backend_acquire() {
    if (g_backend_refs.fetch_add(1) == 0) {
        llama_backend_init();
    }
}

void backend_release() {
    if (g_backend_refs.fetch_sub(1) == 1) {
        llama_backend_free();
    }
}

} // namespace

struct InferenceEngine::Impl {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    InferenceConfig config;
};

InferenceEngine::InferenceEngine() : pimpl_(std::make_unique<Impl>()) {}

InferenceEngine::~InferenceEngine() {
    shutdown();
}

InferenceEngine::InferenceEngine(InferenceEngine&&) noexcept = default;
InferenceEngine& InferenceEngine::operator=(InferenceEngine&&) noexcept = default;

int InferenceEngine::initialize(const InferenceConfig& config) {
    if (pimpl_->model != nullptr) {
        return -1; // already initialised
    }

    backend_acquire();

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = config.use_mmap;
    model_params.use_mlock = config.use_mlock;
    model_params.n_gpu_layers = 0; // CPU-only on Android

    pimpl_->model =
        llama_load_model_from_file(config.model_path.c_str(), model_params);
    if (pimpl_->model == nullptr) {
        backend_release();
        return -2; // model load failed
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = config.n_ctx > 0 ? (uint32_t) config.n_ctx : 4096;
    ctx_params.n_batch = config.n_batch > 0 ? (uint32_t) config.n_batch : 512;
    ctx_params.n_threads = config.n_threads > 0 ? config.n_threads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    pimpl_->ctx = llama_new_context_with_model(pimpl_->model, ctx_params);
    if (pimpl_->ctx == nullptr) {
        llama_free_model(pimpl_->model);
        pimpl_->model = nullptr;
        backend_release();
        return -3; // context creation failed
    }

    pimpl_->config = config;
    initialized_ = true;
    return 0;
}

int InferenceEngine::generate_stream(const std::string& prompt,
                                      const SamplingParams& params,
                                      TokenCallback callback) {
    if (!initialized_ || pimpl_->ctx == nullptr || pimpl_->model == nullptr) {
        return -1;
    }

    llama_model* model = pimpl_->model;
    llama_context* ctx = pimpl_->ctx;

    // Tokenise prompt (BOS added per model defaults).
    const int32_t n_ctx = (int32_t) llama_n_ctx(ctx);
    std::vector<llama_token> tokens(n_ctx);
    const int32_t n_tokens = llama_tokenize(model, prompt.c_str(),
                                            (int32_t) prompt.size(),
                                            tokens.data(), n_ctx, true, false);
    if (n_tokens <= 0) {
        return -2; // tokenisation failed (or empty prompt)
    }
    tokens.resize(n_tokens);

    // Sampler chain: penalties -> top_k -> top_p -> min_p -> temp -> dist.
    llama_sampler* smpl =
        llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(
        smpl, llama_sampler_init_penalties(
                  llama_n_vocab(model), llama_token_eos(model),
                  -1, // linefeed_id (unused, penalize_nl=false)
                  params.repeat_last_n, params.repeat_penalty,
                  params.frequency_penalty, params.presence_penalty,
                  false, // penalize_nl
                  false  // ignore_eos
                  ));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(params.top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(params.top_p, 1));
    if (params.min_p > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(params.min_p, 1));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(params.temperature));
    const uint32_t seed =
        params.seed < 0 ? (uint32_t) time(nullptr) : (uint32_t) params.seed;
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));

    int result = 0;
    const int max_tokens = params.max_tokens > 0 ? params.max_tokens : 2048;

    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), n_tokens))) {
        llama_sampler_free(smpl);
        return -3; // prompt processing failed
    }

    int generated = 0;
    while (generated < max_tokens) {
        const llama_token id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_token_is_eog(model, id)) {
            break;
        }
        tokens.push_back(id);

        if (llama_decode(ctx, llama_batch_get_one(&id, 1))) {
            result = -4; // decode failed mid-generation
            break;
        }

        char buf[256];
        const int len =
            llama_token_to_piece(model, id, buf, sizeof(buf), 0, true);
        if (len > 0 && callback) {
            callback(std::string(buf, len));
        }
        ++generated;

        llama_sampler_accept(smpl, id);
    }

    llama_sampler_free(smpl);
    return result;
}

void InferenceEngine::shutdown() {
    if (pimpl_->ctx != nullptr) {
        llama_free(pimpl_->ctx);
        pimpl_->ctx = nullptr;
    }
    if (pimpl_->model != nullptr) {
        llama_free_model(pimpl_->model);
        pimpl_->model = nullptr;
    }
    if (initialized_) {
        initialized_ = false;
        backend_release();
    }
}

ModelInfo InferenceEngine::get_model_info() const {
    ModelInfo info;
    if (pimpl_->model == nullptr) {
        return info;
    }
    char desc[256] = {};
    llama_model_desc(pimpl_->model, desc, sizeof(desc));
    info.name = desc;
    info.architecture = "gguf/llama.cpp";
    info.n_vocab = llama_n_vocab(pimpl_->model);
    info.n_ctx_train = llama_n_ctx_train(pimpl_->model);
    return info;
}

std::string InferenceEngine::get_model_info_string() const {
    if (pimpl_->model == nullptr) {
        return "Model not loaded";
    }
    const ModelInfo info = get_model_info();
    char buf[512];
    snprintf(buf, sizeof(buf),
             "Model: %s\nParams: %llu\nVocab: %d\nTrainCtx: %d\nCtx: %u\n",
             info.name.c_str(),
             (unsigned long long) llama_model_n_params(pimpl_->model),
             info.n_vocab, info.n_ctx_train,
             pimpl_->ctx != nullptr ? llama_n_ctx(pimpl_->ctx) : 0);
    return std::string(buf);
}

} // namespace qwen
} // namespace cupcake
