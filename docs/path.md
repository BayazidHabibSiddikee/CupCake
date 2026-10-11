# Project Path Structure

## Root Directory
```
/home/sword/Documents/android/CupCake/
```

## Architecture Documentation (`architecture/`)
```
architecture/
├── README.md                           # Architecture overview
├── system-overview.md                  # High-level system architecture
├── data-flow.md                        # Data flow diagrams
├── component-design/
│   ├── esp32-firmware.md               # ESP32 firmware design
│   ├── android-app.md                  # Android app architecture
│   ├── ollama-inference.md             # Ollama HTTP API inference
│   ├── api-gateway.md                  # Custom API/Server integration
│   └── system-prompt.md                # System prompt with image support
├── api/
│   ├── ble-protocol.md                 # BLE communication protocol
│   ├── rest-api.md                     # REST API for custom servers
│   └── websocket-api.md                # WebSocket for streaming
├── models/
│   ├── ollama-models.md                # Ollama model configuration
│   ├── custom-model.md                 # Custom model integration
│   └── system-prompt.md                # System prompt schema
└── security/
    ├── threat-model.md                 # Threat analysis
    ├── data-protection.md              # Encryption, keys
    └── api-security.md                 # API authentication
```

## ESP32 Firmware (`esp/`)
```
esp/
├── platformio.ini                      # PlatformIO configuration
├── src/
│   ├── main.cpp                        # Entry point
│   ├── sensors/
│   │   ├── imu.cpp/h                   # IMU (MPU6050/ICM20948) driver
│   │   ├── temperature.cpp/h           # Temperature sensor
│   │   └── battery.cpp/h               # Battery monitoring
│   ├── comm/
│   │   ├── ble.cpp/h                   # BLE GATT server/client
│   │   ├── wifi.cpp/h                  # WiFi connection manager
│   │   └── protocol.cpp/h              # Binary protocol encoding
│   ├── power/
│   │   ├── sleep.cpp/h                 # Deep/light sleep management
│   │   └── ota.cpp/h                   # OTA update handler
│   └── utils/
│       ├── logger.cpp/h                # Logging utility
│       └── config.cpp/h                # NVS configuration storage
├── include/
│   ├── config.h                        # Build-time configuration
│   ├── pinout.h                        # GPIO pin definitions
│   └── version.h                       # Firmware version
├── lib/                                # External libraries (git submodules)
│   ├── ArduinoJson/
│   ├── NimBLE-Arduino/
│   └── zlib/
└── test/
    ├── test_sensors.cpp
    ├── test_comm.cpp
    └── test_power.cpp
```

