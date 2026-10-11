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
│   ├── ollama-inference.md      # Ollama HTTP API inference
│   ├── api-gateway.md           # Custom API/Server integration
│   └── system-prompt.md         # System prompt with image support
├── api/
│   ├── ble-protocol.md          # BLE communication protocol
│   ├── rest-api.md              # REST API for custom servers
│   └── websocket-api.md         # WebSocket for streaming
├── models/
│   ├── ollama-models.md         # Ollama model configuration
│   ├── custom-model.md          # Custom model integration
│   └── system-prompt.md         # System prompt schema
└── security/
    ├── threat-model.md          # Threat analysis
    ├── data-protection.md       # Encryption, keys
    └── api-security.md          # API authentication
```

## Key Architectural Decisions

### 1. Server-Based Inference Model
- **Ollama server**: Local/remote HTTP API (smollm, qwen2:0.5b)
- **Custom endpoints**: OpenAI-compatible APIs (vLLM, TGI, OpenAI, etc.)
- **Routing**: ModelConfig selects provider type

### 2. System Prompt with Images
- Users upload images (diagrams, docs, screenshots)
- OCR + vision model extracts text/context
- Combined with text prompt for system instruction
- Stored per-conversation, versioned

### 3. Multi-Server Support
- Default: Ollama at http://localhost:11434/v1
- Configurable: Custom OpenAI-compatible endpoints
- Auth: API keys, Bearer tokens
- Connection pooling, timeout, retry

### 4. Data Flow
```
User Input → [System Prompt + Context] → ApiClient → [Ollama | OpenAI-compatible] → Stream Response
                     ↑                         ↓
               Image Upload              Config Store (Room)
```

## Technology Stack

| Layer | Technology |
|-------|------------|
| Mobile UI | Jetpack Compose, Material3 |
| Inference | OllamaEngine (OkHttp + NDJSON/SSE) |
| Networking | OkHttp, Retrofit, WebSocket |
| Database | Room (SQLCipher), DataStore |
| DI | Hilt |
| BLE | AndroidX Bluetooth |
| ESP32 | Arduino/ESP-IDF, NimBLE |
| Serialization | Kotlinx Serialization (JSON), CBOR |
