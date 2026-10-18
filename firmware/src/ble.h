#pragma once

/**
 * BLE Nordic UART (NUS) GATT server — mirrors app/src/main/java/com/cupcake/ble/
 * BleGattClient.kt exactly:
 *   Service: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 *   RX  (phone writes):  6E400002-B5A3-F393-E0A9-E50E24DCCA9E
 *   TX  (we notify):     6E400003-B5A3-F393-E0A9-E50E24DCCA9E
 *
 * Envelope in:  {"id":"..","type":"wifi|info|face|motor|command","action":"..",
 *                "params":{..}}
 * Envelope out: {"type":"resp","id":"..","status":"ok|error","data":{...}}
 *
 * Also emits binary battery notifications: 0x02 + float voltage + float percent
 * (little-endian, must keep matching ProtocolDecoder.BATTERY_PAYLOAD_SIZE = 9).
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include <functional>
#include "auth.h"

class DeviceAuth;

class BleLink {
public:
    /** name is what the phone scans for. */
    void begin(const char* name);
    void tick();
    /** Push current battery over the TX characteristic (called every ~5s). */
    void notifyBattery(float voltage, float percent);

    bool deviceConnected = false;

    /** Handlers wired by main.cpp. */
    std::function<void(const String&)> onFace;    // expression
    std::function<void(const String&)> onMotor;   // action name
    /** wifi/info command dispatch: returns false in data["ok"] on error. */
    std::function<void(const String& type, const String& action,
                       JsonObjectConst params, JsonObject data)> onCommand;

    /** Device-auth guard (optional): all dispatch is gated on pairing. */
    void setAuth(DeviceAuth* auth) { auth_ = auth; }

private:
    BLEServer* server_ = nullptr;
    BLECharacteristic* tx_ = nullptr;
    BLECharacteristic* rx_ = nullptr;
    DeviceAuth* auth_ = nullptr;
    void respond(const String& id, bool ok, JsonObjectConst data);
};