## Android App (`app/`)
```
app/
├── build.gradle.kts                    # Module-level Gradle config
├── proguard-rules.pro                  # ProGuard rules
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/
│   │   │   └── com/cupcake/
│   │   │       ├── CupCakeApplication.kt
│   │   │       ├── MainActivity.kt
│   │   │       ├── ui/
│   │   │       │   ├── theme/
│   │   │       │   ├── screen/
│   │   │       │   │   ├── home/
│   │   │       │   │   ├── device/
│   │   │       │   │   ├── chat/
│   │   │       │   │   │   ├── ChatScreen.kt
│   │   │       │   │   │   └── ChatViewModel.kt
│   │   │       │   │   ├── settings/
│   │   │       │   │   │   ├── SettingsScreen.kt
│   │   │       │   │   │   └── SettingsViewModel.kt
│   │   │       │   │   └── systemprompt/
│   │   │       │   │       ├── SystemPromptScreen.kt
│   │   │       │   │       └── SystemPromptViewModel.kt
│   │   │       │   └── component/
│   │   │       │       ├── ChatComponents.kt
│   │   │       │       ├── MessageInput.kt
│   │   │       │       └── ImagePickerSheet.kt
│   │   │       ├── data/
│   │   │       │   ├── model/
│   │   │       │   │   ├── ChatMessage.kt
│   │   │       │   │   ├── Conversation.kt
│   │   │       │   │   ├── SystemPrompt.kt
│   │   │       │   │   ├── ModelConfig.kt
│   │   │       │   │   └── ApiProvider.kt
│   │   │       │   ├── repository/
│   │   │       │   │   ├── ChatRepository.kt
│   │   │       │   │   ├── SystemPromptRepository.kt
│   │   │       │   │   └── ModelRepository.kt
│   │   │       │   ├── source/
│   │   │       │   │   ├── local/
│   │   │       │   │   │   ├── AppDatabase.kt
│   │   │       │   │   │   ├── ChatDao.kt
│   │   │       │   │   │   └── converters/
│   │   │       │   │   │       └── Converters.kt
│   │   │       │   │   └── remote/
│   │   │       │   │       ├── ApiServices.kt
│   │   │       │   │       └── ApiClient.kt
│   │   │       │   └── repository/
│   │   │       │       └── RepositoryImpl.kt
│   │   │       ├── domain/
│   │   │       │   ├── usecase/
│   │   │       │   │   ├── ChatUseCases.kt
│   │   │       │   │   ├── SystemPromptUseCases.kt
│   │   │       │   │   └── ModelUseCases.kt
│   │   │       │   └── repository/
│   │   │       │       ├── ChatRepository.kt
│   │   │       │       ├── SystemPromptRepository.kt
│   │   │       │       └── ModelRepository.kt
│   │   │       ├── di/
│   │   │       │   └── AppModule.kt
│   │   │       ├── ai/
│   │   │       │   ├── OllamaEngine.kt          # Ollama HTTP streaming client
│   │   │       │   ├── CharacterManager.kt
│   │   │       │   └── EnergyManager.kt
│   │   │       └── game/
│   │   │           └── GameManager.kt
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt          # Minimal - no native inference
│   │   │   ├── test/
│   │   │   └── utils/
│   │   └── assets/
│   │       └── images/                  # App icons, placeholders
│   ├── test/
│   │   └── java/com/cupcake/
│   │       ├── repository/
│   │       ├── usecase/
│   │       └── data/
│   └── androidTest/
│       └── java/com/cupcake/
│           └── MainActivityTest.kt
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── gradle.properties
├── settings.gradle.kts
└── local.properties                     # SDK/NDK paths (gitignored)
```

## Documentation (`docs/`)
```
docs/
├── architecture.md                      # System architecture
├── path.md                              # This file
├── plan.md                              # Development plan
├── api/
│   ├── ble-protocol.md                  # BLE protocol specification
│   ├── qwen-api.md                      # Qwen inference API
│   └── sensor-api.md                    # Sensor data format
└── hardware/
    ├── esp32-pinout.md                  # ESP32 pin assignments
    ├── schematic.pdf                    # PCB schematic
    └── bom.md                           # Bill of materials
```

## Build Outputs
```
build/
├── esp/
│   ├── firmware.bin
│   ├── partitions.bin
│   └── bootloader.bin
└── app/
    └── outputs/
        ├── apk/debug/app-debug.apk
        └── bundle/release/app-release.aab
```

## External Dependencies (Git Submodules)
```
third_party/
├── nimble-arduino/                      # BLE stack for ESP32
└── protobuf-c/                          # Protocol buffers (if needed)
```

## Key Files Quick Reference

| File | Purpose |
|------|---------|
| `esp/platformio.ini` | ESP32 build config, dependencies, upload settings |
| `app/build.gradle.kts` | Android module config, dependencies (no NDK/CMake) |
| `app/src/main/java/com/cupcake/ai/OllamaEngine.kt` | Ollama HTTP streaming client |
| `app/src/main/java/com/cupcake/data/model/ChatMessage.kt` | Chat, system prompt, model config models |
| `app/src/main/java/com/cupcake/ui/screen/chat/ChatScreen.kt` | Chat UI with streaming |
| `app/src/main/java/com/cupcake/ui/screen/systemprompt/SystemPromptScreen.kt` | System prompt editor with images |
| `app/src/main/java/com/cupcake/ui/screen/settings/SettingsScreen.kt` | Custom API provider settings |
| `docs/architecture.md` | System design, data flows, component diagram |
| `docs/plan.md` | Phased development roadmap |
| `architecture/component-design/api-gateway.md` | Custom API integration design |
| `architecture/component-design/system-prompt.md` | System prompt with image support |