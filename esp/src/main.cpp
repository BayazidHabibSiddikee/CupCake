/**
 * CupCake ESP32-S3 Firmware
 * Sensor data collection + BLE communication
 */

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Preferences.h>
#include <esp_sleep.h>
#include <esp_task_wdt.h>

#include "config.h"
#include "pinout.h"
#include "sensors/imu.h"
#include "sensors/battery.h"
#include "comm/ble.h"
#include "comm/protocol.h"
#include "power/sleep.h"
#include "power/ota.h"
#include "utils/logger.h"
#include "utils/config.h"
#include "utils/wifi.h"

// =============================================================================
// GLOBAL STATE
// =============================================================================

static IMUSensor imu;
static BatteryMonitor battery;
static BLEManager ble;
static ProtocolHandler protocol;
static SleepManager sleep;
static OTAManager ota;
static ConfigManager config;
static WifiProvisioner wifiProv(config);

static TaskHandle_t sensorTaskHandle = nullptr;
static TaskHandle_t commTaskHandle = nullptr;

static uint32_t sensorSequence = 0;
static uint32_t lastSensorRead = 0;
static bool sensorStreaming = false;

// Heartbeat state
static uint32_t lastPingSent = 0;
static uint32_t lastPongReceived = 0;
static constexpr uint32_t PING_INTERVAL_MS = 10000;  // 10 seconds
static constexpr uint32_t PONG_TIMEOUT_MS = 30000;   // 30 seconds
static bool connectionHealthy = true;

// =============================================================================
// SENSOR TASK (Core 0)
// =============================================================================

