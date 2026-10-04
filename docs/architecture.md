# System Architecture

## Overview

CupCake is a hybrid edge-AI system combining an ESP32-S3 microcontroller for sensor acquisition and an Android application for on-device LLM inference using a quantized Qwen model.

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
│  │  Sensors     │                         │  Qwen Model  │     │
│  │  - IMU       │                         │  (INT4 GGUF) │     │
│  │  - Temp      │                         │  - Tokenizer │     │
│  │  - Battery   │                         │  - Inference │     │
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
│                     ANDROID APP (Clean Architecture)           │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      UI LAYER                            │  │
│  │  Compose Screens ──► ViewModels ──► State (Flow/LiveData)│  │
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
│  │  RepositoryImpl ◄── Local (Room) + Remote (BLE)          │  │
│  │  DataSources: BleDataSource, Database, Preferences       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    NATIVE LAYER (JNI)                    │  │
│  │  QwenNative.kt ◄── qwen_jni.cpp ◄── Qwen C++ Engine      │  │
│  │  BleNative.kt  ◄── ble_jni.cpp  ◄── (optional)           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Module Dependencies:**
```
app
├── ui (compose, viewmodel, navigation)
├── domain (usecase, model, repository interfaces)
├── data (repository impl, Room, BLE, DataStore)
├── native (JNI interfaces, NativeLoader)
└── core (di, util, constants) → all modules depend on this
```

### Qwen Inference Engine (C++)

```
┌────────────────────────────────────────────────────────────────┐
│                      QWEN INFERENCE ENGINE                     │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │  Tokenizer   │    │   Model      │    │  Sampler     │     │
│  │  (BPE)       │───►│  (GGML)      │───►│  (top-k,     │     │
│  │  tokenizers- │    │  INT4 GGUF   │    │  top-p, temp)│     │
│  │  cpp         │    │              │    │              │     │
│  └──────────────┘    └──────────────┘    └──────────────┘     │
│         │                   │                   │              │
│         ▼                   ▼                   ▼              │
│  ┌────────────────────────────────────────────────────────┐   │
│  │                  Memory Pool Allocator                  │   │
│  │  - Pre-allocated tensor buffers (no malloc in hot path) │   │
│  │  - Arena allocator for GGML context                     │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                │
│  Thread Model:                                                 │
│  - Main thread: JNI calls, tokenizer                           │
│  - Worker pool: GGML inference (1-4 threads)                   │
│  - Callback thread: Kotlin Flow emission                       │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Model Specifications:**
| Parameter | Value |
|-----------|-------|
| Base Model | Qwen2-0.5B-Instruct |
| Quantization | Q4_K_M (GGUF) |
| Model Size | ~400 MB |
| Context Window | 4096 tokens |
| Vocabulary | 151,936 (BPE) |
| Target Latency | <100ms/token (Snapdragon 8 Gen 2) |
| Peak RAM | ~600 MB (model + KV cache + workspace) |

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
| Model weights | Extraction | Obfuscation, Play Asset Delivery encryption |
| Sensor data | Eavesdropping | BLE encryption (LE Secure Connections) |
| Commands | Injection | Message authentication (HMAC) |
| OTA firmware | Tampering | Signed images, secure boot |
| User chats | Local access | Android Keystore, encrypted Room |

### Implementation

- **BLE**: LE Secure Connections (FIPS), bonding required
- **OTA**: RSA-2048 signed firmware, verified before flash
- **Model**: Encrypted in assets, decrypted at runtime via Keystore
- **Database**: SQLCipher (Room encryption)
- **Network**: None (offline-first), future: TLS 1.3

## Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| BLE connection time | <2s | Android scan + connect |
| Sensor latency (ESP32→UI) | <50ms | Timestamp delta |
| First token latency | <500ms | JNI call → first callback |
| Token generation rate | >10 tok/s | Sustained throughput |
| Peak memory (Android) | <800 MB | profilers |
| Battery impact (ESP32) | <5%/day | 24h test |
| APK size | <150 MB | Bundle analyzer |
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
  ├─ Native libs: stripped, aligned
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
    - Native tests (CMake + CTest)
  
  integration:
    - Hardware-in-loop (if runners available)
    - E2E tests (UI Automator)
  
  release:
    - Sign artifacts
    - Upload to GitHub Releases / Play Console
```

## Future Extensibility

1. **Multi-model support**: Switch between Qwen, Phi, Gemma via model registry
2. **WiFi Direct / Thread**: Higher bandwidth for model updates
3. **Federated learning**: On-device fine-tuning with privacy
4. **Voice I/O**: Whisper.cpp + Piper TTS integration
5. **Mesh networking**: ESP32-ESP32 sensor relay
6. **Wear OS companion**: Glanceable sensor data on watch