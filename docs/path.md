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
│   ├── qwen-inference.md               # Qwen on-device inference
│   ├── api-gateway.md                  # Custom API/Server integration
│   └── system-prompt.md                # System prompt with image support
├── api/
│   ├── ble-protocol.md                 # BLE communication protocol
│   ├── rest-api.md                     # REST API for custom servers
│   └── websocket-api.md                # WebSocket for streaming
├── models/
│   ├── qwen-config.md                  # Qwen model configuration
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
│   │   │       └── native/
│   │   │           ├── QwenNative.kt           # JNI interface
│   │   │           └── NativeLoader.kt         # System.loadLibrary
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt
│   │   │   ├── jni/
│   │   │   │   ├── qwen_jni.cpp/h              # Qwen JNI bridge
│   │   │   │   ├── jni_common.cpp/h            # Common JNI helpers
│   │   │   │   └── ble_jni.cpp/h               # BLE JNI (optional)
│   │   │   ├── qwen/
│   │   │   │   ├── qwen_model.cpp/h            # Qwen model wrapper
│   │   │   │   ├── tokenizer.cpp/h             # Tokenizer (BPE)
│   │   │   │   ├── inference.cpp/h             # Inference engine
│   │   │   │   ├── quantization.cpp/h          # INT4/INT8 quantization
│   │   │   │   └── ggml_backend.cpp/h          # GGML backend wrapper
│   │   │   ├── utils/
│   │   │   │   ├── logger.cpp/h
│   │   │   │   ├── memory.cpp/h                # Memory pool allocator
│   │   │   │   └── threading.cpp/h             # Thread pool
│   │   │   ├── third_party/
│   │   │   │   ├── ggml/                       # GGML submodule
│   │   │   │   └── tokenizers-cpp/             # Tokenizers submodule
│   │   │   └── test/
│   │   │       ├── test_tokenizer.cpp
│   │   │       └── test_inference.cpp
│   │   ├── jniLibs/
│   │   │   ├── arm64-v8a/
│   │   │   │   ├── libqwen.so
│   │   │   │   └── libggml.so
│   │   │   └── armeabi-v7a/
│   │   │       ├── libqwen.so
│   │   │       └── libggml.so
│   │   └── assets/
│   │       ├── models/
│   │       │   └── qwen-0.5b-q4_k_m.gguf       # Quantized model
│   │       └── tokenizer/
│   │           └── tokenizer.json              # BPE tokenizer
│   ├── test/
│   │   └── java/com/cupcake/
│   │       ├── repository/
│   │       ├── usecase/
│   │       └── native/
│   └── androidTest/
│       └── java/com/cupcake/
│           ├── MainActivityTest.kt
│           └── NativeIntegrationTest.kt
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
    ├── outputs/
    │   ├── apk/debug/app-debug.apk
    │   └── bundle/release/app-release.aab
    └── intermediates/
        └── cxx/Debug/obj/arm64-v8a/libqwen.so
```

## External Dependencies (Git Submodules)
```
third_party/
├── ggml/                                # GGML inference backend
├── tokenizers-cpp/                      # Fast BPE tokenizer
├── nimble-arduino/                      # BLE stack for ESP32
└── protobuf-c/                          # Protocol buffers (if needed)
```

## Key Files Quick Reference

| File | Purpose |
|------|---------|
| `esp/platformio.ini` | ESP32 build config, dependencies, upload settings |
| `app/build.gradle.kts` | Android module config, NDK, CMake, dependencies |
| `app/src/main/cpp/CMakeLists.txt` | Native build, link GGML, Qwen sources |
| `app/src/main/cpp/jni/qwen_jni.cpp` | JNI bridge: Kotlin ↔ C++ Qwen |
| `app/src/main/cpp/qwen/inference.cpp` | Core inference loop |
| `app/src/main/java/com/cupcake/native/QwenNative.kt` | Kotlin JNI declarations |
| `app/src/main/java/com/cupcake/data/model/ChatMessage.kt` | Chat, system prompt, model config models |
| `app/src/main/java/com/cupcake/ui/screen/chat/ChatScreen.kt` | Chat UI with streaming |
| `app/src/main/java/com/cupcake/ui/screen/systemprompt/SystemPromptScreen.kt` | System prompt editor with images |
| `app/src/main/java/com/cupcake/ui/screen/settings/SettingsScreen.kt` | Custom API provider settings |
| `docs/architecture.md` | System design, data flows, component diagram |
| `docs/plan.md` | Phased development roadmap |
| `architecture/component-design/api-gateway.md` | Custom API integration design |
| `architecture/component-design/system-prompt.md` | System prompt with image support |