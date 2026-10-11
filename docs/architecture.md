# System Architecture

## Overview

CupCake is a hybrid edge-AI system combining an ESP32-S3 microcontroller for sensor acquisition and an Android application that connects to an Ollama server for LLM inference (smollm, qwen2:0.5b models).

```
┌─────────────────────────────────────────────────────────────────┐
│                        CUPCAKE SYSTEM                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐         BLE / WiFi          ┌──────────────┐  │
│  │   ESP32-S3   │◄────────────────────────────►│  ANDROID     │  │
│  │  (Edge Node) │   Sensor Data / Commands    │  (Host App)  │  │
│  └──────────────┘                             └──────────────┘  │
│       │                                            │            │
│       ▼                                            ▼            │
│  ┌──────────────┐                         ┌──────────────┐     │
│  │  Sensors     │                         │  Ollama      │     │
│  │  - IMU       │                         │  (HTTP API)  │     │
│  │  - Temp      │                         │  - smollm    │     │
│  │  - Battery   │                         │  - qwen2:0.5b│     │
│  └──────────────┘                         └──────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### ESP32 Firmware (`esp/`)

```
┌────────────────────────────────────────────────────────────────┐
│                      ESP32 FIRMWARE                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │   Sensor    │  │    Comm     │  │   Power     │             │
│  │   Manager   │  │   Manager   │  │   Manager   │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                │                │                      │
│         ▼                ▼                ▼                      │
│  ┌─────────────────────────────────────────────────────┐        │
│  │                   Event Loop                        │        │
│  │  (FreeRTOS tasks + ESP-IDF event system)           │        │
│  └─────────────────────────────────────────────────────┘        │
│         │                │                │                      │
│    ┌────┴────┐      ┌────┴────┐    ┌────┴────┐                 │
│    ▼         ▼      ▼         ▼    ▼         ▼                 │
│  IMU      Temp    BLE       WiFi  Sleep    OTA                  │
│  Task     Task    GATT      Task  Task     Task                 │
│                                                                │
│  Peripherals: I2C (IMU), ADC (Battery), GPIO (LED/Button)     │
│  Storage: NVS (config), SPIFFS (certificates)                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Key Design Decisions:**
- **FreeRTOS** with pinned tasks: Sensor (Core 0), Comm (Core 1)
- **NimBLE** stack for BLE 5.0 (2M PHY, extended advertising)
- **Binary protocol** (CBOR) over BLE GATT notifications
- **Deep sleep** between sensor reads (configurable 100ms-10s)
- **Watchdog** on each task with panic recovery

### Android Application (`app/`)

