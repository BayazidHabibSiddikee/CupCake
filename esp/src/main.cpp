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

static TaskHandle_t sensorTaskHandle = nullptr;
static TaskHandle_t commTaskHandle = nullptr;

static uint32_t sensorSequence = 0;
static uint32_t lastSensorRead = 0;
static bool sensorStreaming = false;

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
            // Handle OTA data chunk
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
}

void onBLEDisconnect() {
    LOGI("BLE client disconnected");
    sensorStreaming = false;
}

void onBLEWrite(const uint8_t* data, size_t len) {
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

    // Handle sleep if configured
    if (sleep.shouldSleep()) {
        uint32_t duration = config.getSleepDuration();
        LOGI("Auto-sleep for %d ms", duration);
        sleep.deepSleep(duration);
    }

    // Main loop delay
    vTaskDelay(pdMS_TO_TICKS(100));
}