# CupCake

AI-powered ESP32 + Android application with on-device tiny-LM inference, Ollama backend support (smollm, qwen2:0.5b), custom API server support, and rich system prompts with image uploads.

## Features

- **Ollama Backend**: Connect to Ollama server for smollm and qwen2:0.5b models
- **Custom API Support**: Connect to OpenAI, Ollama, vLLM, or any OpenAI-compatible endpoint
- **System Prompts with Images**: Upload images (diagrams, docs, screenshots) - OCR extracts text for context
- **ESP32 Sensor Integration**: Real-time IMU, battery data via BLE
- **Per-Conversation Config**: Different models, prompts, settings per chat

## Project Structure

```
CupCake/
├── architecture/               # Architecture documentation
│   ├── component-design/
│   │   ├── api-gateway.md      # Custom API integration
│   │   ├── system-prompt.md    # System prompt with images
│   │   └── ...
├── esp/                        # ESP32 Firmware (PlatformIO)
│   ├── src/main.cpp            # Dual-core FreeRTOS app
│   ├── include/config.h        # Hardware config
│   └── platformio.ini
├── app/                        # Android App (Kotlin)
│   ├── src/main/
│   │   ├── java/com/cupcake/
│   │   │   ├── ui/screen/
│   │   │   │   ├── chat/       # Chat with streaming
│   │   │   │   ├── systemprompt/ # System prompt editor
│   │   │   │   └── settings/   # Custom API providers
│   │   │   ├── data/model/     # ChatMessage, SystemPrompt, ModelConfig
│   │   │   ├── domain/         # Clean architecture use cases
│   │   │   └── ai/             # OllamaEngine for HTTP inference
├── docs/                       # Project documentation
└── README.md
```

## Components

### ESP32 Firmware (`esp/`)
- Sensor data collection (accelerometer, gyroscope, temperature)
- BLE GATT server (Nordic UART compatible)
- Binary protocol (CBOR) for efficient data transfer
- Deep sleep + OTA updates
- Dual-core FreeRTOS (sensor on core 0, BLE on core 1)

