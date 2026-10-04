# CupCake Architecture

## Overview

This directory contains architecture diagrams, design documents, and technical specifications for the CupCake system.

## Contents

```
architecture/
├── README.md                    # This file
├── system-overview.md           # High-level system architecture
├── data-flow.md                 # Data flow diagrams
├── component-design/
│   ├── esp32-firmware.md        # ESP32 firmware design
│   ├── android-app.md           # Android app architecture
│   ├── qwen-inference.md        # Qwen on-device inference
│   ├── api-gateway.md           # Custom API/Server integration
│   └── system-prompt.md         # System prompt with image support
├── api/
│   ├── ble-protocol.md          # BLE communication protocol
│   ├── rest-api.md              # REST API for custom servers
│   └── websocket-api.md         # WebSocket for streaming
├── models/
│   ├── qwen-config.md           # Qwen model configuration
│   ├── custom-model.md          # Custom model integration
│   └── system-prompt.md         # System prompt schema
└── security/
    ├── threat-model.md          # Threat analysis
    ├── data-protection.md       # Encryption, keys
    └── api-security.md          # API authentication
```

## Key Architectural Decisions

### 1. Hybrid Inference Model
- **On-device**: Qwen-0.5B/1.5B quantized (INT4) via GGML
- **Cloud fallback**: Custom API endpoints (OpenAI-compatible)
- **Routing**: Automatic based on connectivity, latency, user preference

### 2. System Prompt with Images
- Users upload images (diagrams, docs, screenshots)
- OCR + vision model extracts text/context
- Combined with text prompt for system instruction
- Stored per-conversation, versioned

### 3. Multi-Server Support
- Built-in: Local Qwen (offline-first)
- Configurable: Custom OpenAI-compatible endpoints
- Auth: API keys, OAuth, mTLS
- Load balancing, failover, circuit breaker

### 4. Data Flow
```
User Input → [System Prompt + Context] → Router → [Local Qwen | Custom API] → Stream Response
                    ↑                         ↓
              Image Upload              Config Store
```

## Technology Stack

| Layer | Technology |
|-------|------------|
| Mobile UI | Jetpack Compose, Material3 |
| Local Inference | GGML, GGUF, tokenizers-cpp |
| Networking | OkHttp, Retrofit, WebSocket |
| Database | Room (SQLCipher), DataStore |
| DI | Hilt |
| BLE | AndroidX Bluetooth |
| ESP32 | Arduino/ESP-IDF, NimBLE |
| Serialization | Kotlinx Serialization (JSON), CBOR |