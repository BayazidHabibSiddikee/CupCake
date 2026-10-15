#include "utils/wifi.h"

#include <ArduinoJson.h>
#include <WiFi.h>

#include "utils/logger.h"

namespace {
constexpr uint32_t kReconnectIntervalMs = 15000;
}  // namespace

void WifiProvisioner::begin() {
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    begun_ = true;
    applyStored_();
}

void WifiProvisioner::loop() {
    if (!begun_ || WiFi.status() == WL_CONNECTED || !hasCredentials()) {
        return;
    }
    const unsigned long now = millis();
    if (now - lastReconnectAttempt_ < kReconnectIntervalMs) {
        return;
    }
    lastReconnectAttempt_ = now;
    LOGI("WiFi: retrying stored network");
    applyStored_();
}

bool WifiProvisioner::hasCredentials() const {
    return !config_.getWifiSsid().isEmpty();
}

bool WifiProvisioner::isConnected() const {
    return WiFi.status() == WL_CONNECTED;
}

void WifiProvisioner::applyStored_() {
    const String ssid = config_.getWifiSsid();
    if (ssid.isEmpty()) {
        return;
    }
    beginStation_(ssid, config_.getWifiPassword());
}

void WifiProvisioner::beginStation_(const String& ssid,
                                    const String& password) {
    LOGI("WiFi: connecting to '%s'", ssid.c_str());
    WiFi.begin(ssid.c_str(), password.c_str());
}

String WifiProvisioner::scanNetworks() {
    // Blocking scan (~1-3s). Hidden networks are included so users can join
    // SSIDs that do not broadcast.
    const int count = WiFi.scanNetworks(false, true);

    JsonDocument doc;
    JsonArray out = doc.to<JsonArray>();
    for (int i = 0; i < count; ++i) {
        JsonObject net = out.add<JsonObject>();
        net["ssid"] = WiFi.SSID(i);
        net["rssi"] = WiFi.RSSI(i);
        net["secure"] = WiFi.encryptionType(i) != WIFI_AUTH_OPEN;
    }
    WiFi.scanDelete();

    String result;
    serializeJson(out, result);
    LOGI("WiFi: scan found %d networks", count);
    return result;
}

bool WifiProvisioner::connect(const String& ssid, const String& password) {
    if (ssid.isEmpty()) {
        return false;
    }
    // Persist immediately so a background retry survives reboots, then let the
    // driver associate asynchronously. The phone polls status() for the result.
    config_.setWifiSsid(ssid);
    config_.setWifiPassword(password);
    beginStation_(ssid, password);
    return true;
}

void WifiProvisioner::forget() {
    WiFi.disconnect(true, true);
    config_.clearWifi();
    LOGI("WiFi: credentials cleared");
}

String WifiProvisioner::stateString() const {
    switch (WiFi.status()) {
        case WL_CONNECTED:
            return "connected";
        case WL_NO_SSID_AVAIL:
            return "no_ssid";
        case WL_CONNECT_FAILED:
            return "failed";
        case WL_DISCONNECTED:
            return hasCredentials() ? "connecting" : "no_credentials";
        case WL_IDLE_STATUS:
            return hasCredentials() ? "connecting" : "no_credentials";
        default:
            return "disconnected";
    }
}

String WifiProvisioner::ip() const {
    return WiFi.localIP().toString();
}

String WifiProvisioner::ssid() const {
    return WiFi.SSID();
}

int WifiProvisioner::rssi() const {
    return WiFi.RSSI();
}
