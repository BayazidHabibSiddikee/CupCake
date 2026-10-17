#include "net.h"
#include <Preferences.h>

static Net* self = nullptr;
static Preferences prefs;

void Net::begin() {
    self = this;
    prefs.begin("cupcake_net", true);
    String ssid = prefs.getString("ssid", "");
    String pass = prefs.getString("pass", "");
    phoneIp_ = prefs.getString("phone_ip", "");
    prefs.end();
    if (ssid.length()) wifiConnect(ssid, pass);
    // NOTE: udp_.begin() only AFTER the WiFi/lwIP stack is up — opening it
    // beforehand crashes lwIP (null sys-mutex assert).
}

/** One-shot: bind the UDP beacon socket once WiFi/lwIP is ready. */
static bool udpUp_ = false;

void Net::ensureUdp() {
    if (udpUp_ || WiFi.status() != WL_CONNECTED) return;
    if (udp_.begin(BEACON_PORT)) udpUp_ = true;
}

bool Net::udpReady() const { return udpUp_; }

void Net::wifiConnect(const String& ssid, const String& password) {
    prefs.begin("cupcake_net", false);
    prefs.putString("ssid", ssid);
    prefs.putString("pass", password);
    prefs.end();
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid.c_str(), password.c_str());
}

void Net::wifiForget() {
    prefs.begin("cupcake_net", false);
    prefs.clear();
    prefs.end();
    WiFi.disconnect(true, true);
    if (wsConnected_) {
        ws_.disconnect();
        wsConnected_ = false;
    }
}

JsonObject Net::status(JsonDocument& doc) {
    JsonObject o = doc.to<JsonObject>();
    wl_status_t s = WiFi.status();
    String state = s == WL_CONNECTED ? "connected" : s == WL_IDLE_STATUS ? "connecting" : "failed";
    o["state"] = state;
    o["ssid"] = WiFi.SSID();
    o["ip"] = WiFi.localIP().toString();
    o["rssi"] = WiFi.RSSI();
    return o;
}

void Net::send(const String& type, const String& payloadJson) {
    if (!wsConnected_) return;
    JsonDocument env;
    env["type"] = type;
    env["payload"] = payloadJson;
    env["timestamp"] = (uint32_t)(millis());
    String out;
    serializeJson(env, out);
    ws_.sendTXT(out);
}

void Net::sendStatus() {
    JsonDocument d;
    JsonObject o = status(d);
    String out;
    serializeJson(o, out);
    send("status", out);
}

// ----------------------- UDP phone discovery --------------------------------

void Net::tick() {
    updateBeacon();
    ws_.loop();
}

void Net::updateBeacon() {
    ensureUdp();
    if (millis() - lastBeaconSend_ < 300) return;
    lastBeaconSend_ = millis();
    if (!udpReady()) return;

    int size = udp_.parsePacket();
    if (size <= 0) return;
    char buf[128];
    int len = udp_.read(buf, sizeof(buf) - 1);
    buf[len] = 0;
    IPAddress sender = udp_.remoteIP();

    JsonDocument doc;
    if (!deserializeJson(doc, buf, len)) return;
    if (String(doc["type"].as<const char*>()) != "phone") return;

    int advertPort = doc["port"].isNull() ? 8080 : doc["port"].as<int>();
    String last = phoneIp_;
    phoneIp_ = sender.toString();
    phonePort_ = advertPort;
    if (last != phoneIp_) {
        prefs.begin("cupcake_net", false);
        prefs.putString("phone_ip", phoneIp_);
        prefs.end();
    }
    if (!wsConnected_) connectWs();
}

// --------------------------- WebSocket client --------------------------------

void Net::connectWs() {
    if (phoneIp_.isEmpty() || !wifiReady() || wsConnected_) return;
    ws_.begin(phoneIp_, phonePort_, "/esp");
    ws_.onEvent([](WStype_t type, uint8_t* payload, size_t len) {
        if (self) self->onWsEvent(type, payload, len);
    });
    ws_.setReconnectInterval(3000);
}

void Net::onWsEvent(WStype_t type, uint8_t* payload, size_t len) {
    switch (type) {
        case WStype_CONNECTED:
            wsConnected_ = true;
            sendStatus();
            break;
        case WStype_DISCONNECTED:
            wsConnected_ = false;
            break;
        case WStype_TEXT:
            handleEnvelope(String((char*)payload).substring(0, len));
            break;
        default:
            break;   // binary audio ignored on this build (no amp)
    }
}

void Net::handleEnvelope(const String& text) {
    JsonDocument doc;
    if (deserializeJson(doc, text)) return;
    String type = doc["type"].isNull() ? "" : String(doc["type"].as<const char*>());

    if (type == "face") {
        String payload = doc["payload"].isNull() ? "" : String(doc["payload"].as<const char*>());
        JsonDocument p;
        if (!deserializeJson(p, payload)) {
            if (onFace) onFace(String(p["expression"].as<const char*>()));
        }
    } else if (type == "motor") {
        String payload = doc["payload"].isNull() ? "" : String(doc["payload"].as<const char*>());
        JsonDocument p;
        if (!deserializeJson(p, payload)) {
            if (onMotor) onMotor(String(p["action"].as<const char*>()));
        }
    } else if (type == "ping") {
        JsonDocument pong;
        pong["type"] = "pong";
        pong["payload"] = "{}";
        pong["timestamp"] = (uint32_t)millis();
        String out;
        serializeJson(pong, out);
        ws_.sendTXT(out);
    }
}