void sensorTask(void* parameter) {
    LOGI("Sensor task started on core %d", xPortGetCoreID());

    while (true) {
        // Feed watchdog
        esp_task_wdt_reset();

        // Read sensors at configured interval
        uint32_t now = millis();
        uint32_t interval = config.getSensorInterval();

        if (now - lastSensorRead >= interval) {
            IMUData data;
            if (imu.read(data)) {
                data.timestamp = now;
                data.sequence = sensorSequence++;

                // Send over BLE if connected and streaming
                if (sensorStreaming && ble.isConnected()) {
                    uint8_t buffer[256];
                    size_t len = protocol.encodeSensorData(data, buffer, sizeof(buffer));
                    if (len > 0) {
                        ble.notify(buffer, len);
                    }
                }
            }

            // Battery reading (less frequent)
            static uint32_t lastBatteryRead = 0;
            if (now - lastBatteryRead >= 10000) { // 10s
                float voltage = battery.readVoltage();
                float percentage = battery.readPercentage();
                LOGD("Battery: %.2fV (%.0f%%)", voltage, percentage);

                if (ble.isConnected()) {
                    uint8_t buffer[64];
                    size_t len = protocol.encodeBattery(voltage, percentage, buffer, sizeof(buffer));
                    if (len > 0) {
                        ble.notify(buffer, len);
                    }
                }
                lastBatteryRead = now;
            }

            lastSensorRead = now;
        }

        // Yield to other tasks
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// =============================================================================
// COMMAND HANDLERS
// =============================================================================

void handleCommand(const Command& cmd) {
    LOGI("Received command: %s (id: %s)", cmd.type.c_str(), cmd.id.c_str());

    if (cmd.type == "config") {
        if (cmd.params.containsKey("sensor_interval")) {
            config.setSensorInterval(cmd.params["sensor_interval"]);
            ble.sendResponse(cmd.id, "ok", "Sensor interval updated");
        }
        if (cmd.params.containsKey("sleep_duration")) {
            config.setSleepDuration(cmd.params["sleep_duration"]);
            ble.sendResponse(cmd.id, "ok", "Sleep duration updated");
        }
    }
    else if (cmd.type == "control") {
        if (cmd.action == "stream_start") {
            sensorStreaming = true;
            ble.sendResponse(cmd.id, "ok", "Streaming started");
        }
        else if (cmd.action == "stream_stop") {
            sensorStreaming = false;
            ble.sendResponse(cmd.id, "ok", "Streaming stopped");
        }
        else if (cmd.action == "sleep") {
            uint32_t duration = cmd.params["duration"] | config.getSleepDuration();
            ble.sendResponse(cmd.id, "ok", "Entering sleep");
            vTaskDelay(pdMS_TO_TICKS(100));
            sleep.deepSleep(duration);
        }
        else if (cmd.action == "restart") {
            ble.sendResponse(cmd.id, "ok", "Restarting");
            vTaskDelay(pdMS_TO_TICKS(100));
            ESP.restart();
        }
    }
    else if (cmd.type == "ota") {
        if (cmd.action == "begin") {
            if (ota.begin()) {
                ble.sendResponse(cmd.id, "ok", "OTA ready");
            } else {
                ble.sendResponse(cmd.id, "error", "OTA begin failed");
            }
        }
        else if (cmd.action == "write") {
            // Handle OTA data chunk (params.chunk = raw chunk bytes as string)
            const char* chunk = cmd.params["chunk"] | "";
            size_t written = ota.write(
                reinterpret_cast<const uint8_t*>(chunk), strlen(chunk));
            if (written == strlen(chunk)) {
                ble.sendResponse(cmd.id, "ok", "Chunk written");
            } else {
                ble.sendResponse(cmd.id, "error", "Chunk write failed");
            }
        }
        else if (cmd.action == "end") {
            if (ota.end()) {
                ble.sendResponse(cmd.id, "ok", "OTA complete, rebooting");
                vTaskDelay(pdMS_TO_TICKS(500));
                ESP.restart();
            } else {
                ble.sendResponse(cmd.id, "error", "OTA end failed");
            }
        }
    }
    else if (cmd.type == "wifi") {
        if (cmd.action == "scan") {
            JsonDocument data;
            data["networks"] = wifiProv.scanNetworks();
            ble.sendResponse(cmd.id, "ok", data);
        }
        else if (cmd.action == "connect") {
            const String ssid = cmd.params["ssid"] | "";
            const String password = cmd.params["password"] | "";
            if (ssid.isEmpty()) {
                ble.sendResponse(cmd.id, "error", "Missing ssid");
            } else {
                String ip, err;
                if (wifiProv.connect(ssid, password)) {
                    JsonDocument data;
                    data["message"] = "connected";
                    data["ssid"] = wifiProv.ssid();
                    data["ip"] = wifiProv.ip();
                    ble.sendResponse(cmd.id, "ok", data);
                } else {
                    ble.sendResponse(cmd.id, "error",
                                     "Could not connect to WiFi");
                }
            }
        }
        else if (cmd.action == "status") {
            JsonDocument data;
            data["state"] = wifiProv.stateString();
            data["ssid"] = wifiProv.ssid();
            data["ip"] = wifiProv.ip();
            data["rssi"] = wifiProv.rssi();
            ble.sendResponse(cmd.id, "ok", data);
        }
        else if (cmd.action == "forget") {
            wifiProv.forget();
            ble.sendResponse(cmd.id, "ok", "WiFi credentials cleared");
        }
        else {
            ble.sendResponse(cmd.id, "error", "Unknown wifi action");
        }
    }
    else if (cmd.type == "info") {
        JsonDocument response;
        response["firmware_version"] = FIRMWARE_VERSION;
        response["device_id"] = config.getDeviceId();
        response["sensor_interval"] = config.getSensorInterval();
        response["sleep_duration"] = config.getSleepDuration();
        response["battery_voltage"] = battery.readVoltage();
        response["free_heap"] = ESP.getFreeHeap();
        response["psram_size"] = ESP.getPsramSize();

        ble.sendResponse(cmd.id, "ok", response);
    }
}

// =============================================================================
// BLE CALLBACKS
// =============================================================================

void onBLEConnect() {
    LOGI("BLE client connected");
    sensorStreaming = false; // Start paused
    // Reset heartbeat state
    lastPingSent = 0;
    lastPongReceived = millis();
    connectionHealthy = true;
}

void onBLEDisconnect() {
    LOGI("BLE client disconnected");
    sensorStreaming = false;
    connectionHealthy = false;
}

void onBLEWrite(const uint8_t* data, size_t len) {
    // Check for PONG response first
    uint32_t pongTimestamp;
    if (protocol.decodePong(data, len, pongTimestamp)) {
        lastPongReceived = millis();
        connectionHealthy = true;
        ble.handlePong();
        LOGD("Received pong, timestamp: %u", pongTimestamp);
        return;
    }
    
    // Check for PING request (from client)
    if (len >= 5 && data[0] == ProtocolHandler::MSG_TYPE_PING) {
        // Respond with PONG
        uint32_t pingTimestamp;
        memcpy(&pingTimestamp, data + 1, sizeof(pingTimestamp));
        uint8_t buffer[5];
        size_t pongLen = protocol.encodePong(pingTimestamp, buffer, sizeof(buffer));
        if (pongLen > 0) {
            ble.notify(buffer, pongLen);
        }
        LOGD("Received ping, sent pong");
        return;
    }

    // Handle regular commands
    Command cmd;
    if (protocol.decodeCommand(data, len, cmd)) {
        handleCommand(cmd);
    } else {
        LOGW("Failed to decode command");
    }
}

// =============================================================================
// SETUP
// =============================================================================

void setup() {
    // Serial for debugging
    Serial.begin(115200);
    delay(100);
    LOGI("=== CupCake ESP32-S3 Firmware v%s ===", FIRMWARE_VERSION);

    // Initialize config
    config.begin();

    // Initialize WiFi provisioning (reconnects stored network if present)
    wifiProv.begin();

    // Initialize I2C
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    Wire.setClock(400000);

    // Initialize IMU
    if (!imu.begin()) {
        LOGE("IMU initialization failed!");
    } else {
        LOGI("IMU initialized");
    }

    // Initialize battery monitor
    battery.begin(BATTERY_ADC_PIN, BATTERY_R1, BATTERY_R2);

    // Initialize BLE
    ble.begin(DEVICE_NAME);
    ble.setCallbacks(onBLEConnect, onBLEDisconnect, onBLEWrite);

    // Initialize sleep manager
    sleep.begin();

    // Initialize OTA
    ota.begin();

    // Enable task watchdog
    esp_task_wdt_config_t twdt_config = {
        .timeout_ms = 5000,
        .idle_core_mask = (1 << 0) | (1 << 1),
        .trigger_panic = true
    };
    esp_task_wdt_init(&twdt_config);
    esp_task_wdt_add(nullptr);

    // Create sensor task (Core 0)
    xTaskCreatePinnedToCore(
        sensorTask,
        "sensor_task",
        4096,
        nullptr,
        5,
        &sensorTaskHandle,
        0
    );

    // Create communication task (Core 1) - handles BLE events
    // Note: NimBLE runs its own task, but we can pin it
    LOGI("Setup complete, free heap: %d", ESP.getFreeHeap());
}

// =============================================================================
// LOOP
// =============================================================================

void loop() {
    // Feed watchdog
    esp_task_wdt_reset();

    // Handle OTA
    ota.handle();

    // Keep WiFi provisioning alive (retry stored network)
    wifiProv.loop();

    // Handle sleep if configured
    if (sleep.shouldSleep()) {
        uint32_t duration = config.getSleepDuration();
        LOGI("Auto-sleep for %d ms", duration);
        sleep.deepSleep(duration);
    }

    // Main loop delay
    vTaskDelay(pdMS_TO_TICKS(100));
}