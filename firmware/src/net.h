#pragma once

/**
 * WiFi station + UDP phone discovery + WebSocket client to the phone's
 * EspWebSocketServer (port 8080, path /esp).
 *
 * Discovery: the phone broadcasts `{"type":"phone","port":8080}` on UDP 44880
 * (network/PhoneBeacon.kt). The board picks up the sender address and caches it
 * in Preferences so it reconnects instantly on reboot even before the beacon.
 *
 * Phone -> board envelopes: {"type":"<name>","payload":"<json-string>",
 * "timestamp":ms}. NOTE payload is a *stringified* JSON object, so it is parsed
 * twice here. Binary frames = raw audio (ignored — no amp on this build).
 *
 * board -> phone replies use the same shape with a real JSON payload string.
 */

#include <WebSocketsClient.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <ArduinoJson.h>
#include <functional>

class Net {
public:
    static constexpr int BEACON_PORT = 44880;

    void begin();
    void tick();

    /** Applies SSID/pass; starts the WiFi link immediately. */
    void wifiConnect(const String& ssid, const String& password);
    void wifiForget();
    /** Returns a WiFi status JSON object: state/ssid/ip/rssi. */
    JsonObject status(JsonDocument& doc);

    bool wsConnected() const { return wsConnected_; }
    bool wifiReady() const { return WiFi.status() == WL_CONNECTED; }
    void send(const String& type, const String& payloadJson);
    void sendStatus();

    std::function<void(const String&)> onFace;    // expression
    std::function<void(const String&)> onMotor;   // action name

private:
    void updateBeacon();
    void connectWs();
    void onWsEvent(WStype_t type, uint8_t* payload, size_t len);
    void handleEnvelope(const String& text);

    WebSocketsClient ws_;
    WiFiUDP udp_;
    String phoneIp_;
    int phonePort_ = 8080;
    bool wsConnected_ = false;
    uint32_t lastBeaconSend_ = 0;
};
