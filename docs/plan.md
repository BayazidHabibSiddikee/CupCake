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
- [ ] Gradle configuration (no NDK/CMake needed)
- [ ] Jetpack Compose UI scaffold (Material3)
- [ ] Room database schema (Device, ChatMessage, SensorReading)
- [ ] Hilt/Dagger DI setup
- [ ] BLE scanning + connection (AndroidX Bluetooth)
- [ ] Basic device list + connection UI

### Infrastructure
- [ ] CI/CD (GitHub Actions): lint, test, build APK
- [ ] Version catalog (libs.versions.toml)
- [ ] Documentation site (GitHub Pages / MkDocs)

---

## Phase 2: Ollama Integration (Week 3-4)

### Ollama HTTP Client
- [ ] `OllamaEngine.kt` with OkHttp SSE/NDJSON streaming
- [ ] OpenAI-compatible API support (vLLM, TGI, etc.)
- [ ] Streaming token emission via Kotlin Flow
- [ ] Connection pooling and timeout configuration
- [ ] Error handling: network, model not found, token limit
- [ ] Model list fetching from Ollama server

### Android Integration
- [ ] Chat screen with streaming tokens
- [ ] Model selection (smollm, qwen2:0.5b, custom)
- [ ] Inference settings (temperature, top-p, max tokens)
- [ ] Token usage / cost tracking
- [ ] Custom endpoint configuration

---
 
## Phase 3: Games & AI Features (Week 5-6) ✅ COMPLETED
 
### Game Engine Implementation
- [x] GameManager with sealed Game interface and 14 game types
- [x] TicTacToe (3x3 & 4x4), Connect Four, Chess, Rock Paper Scissors
- [x] Guess Number, Math Quiz, Memory Match, Hangman, Word Scramble
- [x] Number Base Conversion, Reaction Time, Clicker, Mastermind
- [x] Game UI screens with Compose (GameScreen, GameViewModel)
 
### ML/AI Game Engine
- [x] Q-Learning ML models for TicTacToe, Connect Four, TicTacToe 4x4
- [x] Adaptive difficulty (EASY/NORMAL/HARD/CHEATING) based on jealousy/anger
- [x] Jealousy/Anger tracking system (0.0-1.0 scale)
- [x] Bot mood system (NEUTRAL, COMPETITIVE, JEALOUS, ANGRY, FURIOUS, CONFIDENT)
 
### Cheating Mind System
- [x] Desperation-based cheating mind (activates at >0.75 desperation)
- [x] 9 cheating strategies: OCCUPY_WINNING_SPOT, BLOCK_AND_WIN, MULTI_PLACE, STEAL_WIN, CHANGE_RULES, FAKE_MISTAKE, TIME_TRAVEL, GHOST_MOVE, PSYCHOLOGICAL_WARFARE
- [x] Cheating probability scales with desperation level
- [x] Cooldown, cheat count tracking, human suspicion detection
 
### LLM Integration for Games
- [x] notifyLLMMove() - sends move info to LLM after EVERY move
- [x] Jealousy/anger/mood context in LLM prompts
- [x] triggerReaction() includes jealousy/anger/mood in prompts
- [x] Bot personality adapts to mood (furious, jealous, competitive, etc.)
 
### Points/XP System
- [x] PointsManager with XP, levels, achievements
- [x] Game-specific point rewards with difficulty multipliers
- [x] Streak achievements, milestone achievements, perfect game achievements
- [x] PointsScreen UI with profile, achievements, stats tabs
 
---
 
## Phase 4: Sensor Fusion & Communication (Week 7-8)
 
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
- [ ] Sensor-triggered LLM prompts (contextual)

---

## Phase 4: Polish & Production (Week 7-8)

### UX/UI
- [ ] Onboarding flow (device pairing, Ollama setup guide)
- [ ] Dark/light theme + dynamic color
- [ ] Accessibility (TalkBack, large text)
- [ ] Animations (shared element, predictive back)

### Reliability
- [ ] Crash reporting (Firebase Crashlytics / Sentry)
- [ ] ANR monitoring
- [ ] BLE reconnection with exponential backoff
- [ ] Low-memory handling (onTrimMemory)

### Testing
- [ ] Unit tests: repositories, use cases, API client (80%+)
- [ ] Instrumented tests: BLE, Database
- [ ] E2E: device pair → chat → sensor stream
- [ ] Performance: inference latency, memory, battery
- [ ] Monkey/Stress test (24h)

### Release
- [ ] ProGuard/R8 rules
- [ ] App Bundle + Play Console setup
- [ ] ESP32 firmware signing
- [ ] Release notes / changelog

---

## Milestones

| Milestone | Target | Criteria |
|-----------|--------|----------|
| M1: Hardware Ready | Week 2 | ESP32 streams IMU over BLE, Android connects |
| M2: Ollama Connected | Week 4 | LLM generates tokens via Ollama HTTP API |
| M3: Full Loop | Week 6 | Sensor data → context → LLM response → UI |
| M4: Production | Week 8 | Play Store ready, signed firmware, docs complete |

---

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Ollama server unavailable | Medium | High | Offline queue, cached responses, clear error UX |
| BLE throughput limits | High | Medium | Batch sensor data, compress, WiFi fallback for bulk |
| Network latency | Medium | Medium | Streaming UI, timeout config, local fallback |
| Model not pulled on Ollama | Medium | High | Auto-pull guidance, model list refresh |

---

## Team Allocation (if applicable)

| Role | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| Embedded (ESP32) | 1.0 FTE | 0.5 FTE | 0.5 FTE | 0.25 FTE |
| Android (Kotlin) | 1.0 FTE | 1.0 FTE | 0.5 FTE | 0.5 FTE |
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