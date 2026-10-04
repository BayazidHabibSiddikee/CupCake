#ifndef BLE_H
#define BLE_H

#include <NimBLEDevice.h>
#include <functional>
#include <string>
#include <vector>

#include "protocol.h"

class BLEManager {
public:
    using ConnectCallback = std::function<void()>;
    using DisconnectCallback = std::function<void()>;
    using WriteCallback = std::function<void(const uint8_t*, size_t)>;

    BLEManager();
    ~BLEManager();

    bool begin(const char* deviceName);
    void setCallbacks(ConnectCallback onConnect,
                      DisconnectCallback onDisconnect,
                      WriteCallback onWrite);

    bool isConnected() const;
    void notify(const uint8_t* data, size_t len);
    void sendResponse(const std::string& id, const std::string& status, const JsonDocument& data);
    void sendResponse(const std::string& id, const std::string& status, const char* message);

    void handle();

private:
    NimBLEServer* server_ = nullptr;
    NimBLEService* service_ = nullptr;
    NimBLECharacteristic* txChar_ = nullptr;
    NimBLECharacteristic* rxChar_ = nullptr;

    ConnectCallback onConnect_;
    DisconnectCallback onDisconnect_;
    WriteCallback onWrite_;

    bool connected_ = false;

    class ServerCallbacks : public NimBLEServerCallbacks {
    public:
        BLEManager* parent;
        void onConnect(NimBLEServer* server, NimBLEConnInfo& connInfo) override;
        void onDisconnect(NimBLEServer* server, NimBLEConnInfo& connInfo, int reason) override;
    } serverCallbacks_;

    class RXCallbacks : public NimBLECharacteristicCallbacks {
    public:
        BLEManager* parent;
        void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo& connInfo) override;
    } rxCallbacks_;

    void setupService();
};

#endif // BLE_H