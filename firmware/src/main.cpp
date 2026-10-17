#include <Arduino.h>
#include <ArduinoJson.h>
#include "face.h"
#include "motor.h"
#include "ble.h"
#include "net.h"

static constexpr const char* FW_NAME = "CupCake-Companion";
static constexpr const char* FW_VERSION = "0.1.0";

Face face;
Motors motors;
BleLink ble;
Net net;

// Battery sense: battery+ -> 2x 100k divider -> GPIO2 (ADC1_CH2).
// 3.0-4.2 V maps to 1.5-2.1 V, safe under the 3.3 V ADC reference.
static constexpr int PIN_BATTERY = 2;
static constexpr float ADC_SCALE = 2.0f;      // divider ratio
static constexpr float BATTERY_FULL = 4.2f;
static constexpr float BATTERY_EMPTY = 3.3f;

static float batteryVoltage() {
    uint32_t acc = 0;
    for (int i = 0; i < 8; i++) acc += analogReadMilliVolts(PIN_BATTERY);
    float mv = (acc / 8.0f) * ADC_SCALE;
    return mv / 1000.0f;
}

static float batteryPercent() {
    float pct = (batteryVoltage() - BATTERY_EMPTY) / (BATTERY_FULL - BATTERY_EMPTY) * 100.0f;
    return pct < 0 ? 0 : pct > 100 ? 100 : pct;
}

// ------------------------- BLE command handler -------------------------------
// Mirrors the app-side WifiProvisioner / DeviceSetup commands.

static void handleCommand(const String& type, const String& action,
                          JsonObjectConst params, JsonObject data) {
    if (type == "wifi") {
        if (action == "scan") {
            int n = WiFi.scanNetworks();
            JsonDocument nets;
            JsonArray arr = nets.to<JsonArray>();
            for (int i = 0; i < n; i++) {
                JsonObject neto = arr.add<JsonObject>();
                neto["ssid"] = WiFi.SSID(i);
                neto["rssi"] = WiFi.RSSI(i);
                neto["secure"] = WiFi.encryptionType(i) != WIFI_AUTH_OPEN;
            }
            String list;
            serializeJson(nets, list);          // WifiProvisioner expects a JSON *string*
            data["networks"] = list;
        } else if (action == "connect") {
            net.wifiConnect(params["ssid"].as<const char*>(), params["password"].as<const char*>());
            data["state"] = "connecting";
            data["ssid"] = params["ssid"].as<const char*>();
            data["ip"] = "";
            data["rssi"] = 0;
        } else if (action == "status") {
            JsonDocument d;
            JsonObject st = net.status(d);
            data["state"] = st["state"].as<String>();
            data["ssid"] = st["ssid"].as<String>();
            data["ip"] = st["ip"].as<String>();
            data["rssi"] = st["rssi"].as<int>();
        } else if (action == "forget") {
            net.wifiForget();
        } else {
            data["ok"] = false;
            data["message"] = "unknown wifi action";
        }
    } else if (type == "info") {
        data["name"] = FW_NAME;
        data["fw"] = FW_VERSION;
        data["ws"] = net.wsConnected();
        data["batteryPct"] = batteryPercent();
    } else {
        data["ok"] = false;
        data["message"] = "unsupported command type";
    }
}

void setup() {
    face.begin();
    motors.begin();

    net.onFace = [](const String& expression) { face.set(expression); };
    net.onMotor = [](const String& action) { motors.execute(action); };

    ble.onFace = [](const String& expression) { face.set(expression); };
    ble.onMotor = [](const String& action) { motors.execute(action); };
    ble.onCommand = [](const String& type, const String& action,
                       JsonObjectConst params, JsonObject data) {
        handleCommand(type, action, params, data);
    };

    ble.begin(FW_NAME);
    net.begin();
}

void loop() {
    ble.tick();
    net.tick();
    face.tick();
    motors.tick();

    // Battery telemetry over the existing BLE battery frame every 5 s.
    if (millis() - lastBatterySend_ > 5000) {
        lastBatterySend_ = millis();
        ble.notifyBattery(batteryVoltage(), batteryPercent());
    }
}
