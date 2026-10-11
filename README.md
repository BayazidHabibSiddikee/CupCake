# CupCake

AI-powered ESP32 + Android application with Ollama backend support (smollm, qwen2:0.5b), custom API server support, and rich system prompts with image uploads.

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