```
┌────────────────────────────────────────────────────────────────┐
│                    ANDROID APPLICATION                         │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      UI LAYER                            │  │
│  │  Compose Screens ◄── ViewModels ◄── StateFlow/LiveData   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      DOMAIN LAYER                        │  │
│  │  UseCases (ConnectDevice, SendMessage, GetSensorData)    │  │
│  │  Repository Interfaces                                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      DATA LAYER                          │  │
│  │  RepositoryImpl ◄── Local (Room) + Remote (BLE, Ollama)  │  │
│  │  DataSources: BleDataSource, Database, Preferences       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Module Dependencies:**
```
app
├── ui (compose, viewmodel, navigation)
├── domain (usecase, model, repository interfaces)
├── data (repository impl, Room, BLE, DataStore, OllamaEngine)
├── ai (OllamaEngine for HTTP inference)
└── core (di, util, constants) → all modules depend on this
```

### Ollama Inference Engine (HTTP API)

The Android app connects to a local or remote Ollama server via HTTP API for LLM inference. No native/JNI code is required.

**Model Specifications:**
| Parameter | Value |
|-----------|-------|
| Models | smollm, qwen2:0.5b |
| Protocol | HTTP/1.1 + NDJSON streaming |
| Endpoint | http://localhost:11434/v1 (configurable) |
| Context Window | Up to model limit (e.g., 4096 tokens for qwen2:0.5b) |
| Target Latency | Network-dependent (~50-500ms first token) |
| Peak RAM | Minimal (streaming only) |

### Game Engine & AI System

The CupCake app includes a comprehensive game engine with 14 game types and an advanced AI system with ML-powered bots.

**Game Types (14):**
- **Classic**: TicTacToe (3x3), TicTacToe 4x4, Connect Four, Chess
- **Quick**: Rock Paper Scissors, Guess Number
- **Brain**: Math Quiz, Memory Match, Number Base Conversion
- **Word**: Hangman, Word Scramble
- **Logic**: Mastermind
- **Reflex**: Reaction Time
- **Idle**: Clicker

**AI/ML Bot System:**
- **Q-Learning Models**: Separate models for TicTacToe, Connect Four, TicTacToe 4x4
- **Adaptive Difficulty**: EASY/NORMAL/HARD/CHEATING based on bot's emotional state
- **Jealousy/Anger Tracking**: 0.0-1.0 scale tracking player vs bot win ratios
- **Bot Mood System**: NEUTRAL, COMPETITIVE, JEALOUS, ANGRY, FURIOUS, CONFIDENT

**Cheating Mind System:**
- **Desperation-Based Activation**: Triggers when desperation > 0.75 (high jealousy/anger + consecutive losses)
- **9 Cheating Strategies**: OCCUPY_WINNING_SPOT, BLOCK_AND_WIN, MULTI_PLACE, STEAL_WIN, CHANGE_RULES, FAKE_MISTAKE, TIME_TRAVEL, GHOST_MOVE, PSYCHOLOGICAL_WARFARE
- **Probability Scaling**: Cheating probability scales with desperation level (up to 80%)
- **Cooldown & Detection**: Cooldown between cheats, human suspicion tracking

**LLM Integration for Games:**
- **notifyLLMMove()**: Sends move info to Ollama after EVERY move with jealousy/anger/mood context
- **triggerReaction()**: Includes jealousy%, anger%, mood in LLM prompts for emotional reactions
- **Bot Personality**: Adapts dynamically to mood (furious, jealous, competitive, etc.)

**Points/XP System:**
- **PointsManager**: Tracks XP, levels (exponential curve), achievements
- **Game-Specific Rewards**: Difficulty multipliers (Chess 2x, Connect Four 1.5x, etc.)
- **Achievements**: Streak, milestones, perfect games, polymath (try all games)
- **PointsScreen UI**: Profile tab, Achievements tab, Stats tab with per-game stats

## Data Flow

### Sensor Streaming (ESP32 → Android)

```
ESP32                          Android
  │                               │
  ├─[Connect]────────────────────►│
  │                               │
  ├─[Notify: SensorConfig]───────►│ (BLE GATT Notify)
  │                               │
  ├─[Notify: IMU Data]──────────►│ (50Hz, batched 10 samples)
  │     {ts, ax, ay, az, gx...}   │
  │                               ├─► BleDataSource
  │                               │    │
  │                               │    ▼
  │                               │  SensorRepository
  │                               │    │
  │                               │    ▼
  │                               │  GetSensorDataUseCase
  │                               │    │
  │                               │    ▼
  │                               │  HomeViewModel
  │                               │    │
  │                               │    ▼
  │                               │  UI (Chart)
  │                               │
  │◄──[Write: Command]───────────┤ (BLE GATT Write)
  │    {cmd: "sleep", dur: 5000}  │
```

### Chat Inference (Android Only)

```
User Input
    │
    ▼
ChatViewModel.sendMessage()
    │
    ▼
SendMessageUseCase
    │
    ├─► ChatRepository.saveUserMessage()
    │
    ▼
QwenNative.generateStream(prompt, config)
    │
    ▼ (JNI)
qwen_jni.cpp: Java_com_cupcake_native_QwenNative_generateStream
    │
    ├─► tokenizer.encode(prompt) → tokens[]
    │
    ├─► ggml_context_init(model, params)
    │
    ├─► Loop: while !eos && tokens < max
    │     ├─► ggml_forward(ctx, tokens)
    │     ├─► sampler.sample(logits) → next_token
    │     ├─► tokens.push(next_token)
    │     └─► JNI callback: onToken(token_str)
    │
    ▼ (JNI callback)
QwenNative.onToken() → Flow.emit(token)
    │
    ▼
ChatViewModel.collectTokens() → UI updates
    │
    ▼
ChatRepository.saveAssistantMessage(full_response)
```

## Communication Protocol

### BLE GATT Service

```
Service: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E (Nordic UART compatible)
├── TX Characteristic: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E (Notify)
│   └── ESP32 → Android: Sensor data, status, events
└── RX Characteristic: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E (Write)
    └── Android → ESP32: Commands, config, OTA
