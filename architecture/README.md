# CupCake Architecture

> The current, authoritative system architecture lives in
> **[../ARCHITECTURE.md](../ARCHITECTURE.md)** — layers, AI stack, game/points/demand
> subsystems, data flow, DI, build and test strategy.

This directory holds supplementary, component-scoped design notes that go deeper on individual
pieces than the top-level document.

## Contents

```
architecture/
├── README.md                        # This file — index into the design notes
└── component-design/
    ├── api-gateway.md               # Custom API / server integration design
    └── system-prompt.md             # System-prompt schema (with image/OCR support)
```

## Where to look

| Topic | Document |
|-------|----------|
| Whole-system architecture (current) | [../ARCHITECTURE.md](../ARCHITECTURE.md) |
| Product features & usage | [../README.md](../README.md) |
| On-device LLM, prompt, emotion, TTS, games, points, demands | [../ARCHITECTURE.md](../ARCHITECTURE.md) |
| Custom API/server gateway | [component-design/api-gateway.md](component-design/api-gateway.md) |
| System prompt schema | [component-design/system-prompt.md](component-design/system-prompt.md) |
| Roadmap | [../docs/plan.md](../docs/plan.md) |
| Gap analysis | [../docs/GAP_ANALYSIS.md](../docs/GAP_ANALYSIS.md) |
| Model write-up | [../docs/blog/on-device-companion-models.md](../docs/blog/on-device-companion-models.md) |

## Key architectural decisions (current)

1. **On-device inference is the default.** The bundled `liltii-v0.2-v3-q4_0.gguf` runs through
   llama.cpp over JNI (`LlamaEngine`); the app is fully functional offline.
2. **Remote providers are optional.** `ModelConfig.ModelProvider` supports OpenAI-compatible,
   Ollama, vLLM, and custom endpoints for higher-quality replies — but **games always stay
   on-device** (`GameLLMRouter`) so a misconfigured key can never make the bot silent.
3. **One emotion vocabulary.** A `[tag]` at the head of every reply is the single source of truth
   for on-screen art, voice language, and the ESP32 face/motor (`EmotionAssets`).
4. **Two currencies.** `PointsManager` keeps spendable coins separate from lifetime XP/level, so
   satisfying a demand can never cost a level.
5. **Hardware is a pure contract.** The ESP face/motor payloads (`EspFace`, `ProtocolDecoder`) are
   pure functions, unit-testable without a board.

## Historical note

`docs/architecture.md` is retained only as a pointer; its original content described the earlier
Ollama-server-centric design (ESP32 as a sensor node with remote smollm/qwen2 inference), which
has since been replaced by on-device llama.cpp inference.

## Technology stack

| Layer | Technology |
|-------|------------|
| Mobile UI | Jetpack Compose, Material 3, Navigation Compose |
| Inference | llama.cpp via JNI (`LlamaEngine`); optional Ollama (NDJSON) / OpenAI-compatible (SSE) |
| Networking | Ktor WebSocket (`EspWebSocketServer`), OkHttp, Retrofit |
| BLE | AndroidX Bluetooth, Nordic UART Service |
| Database | Room, SharedPreferences / DataStore stores |
| Background | WorkManager (demands, proactive outreach), foreground services (BLE, Shimeji overlay) |
| DI | Hilt |
| Images | Coil (static + animated GIF avatars) |
| Serialization | kotlinx.serialization (JSON), binary `ProtocolDecoder` |
| Firmware | ESP32-S3, PlatformIO (Arduino/ESP-IDF core) |
