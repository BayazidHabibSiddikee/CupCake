# Custom API / Server Integration

## Overview

CupCake supports connecting to custom LLM inference servers beyond the built-in local Qwen model. This enables users to leverage cloud models (OpenAI, Anthropic), self-hosted models (Ollama, vLLM, TGI), or any OpenAI-compatible API endpoint.

## Supported Provider Types

| Provider Type | Protocol | Default Endpoint | Auth |
|---------------|----------|------------------|------|
| Local Qwen | GGML/JNI | N/A (on-device) | None |
| OpenAI Compatible | REST + SSE | `https://api.openai.com/v1` | Bearer token |
| Ollama | REST + SSE | `http://localhost:11434/v1` | Optional |
| vLLM | REST + SSE | `http://localhost:8000/v1` | Optional |
| Custom | REST + SSE | User-defined | Configurable |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      API ROUTER                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Request                                                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │         ModelConfig Resolution          │                   │
│  │  - Provider type                        │                   │
│  │  - Model name                           │                   │
│  │  - Endpoint URL                         │                   │
│  │  - API Key                              │                   │
│  │  - Parameters (temp, top_p, etc.)       │                   │
│  └────────────────┬────────────────────────┘                   │
│                   │                                             │
│         ┌─────────┴─────────┐                                   │
│         ▼                   ▼                                   │
│  ┌─────────────┐     ┌─────────────┐                           │
│  │  LOCAL      │     │  REMOTE     │                           │
│  │  QWEN JNI   │     │  API CLIENT │                           │
│  └─────────────┘     └──────┬──────┘                           │
│                             │                                   │
│                    ┌────────┴────────┐                          │
│                    ▼                 ▼                          │
│             ┌───────────┐     ┌───────────┐                    │
│             │ OpenAI    │     │ Ollama    │                    │
│             │ Compatible│     │ Native    │                    │
│             └───────────┘     └───────────┘                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Configuration

### ModelConfig
```kotlin
data class ModelConfig(
    val provider: ModelProvider = ModelProvider.LOCAL_QWEN,
    val modelName: String = "qwen2-0.5b-instruct-q4_k_m",
    val customEndpoint: String = "",
    val apiKey: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val systemPromptId: String? = null,
    val useStreaming: Boolean = true,
    val timeoutSeconds: Int = 60
) {
    enum class ModelProvider(val value: String) {
        LOCAL_QWEN("local_qwen"),
        CUSTOM_OPENAI("custom_openai"),
        CUSTOM_OLLAMA("custom_ollama"),
        CUSTOM_VLLM("custom_vllm"),
        CUSTOM_OTHER("custom_other")
    }

    fun effectiveEndpoint(): String {
        return when (provider) {
            ModelProvider.CUSTOM_OPENAI -> customEndpoint.ifBlank { "https://api.openai.com/v1" }
            ModelProvider.CUSTOM_OLLAMA -> customEndpoint.ifBlank { "http://localhost:11434/v1" }
            ModelProvider.CUSTOM_VLLM -> customEndpoint.ifBlank { "http://localhost:8000/v1" }
            ModelProvider.CUSTOM_OTHER -> customEndpoint
            else -> ""
        }
    }
}
```

### ApiProvider (Saved Configurations)
```kotlin
data class ApiProvider(
    val id: String,
    val name: String,                    // "My OpenAI", "Home Ollama"
    val providerType: ModelConfig.ModelProvider,
    val baseUrl: String,                 // Base URL without /v1
    val apiKey: String = "",             // Encrypted in storage
    val models: List<String> = emptyList(), // Cached model list
    val headers: Map<String, String> = emptyMap(), // Custom headers
    val isEnabled: Boolean = true,
    val createdAt: Instant
)
```

## API Client Implementation

### OpenAI-Compatible Endpoints
Supports any server implementing the OpenAI Chat Completions API:
- OpenAI (GPT-4, GPT-3.5)
- Azure OpenAI
- vLLM
- TGI (Text Generation Inference)
- LiteLLM proxy
- LocalAI
- OpenRouter

```kotlin
// Request format
{
  "model": "gpt-4",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "temperature": 0.7,
  "top_p": 0.9,
  "max_tokens": 2048,
  "stream": true
}

// Streaming response (SSE)
data: {"id":"chatcmpl-...","choices":[{"delta":{"content":"Hello"}}]}
data: {"id":"chatcmpl-...","choices":[{"delta":{"content":" there"},"finish_reason":"stop"}]}
data: [DONE]
```

