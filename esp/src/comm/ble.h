#ifndef BLE_H
#define BLE_H

#include <NimBLEDevice.h>
#include <functional>
#include <string>
#include <vector>

#include "comm/protocol.h"

class BLEManager {
public:
    using ConnectCallback = std::function<void()>;
    using DisconnectCallback = std::function<void()>;
    using WriteCallback = std::function<void(const uint8_t*, size_t)>;
    using MtuCallback = std::function<void(uint16_t mtu)>;

    BLEManager();
    ~BLEManager();

    bool begin(const char* deviceName);
    void setCallbacks(ConnectCallback onConnect,
                      DisconnectCallback onDisconnect,
                      WriteCallback onWrite);
    void setMtuCallback(MtuCallback onMtuChange);

    bool isConnected() const;
    uint16_t getMtu() const;
    void notify(const uint8_t* data, size_t len);
    void sendResponse(const std::string& id, const std::string& status, const JsonDocument& data);
    void sendResponse(const std::string& id, const std::string& status, const char* message);

    void handle();

    // Heartbeat
    void sendPing();
    void handlePong();

private:
    NimBLEServer* server_ = nullptr;
    NimBLEService* service_ = nullptr;
    NimBLECharacteristic* txChar_ = nullptr;
    NimBLECharacteristic* rxChar_ = nullptr;

    ConnectCallback onConnect_;
    DisconnectCallback onDisconnect_;
    WriteCallback onWrite_;
    MtuCallback onMtuChange_;

    bool connected_ = false;
    uint16_t currentMtu_ = 23;  // Default BLE MTU

    class ServerCallbacks : public NimBLEServerCallbacks {
    public:
        BLEManager* parent;
        void onConnect(NimBLEServer* server, NimBLEConnInfo& connInfo) override;
        void onDisconnect(NimBLEServer* server, NimBLEConnInfo& connInfo, int reason) override;
        void onMtuChange(NimBLEServer* server, NimBLEConnInfo& connInfo, uint16_t mtu) override;
    } serverCallbacks_;

    class RXCallbacks : public NimBLECharacteristicCallbacks {
    public:
        BLEManager* parent;
        void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo& connInfo) override;
    } rxCallbacks_;

    void setupService();
};

#endif // BLE_H