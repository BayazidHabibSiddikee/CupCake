#ifndef UTILS_WIFI_H
#define UTILS_WIFI_H

#include <Arduino.h>

#include "utils/config.h"

// WiFi provisioning driven from the phone over BLE.
//
// The companion app connects to the device's Nordic UART Service and sends
// "wifi" control commands (scan / connect / status / forget). Credentials are
// persisted through ConfigManager so the device reconnects on its own.
class WifiProvisioner {
public:
    explicit WifiProvisioner(ConfigManager& config) : config_(config) {}

    // Puts the radio in station mode and reconnects with stored credentials.
    void begin();

    // Non-blocking housekeeping: retries the stored network if dropped.
    void loop();

    bool hasCredentials() const;
    bool isConnected() const;

    // Blocking scan. Returns a JSON array string: [{"ssid","rssi","secure"}].
    String scanNetworks();

    // Starts a connection in the background and persists the credentials.
    // Callers poll status() until the state settles.
    bool connect(const String& ssid, const String& password);

    // Erases stored credentials and disconnects.
    void forget();

    String stateString() const;
    String ip() const;
    String ssid() const;
    int rssi() const;

private:
    void beginStation_(const String& ssid, const String& password);
    void applyStored_();

    ConfigManager& config_;
    bool begun_ = false;
    unsigned long lastReconnectAttempt_ = 0;
};

#endif  // UTILS_WIFI_H
