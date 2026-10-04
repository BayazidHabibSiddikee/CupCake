# Development Plan

## Phase 1: Foundation (Week 1-2)

### ESP32 Firmware
- [ ] PlatformIO project setup with ESP32-S3 target
- [ ] Basic BLE GATT server (Nordic UART Service compatible)
- [ ] IMU driver (MPU6050/ICM20948) with DMP
- [ ] Battery monitoring + deep sleep
- [ ] NVS configuration storage
- [ ] OTA update framework

### Android App
- [ ] Gradle + CMake + NDK configuration
- [ ] Jetpack Compose UI scaffold (Material3)
- [ ] Room database schema (Device, ChatMessage, SensorReading)
- [ ] Hilt/Dagger DI setup
- [ ] BLE scanning + connection (AndroidX Bluetooth)
- [ ] Basic device list + connection UI

### Infrastructure
- [ ] Git submodules for GGML, tokenizers-cpp
- [ ] CI/CD (GitHub Actions): lint, test, build APK
- [ ] Version catalog (libs.versions.toml)
- [ ] Documentation site (GitHub Pages / MkDocs)

---

## Phase 2: Qwen Integration (Week 3-4)

### C++ Inference Engine
- [ ] Integrate GGML as CMake subdirectory
- [ ] GGUF model loader (qwen-0.5b-q4_k_m.gguf ~400MB)
- [ ] BPE tokenizer (tokenizers-cpp or custom)
- [ ] INT4 quantization support
- [ ] Memory pool allocator (avoid malloc in inference)
- [ ] Thread pool for parallel inference
- [ ] Benchmark: target <2s/token on ESP32-S3, <100ms/token on Snapdragon 8 Gen 2

### JNI Bridge
- [ ] `QwenNative.kt` with suspend functions
- [ ] `qwen_jni.cpp`: init, tokenize, generate, release
- [ ] Async inference with Kotlin Flow callback
- [ ] Error handling: OOM, model not found, token limit
- [ ] Model loading from assets → files directory

### Android Integration
- [ ] Chat screen with streaming tokens
- [ ] Model download / update flow
- [ ] Inference settings (temperature, top-p, max tokens)
- [ ] Token usage / cost tracking

---

## Phase 3: Sensor Fusion & Communication (Week 5-6)

### ESP32 → Android Protocol
- [ ] Binary protocol (CBOR/Protocol Buffers)
- [ ] Sensor data streaming (10-50Hz IMU)
- [ ] Command/response for config
- [ ] Heartbeat / connection health
- [ ] BLE MTU negotiation (517 bytes)

### Android Sensor Pipeline
- [ ] SensorRepository with Flow<SensorReading>
- [ ] Real-time chart (MPAndroidChart / Compose Canvas)
- [ ] Sensor calibration (offset, scale)
- [ ] Background collection (Foreground Service)
- [ ] Data export (CSV, JSON)

### On-Device Processing
- [ ] ESP32: gesture detection (tap, shake, rotate)
- [ ] Android: activity recognition (walk, run, still)
- [ ] Sensor-triggered Qwen prompts (contextual)

---

## Phase 4: Polish & Production (Week 7-8)

### UX/UI
- [ ] Onboarding flow (device pairing, model download)
- [ ] Dark/light theme + dynamic color
- [ ] Accessibility (TalkBack, large text)
- [ ] Animations (shared element, predictive back)

### Reliability
- [ ] Crash reporting (Firebase Crashlytics / Sentry)
- [ ] ANR monitoring
- [ ] BLE reconnection with exponential backoff
- [ ] Model integrity verification (SHA256)
- [ ] Low-memory handling (onTrimMemory)

### Testing
- [ ] Unit tests: repositories, use cases, tokenizer (80%+)
- [ ] Instrumented tests: BLE, Database, JNI
- [ ] E2E: device pair → chat → sensor stream
- [ ] Performance: inference latency, memory, battery
- [ ] Monkey/Stress test (24h)

### Release
- [ ] ProGuard/R8 rules for JNI
- [ ] App Bundle + Play Console setup
- [ ] ESP32 firmware signing
- [ ] Release notes / changelog

---

## Milestones

| Milestone | Target | Criteria |
|-----------|--------|----------|
| M1: Hardware Ready | Week 2 | ESP32 streams IMU over BLE, Android connects |
| M2: Model Runs | Week 4 | Qwen generates tokens on Android via JNI |
| M3: Full Loop | Week 6 | Sensor data → context → Qwen response → UI |
| M4: Production | Week 8 | Play Store ready, signed firmware, docs complete |

---

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Qwen too slow on mobile | Medium | High | Quantize to INT4, use GGML Metal/GPU delegate, smaller model (0.5B) |
| BLE throughput limits | High | Medium | Batch sensor data, compress, WiFi fallback for bulk |
| Model size > Play limit | Low | High | Dynamic delivery (Play Asset Delivery), 150MB limit |
| JNI crashes | Medium | High | Extensive native tests, address sanitizer, fuzzing |
| Thermal throttling | Medium | Medium | Limit inference rate, monitor temp, throttle gracefully |

---

## Team Allocation (if applicable)

| Role | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| Embedded (ESP32) | 1.0 FTE | 0.5 FTE | 0.5 FTE | 0.25 FTE |
| Android (Kotlin) | 1.0 FTE | 0.5 FTE | 0.5 FTE | 0.5 FTE |
| Native (C++/JNI) | 0.25 FTE | 1.0 FTE | 0.5 FTE | 0.25 FTE |
| QA/Testing | 0.25 FTE | 0.5 FTE | 1.0 FTE | 1.0 FTE |

---

## Definition of Done

- [ ] All tests pass (unit + instrumented + E2E)
- [ ] No critical/critical-severity bugs
- [ ] Performance benchmarks met
- [ ] Documentation updated (API, architecture, user guide)
- [ ] Code review approved
- [ ] CI/CD pipeline green
- [ ] Accessibility audit passed