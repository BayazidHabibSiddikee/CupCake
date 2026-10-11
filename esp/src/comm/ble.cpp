#include "comm/ble.h"

#include <Arduino.h>

#include "config.h"
#include "utils/logger.h"

BLEManager::BLEManager() {
    serverCallbacks_.parent = this;
    rxCallbacks_.parent = this;
}

BLEManager::~BLEManager() {
    // NimBLEDevice owns server/service objects; nothing to free here.
}

bool BLEManager::begin(const char* deviceName) {
    NimBLEDevice::init(deviceName);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    server_ = NimBLEDevice::createServer();
    if (!server_) {
        LOGE("Failed to create BLE server");
        return false;
    }
    server_->setCallbacks(&serverCallbacks_);
    setupService();

    NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
    advertising->addServiceUUID(service_->getUUID());
    advertising->setScanResponse(true);
    if (!advertising->start()) {
        LOGE("Failed to start BLE advertising");
        return false;
    }
    LOGI("BLE advertising as '%s'", deviceName);
    return true;
}

void BLEManager::setupService() {
    service_ = server_->createService(BLE_SERVICE_UUID);
    txChar_ = service_->createCharacteristic(BLE_TX_CHAR_UUID,
                                             NIMBLE_PROPERTY::NOTIFY);
    rxChar_ = service_->createCharacteristic(
        BLE_RX_CHAR_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
    rxChar_->setCallbacks(&rxCallbacks_);
    service_->start();
}

void BLEManager::setCallbacks(ConnectCallback onConnect,
                              DisconnectCallback onDisconnect,
                              WriteCallback onWrite) {
    onConnect_ = std::move(onConnect);
    onDisconnect_ = std::move(onDisconnect);
    onWrite_ = std::move(onWrite);
}

void BLEManager::setMtuCallback(MtuCallback onMtuChange) {
    onMtuChange_ = std::move(onMtuChange);
}

bool BLEManager::isConnected() const {
    return connected_;
}

uint16_t BLEManager::getMtu() const {
    return currentMtu_;
}

void BLEManager::notify(const uint8_t* data, size_t len) {
    if (!connected_ || txChar_ == nullptr) {
        return;
    }
    txChar_->setValue(data, len);
    txChar_->notify();
}

void BLEManager::sendResponse(const std::string& id, const std::string& status,
                              const JsonDocument& data) {
    JsonDocument doc;
    doc["type"] = "resp";
    doc["id"] = id;
    doc["status"] = status;
    doc["data"] = data;

    String out;
    serializeJson(doc, out);
    notify(reinterpret_cast<const uint8_t*>(out.c_str()), out.length());
}

void BLEManager::sendResponse(const std::string& id, const std::string& status,
                              const char* message) {
    JsonDocument data;
    data["message"] = message;
    sendResponse(id, status, data);
}

void BLEManager::handle() {
    // NimBLE runs on its own host task; no polling required.
}

// Heartbeat
void BLEManager::sendPing() {
    // Ping message: [type:1][timestamp:4] = 5 bytes
    uint8_t buffer[5];
    buffer[0] = 0x04;  // MSG_TYPE_PING
    uint32_t ts = millis();
    memcpy(buffer + 1, &ts, sizeof(ts));
    notify(buffer, sizeof(buffer));
    LOGD("Sent ping");
}

void BLEManager::handlePong() {
    LOGD("Received pong");
    // Connection health updated in main.cpp
}

// --- Server callbacks -------------------------------------------------------

void BLEManager::ServerCallbacks::onConnect(NimBLEServer* server,
                                            NimBLEConnInfo& connInfo) {
    (void) server;
    (void) connInfo;
    if (parent == nullptr) {
        return;
    }
    parent->connected_ = true;
    parent->currentMtu_ = 23;  // Reset to default
    
    // Request MTU exchange (517 bytes)
    connInfo.requestMtu(517);
    LOGI("BLE client connected, requested MTU 517");
    if (parent->onConnect_) {
        parent->onConnect_();
    }
}

void BLEManager::ServerCallbacks::onDisconnect(NimBLEServer* server,
                                               NimBLEConnInfo& connInfo,
                                               int reason) {
    (void) server;
    (void) connInfo;
    (void) reason;
    if (parent == nullptr) {
        return;
    }
    parent->connected_ = false;
    parent->currentMtu_ = 23;
    LOGI("BLE client disconnected (reason %d)", reason);
    // Resume advertising so another client can connect.
    NimBLEDevice::startAdvertising();
    if (parent->onDisconnect_) {
        parent->onDisconnect_();
    }
}

void BLEManager::ServerCallbacks::onMtuChange(NimBLEServer* server,
                                              NimBLEConnInfo& connInfo,
                                              uint16_t mtu) {
    (void) server;
    (void) connInfo;
    if (parent == nullptr) {
        return;
    }
    parent->currentMtu_ = mtu;
    LOGI("BLE MTU changed to %d", mtu);
    if (parent->onMtuChange_) {
        parent->onMtuChange_(mtu);
    }
}

// --- RX characteristic callbacks --------------------------------------------

void BLEManager::RXCallbacks::onWrite(NimBLECharacteristic* characteristic,
                                      NimBLEConnInfo& connInfo) {
    (void) connInfo;
    if (parent == nullptr || !parent->onWrite_) {
        return;
    }
    const std::string value = characteristic->getValue();
    if (!value.empty()) {
        parent->onWrite_(reinterpret_cast<const uint8_t*>(value.data()),
                         value.size());
    }
}