### Ollama Native API
```kotlin
// Request format
{
  "model": "llama3",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "stream": true,
  "options": {
    "temperature": 0.7,
    "top_p": 0.9,
    "num_predict": 2048
  }
}

// Streaming response (NDJSON)
{"model":"llama3","created_at":"...","message":{"role":"assistant","content":"Hello"},"done":false}
{"model":"llama3","created_at":"...","message":{"role":"assistant","content":" there"},"done":true}
```

## Features

### 1. Connection Testing
- Test button validates endpoint reachability
- Attempts `/models` endpoint (OpenAI) or `/api/tags` (Ollama)
- Shows latency and available models

### 2. Model Discovery
- Fetches available models from provider
- Caches model list for offline viewing
- Auto-refresh on provider edit

### 3. Streaming Support
- Server-Sent Events (SSE) for OpenAI-compatible
- NDJSON for Ollama
- Real-time token display in chat UI

### 4. Failover & Retry
- Circuit breaker pattern (3 failures = open)
- Exponential backoff retry (1s, 2s, 4s)
- Fallback to local model if enabled

### 5. Per-Conversation Config
- Each conversation can use different provider/model
- System prompt linked to model config
- Persisted in database

## Security

### API Key Storage
- Keys encrypted using Android Keystore
- AES-256 GCM encryption
- Keys never logged or exposed in UI

### Network Security
- Certificate pinning for known providers (optional)
- Cleartext traffic blocked by default
- Custom CA support for self-hosted

### Privacy
- Request/response logging opt-in only
- No telemetry sent to CupCake servers
- Local-first: works offline with local model

## Settings UI

### Provider Management
1. **List View**: All configured providers with status
2. **Add Provider**: Name, type, URL, API key
3. **Test Connection**: Validates and fetches models
4. **Edit/Delete**: Modify or remove providers
5. **Set Default**: Choose default for new conversations

### Provider Types UI
- **OpenAI**: Standard OpenAI API format
- **Ollama**: Local Ollama instance
- **vLLM**: High-throughput inference server
- **Custom**: Generic OpenAI-compatible

## Integration Examples

### OpenAI
```
Name: "OpenAI GPT-4"
Type: OpenAI Compatible
URL: https://api.openai.com/v1
API Key: sk-...
Models: gpt-4, gpt-3.5-turbo, gpt-4-turbo
```

### Azure OpenAI
```
Name: "Azure OpenAI"
Type: OpenAI Compatible
URL: https://my-resource.openai.azure.com/openai/deployments/my-deployment
API Key: <azure-key>
Headers: api-key: <azure-key>, api-version: 2024-02-01
```

### Local Ollama
```
Name: "Local Ollama"
Type: Ollama
URL: http://192.168.1.100:11434
API Key: (empty)
Models: llama3, mistral, codellama, qwen2
```

### vLLM Server
```
Name: "vLLM Cluster"
Type: vLLM
URL: http://gpu-server:8000
API Key: (optional)
Models: meta-llama/Llama-3-70B, NousResearch/Hermes-2-Pro
```

### LocalAI (OpenAI-compatible)
```
Name: "LocalAI"
Type: Custom
URL: http://localhost:8080
API Key: (empty)
```

## Error Handling

| Error | Cause | Resolution |
|-------|-------|------------|
| 401 Unauthorized | Invalid API key | Check key, regenerate |
| 404 Not Found | Wrong endpoint/model | Verify URL, model name |
| 429 Rate Limited | Too many requests | Wait, reduce frequency |
| 500 Server Error | Server issue | Check server logs |
| Timeout | Slow response | Increase timeout, check network |
| Connection Refused | Server down | Start server, check firewall |

## Testing Checklist

- [ ] Local Qwen works offline
- [ ] OpenAI API key validation
- [ ] Ollama local network connection
- [ ] vLLM streaming response
- [ ] Custom endpoint with auth headers
- [ ] Model list fetching
- [ ] Conversation-specific config
- [ ] Failover to local model
- [ ] Encrypted API key storage
- [ ] Certificate validation

## Future Extensions

1. **Load Balancing**: Multiple endpoints for same provider
2. **Cost Tracking**: Token usage per provider
3. **Model Router**: Auto-select best model for task
4. **Plugin System**: Custom authentication flows
5. **Usage Analytics**: Local dashboard