### Android App (`app/`)
- **Chat Screen**: Streaming responses, model indicator, system prompt display
- **System Prompt Editor**: Create/edit prompts, attach images with OCR
- **Settings**: Configure Ollama endpoint (default: http://localhost:11434/v1)
- **Remote Inference**: Ollama NDJSON streaming, OpenAI-compatible SSE
- **Database**: Room with SQLCipher encryption

## Architecture Highlights

### Ollama Inference Router
```
User Message → [System Prompt + Context] → OllamaEngine → Ollama Server → Stream Response
```

### System Prompt with Images
1. User uploads images via gallery/camera
2. On-device OCR (ML Kit) extracts text
3. Combined: `Text Prompt + [Image 1: desc + extracted text] + ...`
4. Prepended to every conversation message

### Custom API Providers
| Provider | Protocol | Use Case |
|----------|----------|----------|
| OpenAI Compatible | REST + SSE | OpenAI, Azure, vLLM, TGI, LiteLLM |
| Ollama | REST + NDJSON | Local Ollama instances (smollm, qwen2:0.5b) |
| Custom | REST + SSE | Any OpenAI-compatible endpoint |

## On-Device Models

Two fine-tuned companion models are published on Hugging Face. Both are trained to reply in
Banglish with a leading `[emotion]` tag and an optional `*action*` beat, and are intended to be
**deliberately silly** ("dumbass by design") rather than a general assistant.

| Model | Base | Params | Q4_0 size | Hugging Face |
|-------|------|--------|-----------|--------------|
| `liltii-v0.2` | Qwen2.5-0.5B-Instruct | 494M | 271 MiB | [bayazidhs04/liltii-v0.2-qwen2.5-0.5b](https://huggingface.co/bayazidhs04/liltii-v0.2-qwen2.5-0.5b) |
| `bangla-smollm-135m-cupcake` | rnnandi/bangla-smollm-135m | 210M | 127 MiB | [bayazidhs04/bangla-smollm-135m-cupcake](https://huggingface.co/bayazidhs04/bangla-smollm-135m-cupcake) |

### Quantization recipe

Both models ship as **Q4_0 with quantized token embeddings and output tensor** (`--token-embedding-type Q4_0 --output-tensor-type Q4_0`, 4.50 BPW). k-quants (Q2_K/Q3_K/Q4_K) are unusable
for these architectures because the hidden/KV/vocab dimensions are not multiples of 256, so most
tensors fall back to a larger type — producing files that are both bigger and worse. The Q4_0
recipe is ~22% faster at equal parameter count than a default Q4_0 export.

### Benchmarks

llama.cpp `b11669`, 4 threads, all models Q4_0. `PPL`/`BPB` measured on an in-domain
Banglish chat corpus (40,645 bytes); `tag%`/`action%`/`chars` from 25 chat prompts with the
model's own chat template (temp 0.7, seed 42).

| Model | Params | Size | pp64 (t/s) | tg32 (t/s) | PPL ↓ | BPB ↓ | tag% | action% | reply chars |
|-------|--------|------|-----------|-----------|-------|-------|------|---------|-------------|
| **liltii-v0.2** | 494M | 271 MiB | 84.7 | **20.9** | **16.1** | **1.35** | **52** | **56** | **56** |
| Qwen2.5-0.5B-Instruct (base) | 494M | 331 MiB | 71.4 | 17.1 | 25.2 | 1.56 | 0 | 0 | 200 |
| SmolLM2-360M-Instruct | 362M | 217 MiB | 75.7 | 18.8 | 18.6 | 1.55 | 0 | 0 | 182 |
| SmolLM2-135M-Instruct | 134M | 86 MiB | 164.9 | 33.9 | 22.3 | 1.64 | 0 | 4 | 181 |
| Qwen3-0.6B | 596M | 359 MiB | 65.4 | 16.0 | 26.8 | 1.59 | 0 | 0 | 159 |
| Llama-3.2-1B-Instruct | 1.24B | 730 MiB | — | 9.5 | 23.0 | 1.52 | 0 | 12 | 204 |
| bangla-smollm-135m (base) | 210M | 113 MiB | 168.7 | 29.6 | 413.4 | 3.04 | 0 | 0 | 168 |
| bangla-smollm-135m-cupcake | 210M | 127 MiB | 182.0 | 29.0 | 1005.9 | 3.76 | 0 | 0 | 181 |

BPB (bits per byte) is the cross-tokenizer-comparable metric — PPL is not, since the
vocabularies differ. Findings:

- **`liltii-v0.2` is the clear winner.** It is the only model that actually implements the persona
  (52% emotion tags, 56% action beats, 56-char replies instead of 160–200), has the best in-domain
  BPB, and is faster than its own base at equal parameters.
- **The `bangla-smollm-135m` fine-tune is a documented failure.** It regressed against its own base
  (BPB 3.04 → 3.76, PPL 413 → 1006) and rambles in repetitive loops. Kept public for transparency.
- Both published GGUFs embed their chat template, so `llama.cpp`/Transformers chat inference works
  out of the box.

### Running a model

```bash
llama-cli -hf bayazidhs04/liltii-v0.2-qwen2.5-0.5b \
  --gguf-file liltii-v0.2-v3-q4_0.gguf -cnv
```

## Requirements

- ESP32-S3 development board
- Android Studio Ladybug+ (Koala recommended)
- Ollama server running locally or remote

## Building

### ESP32 Firmware
```bash
cd esp
pio run -t upload
pio device monitor
```

### Android App
```bash
cd app
./gradlew assembleDebug
# Or from root:
./gradlew :app:assembleDebug
```

## Configuration

### Ollama Setup
1. Install Ollama: `curl -fsSL https://ollama.com/install.sh | sh`
2. Pull models: `ollama pull smollm` and `ollama pull qwen2:0.5b`
3. Start server: `ollama serve`

### Custom API Provider (Settings → Add Provider)
- **Ollama**: `http://localhost:11434/v1` (no key needed)
- **OpenAI**: `https://api.openai.com/v1` + API key
- **vLLM**: `http://localhost:8000/v1`
- **Custom**: Any OpenAI-compatible endpoint

### Per-Conversation Settings
- Tap model indicator in chat header
- Select provider, model, parameters
- Choose system prompt (with images)

## Development Plan

See `docs/plan.md` for 8-week phased roadmap:
- Phase 1: Foundation (ESP32 + Android scaffold)
- Phase 2: Ollama Integration (HTTP API + streaming)
- Phase 3: Sensor Fusion + Communication
- Phase 4: Polish + Production

## Architecture Documentation

- `architecture/README.md` - Overview
- `architecture/component-design/api-gateway.md` - Custom API design
- `architecture/component-design/system-prompt.md` - System prompt with images
- `docs/architecture.md` - Full system architecture
- `docs/path.md` - Complete file structure

## License

MIT