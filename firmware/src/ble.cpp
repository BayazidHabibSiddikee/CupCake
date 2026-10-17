#include "ble.h"
#include <Arduino.h>

#define NUS_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX_UUID      "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_TX_UUID      "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

static BleLink* self = nullptr;
static BLECharacteristic* g_tx = nullptr;
static std::string g_pending;

static void txNotify(const uint8_t* data, size_t len) {
    g_tx->setValue((uint8_t*)data, len);
    g_tx->notify();
}

static void txNotifyText(const char* text) {
#if defined(CONFIG_IDF_TARGET_ESP32S3)
    g_tx->setValue(const_cast<uint8_t*>((const uint8_t*)text));
#else
    g_tx->setValue(String(text));
#endif
    g_tx->notify();
}

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer*) override { self->deviceConnected = true; }
    void onDisconnect(BLEServer*) override {
        self->deviceConnected = false;
        BLEDevice::startAdvertising();   // let the phone re-connect
    }
};

class RxCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* c) override {
#if defined(CONFIG_IDF_TARGET_ESP32S3)
        std::string value = c->getValue();
#else
        // Plain ESP32 BLE lib returns Arduino String here.
        std::string value = std::string(c->getValue().c_str());
#endif
        if (value.empty()) return;

        // Binary frames (ProtocolDecoder types): ping 0x04 -> pong 0x84,
        // echo the int32 timestamp. Binary-safe (may contain NULs).
        if ((uint8_t)value[0] == 0x04 && value.size() >= 5) {
            uint8_t pong[5] = {0x84, 0, 0, 0, 0};
            memcpy(pong + 1, value.data() + 1, 4);
            txNotify(pong, 5);
            return;
        }
        if (value[0] == '{') g_pending = value;  // parsed in tick()
    }
};

void BleLink::begin(const char* name) {
    self = this;
    BLEDevice::init(name);
    server_ = BLEDevice::createServer();
    server_->setCallbacks(new ServerCallbacks());
    BLEService* svc = server_->createService(NUS_SERVICE_UUID);

    rx_ = svc->createCharacteristic(
        NUS_RX_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
    rx_->setCallbacks(new RxCallbacks());

    tx_ = svc->createCharacteristic(
        NUS_TX_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
    tx_->addDescriptor(new BLE2902());
    g_tx = tx_;

    svc->start();
    BLEAdvertising* adv = BLEDevice::getAdvertising();
    adv->addServiceUUID(NUS_SERVICE_UUID);
    adv->setScanResponse(true);
    BLEDevice::startAdvertising();
}

void BleLink::tick() {
    if (g_pending.empty() || !deviceConnected) return;
    JsonDocument req;
    if (deserializeJson(req, g_pending)) { g_pending.clear(); return; }
    g_pending.clear();

    String id = req["id"].isNull() ? "0" : String(req["id"].as<const char*>());
    String type = req["type"].isNull() ? "" : String(req["type"].as<const char*>());
    String action = req["action"].isNull() ? "" : String(req["action"].as<const char*>());

    // Face / motor short-circuit straight to the hardware.
    if (type == "" && action == "face") {
        if (req["params"]["expression"].is<const char*>() && onFace) {
            onFace(String(req["params"]["expression"].as<const char*>()));
        }
        respond(id, true, JsonObject());
        return;
    }
    if (type == "" && action == "motor") {
        if (req["params"]["action"].is<const char*>() && onMotor) {
            onMotor(String(req["params"]["action"].as<const char*>()));
        }
        respond(id, true, JsonObject());
        return;
    }

    JsonDocument dataDoc;
    JsonObject data = dataDoc.to<JsonObject>();
    if (onCommand) {
        onCommand(type, action, req["params"].as<JsonObjectConst>(), data);
    } else {
        data["ok"] = false;
        data["message"] = "no handler";
    }
    bool ok = data["ok"].isNull() || data["ok"].as<bool>();
    if (!data["ok"].isNull()) data.remove("ok");
    respond(id, ok, data);
}

void BleLink::respond(const String& id, bool ok, JsonObjectConst data) {
    JsonDocument resp;
    resp["type"] = "resp";
    resp["id"] = id;
    resp["status"] = ok ? "ok" : "error";
    resp["data"] = data;
    String out;
    serializeJson(resp, out);
    txNotifyText(out.c_str());
}

void BleLink::notifyBattery(float voltage, float percent) {
    if (!deviceConnected) return;
    uint8_t buf[9];
    buf[0] = 0x02;
    memcpy(buf + 1, &voltage, 4);
    memcpy(buf + 5, &percent, 4);
    txNotify(buf, 9);
}

