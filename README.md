# CupCake

AI-powered ESP32 + Android application with on-device Qwen model inference, custom API server support, and rich system prompts with image uploads.

## Features

- **On-Device LLM**: Quantized Qwen model (0.5B/1.5B) running via GGML/JNI
- **Custom API Support**: Connect to OpenAI, Ollama, vLLM, or any OpenAI-compatible endpoint
- **System Prompts with Images**: Upload images (diagrams, docs, screenshots) - OCR extracts text for context
- **ESP32 Sensor Integration**: Real-time IMU, battery data via BLE
- **Offline-First**: Works completely offline with local model
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
├── app/                        # Android App (Kotlin + C++)
│   ├── src/main/
│   │   ├── java/com/cupcake/
│   │   │   ├── ui/screen/
│   │   │   │   ├── chat/       # Chat with streaming
│   │   │   │   ├── systemprompt/ # System prompt editor
│   │   │   │   └── settings/   # Custom API providers
│   │   │   ├── data/model/     # ChatMessage, SystemPrompt, ModelConfig
│   │   │   ├── domain/         # Clean architecture use cases
│   │   │   └── native/         # QwenNative JNI bridge
│   │   ├── cpp/                # C++ inference engine
│   │   └── assets/models/      # Qwen GGUF model
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
- **Settings**: Add custom API providers (OpenAI, Ollama, vLLM, custom)
- **Local Inference**: Qwen via GGML JNI (INT4 quantization)
- **Remote Inference**: OpenAI-compatible SSE, Ollama NDJSON streaming
- **Database**: Room with SQLCipher encryption

## Architecture Highlights

### Hybrid Inference Router
```
User Message → [System Prompt + Context] → Router → [Local Qwen | Custom API] → Stream Response
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
| Ollama | REST + NDJSON | Local Ollama instances |
| Custom | REST + SSE | Any OpenAI-compatible endpoint |

## Requirements

- ESP32-S3 development board
- Android Studio Ladybug+ (Koala recommended)
- NDK r26+
- CMake 3.22+
- PlatformIO (for ESP32)

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

### Model Setup
1. Download Qwen GGUF model: `qwen2-0.5b-instruct-q4_k_m.gguf`
2. Place in `app/src/main/assets/models/`
3. Download tokenizer: `tokenizer.json` 
4. Place in `app/src/main/assets/tokenizer/`

## Configuration

### Custom API Provider (Settings → Add Provider)
- **OpenAI**: `https://api.openai.com/v1` + API key
- **Ollama**: `http://localhost:11434/v1` (no key needed)
- **vLLM**: `http://localhost:8000/v1`
- **Custom**: Any OpenAI-compatible endpoint

### Per-Conversation Settings
- Tap model indicator in chat header
- Select provider, model, parameters
- Choose system prompt (with images)

## Development Plan

See `docs/plan.md` for 8-week phased roadmap:
- Phase 1: Foundation (ESP32 + Android scaffold)
- Phase 2: Qwen Integration (GGML + JNI)
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