```

### Binary Payload (CBOR)

**Sensor Data (Notification):**
```cbor
{
  "type": "sensor",
  "seq": 1234,
  "ts": 1700000000000,
  "imu": {
    "accel": [0.01, -0.02, 9.81],
    "gyro": [0.1, -0.05, 0.02],
    "temp": 23.5
  },
  "battery": 3.72
}
```

**Command (Write):**
```cbor
{
  "type": "cmd",
  "id": "cmd_001",
  "cmd": "sleep",
  "params": {"duration_ms": 5000}
}
```

**Response (Notification):**
```cbor
{
  "type": "resp",
  "id": "cmd_001",
  "status": "ok",
  "data": {}
}
```

## Memory Management

### ESP32 (520KB SRAM + 4MB PSRAM)

| Component | Memory |
|-----------|--------|
| FreeRTOS + ESP-IDF | ~180 KB |
| NimBLE stack | ~60 KB |
| Sensor buffers (2x IMU) | ~8 KB |
| Protocol buffers | ~4 KB |
| NVS cache | ~4 KB |
| **Free** | **~264 KB** (SRAM) |
| PSRAM: OTA buffer, certificates | ~2 MB |

### Android (Native)

| Component | Memory |
|-----------|--------|
| Qwen model (Q4_K_M) | ~400 MB |
| KV Cache (4096 ctx) | ~120 MB |
| GGML workspace | ~50 MB |
| Tokenizer | ~10 MB |
| JNI overhead | ~20 MB |
| **Total** | **~600 MB** |

**Optimization Strategies:**
- Memory-mapped model file (mmap)
- Shared KV cache across requests
- Tensor buffer pooling (GGML arena)
- Release model on background (onTrimMemory)

## Security

### Threat Model

| Asset | Threat | Mitigation |
|-------|--------|------------|
| Sensor data | Eavesdropping | BLE encryption (LE Secure Connections) |
| Commands | Injection | Message authentication (HMAC) |
| OTA firmware | Tampering | Signed images, secure boot |
| User chats | Local access | Android Keystore, encrypted Room |
| API keys | Exposure | Encrypted storage, never logged |

### Implementation

- **BLE**: LE Secure Connections (FIPS), bonding required
- **OTA**: RSA-2048 signed firmware, verified before flash
- **Database**: SQLCipher (Room encryption)
- **Network**: TLS 1.3 for remote endpoints
- **API Keys**: Encrypted in Android Keystore

## Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| BLE connection time | <2s | Android scan + connect |
| Sensor latency (ESP32→UI) | <50ms | Timestamp delta |
| First token latency | <500ms | HTTP request → first chunk |
| Token generation rate | >10 tok/s | Sustained throughput (Ollama) |
| Peak memory (Android) | <100 MB | profilers (streaming only) |
| Battery impact (ESP32) | <5%/day | 24h test |
| APK size | <50 MB | Bundle analyzer |
| Firmware size | <1 MB | .bin size |

## Deployment

### ESP32
```
PlatformIO → pio run -e esp32s3
  ├─ Build: firmware.bin, partitions.bin, bootloader.bin
  ├─ Flash: pio run -t upload (USB) or OTA
  └─ Monitor: pio device monitor
```

### Android
```
./gradlew assembleRelease
  ├─ AAB: app/build/outputs/bundle/release/app-release.aab
  ├─ APK: app/build/outputs/apk/release/app-release.apk
  └─ ProGuard: minifyEnabled true
```

### CI/CD Pipeline

```yaml
# .github/workflows/ci.yml
jobs:
  esp32:
    - Build firmware (PlatformIO)
    - Unit tests (Unity)
    - Static analysis (cppcheck)
  
  android:
    - KTLint + Detekt
    - Unit tests (JUnit + MockK)
    - Instrumented tests (emulator)
    - Build APK + AAB
  
  integration:
    - Hardware-in-loop (if runners available)
    - E2E tests (UI Automator)
  
  release:
    - Sign artifacts
    - Upload to GitHub Releases / Play Console
```

## Future Extensibility

1. **Multi-model support**: Switch between smollm, qwen2:0.5b, and other Ollama models
2. **WiFi Direct / Thread**: Higher bandwidth for sensor data
3. **Voice I/O**: Whisper.cpp + Piper TTS integration
4. **Mesh networking**: ESP32-ESP32 sensor relay
5. **Wear OS companion**: Glanceable sensor data on watch