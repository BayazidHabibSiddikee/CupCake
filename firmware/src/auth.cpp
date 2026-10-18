#include "auth.h"
#include "esp_mac.h"

void DeviceAuth::begin() {
    // mint a stable device id once: 6 hex chars from the efuse MAC
    uint8_t mac[6];
    esp_efuse_mac_get_default(mac);
    char id[9];
    snprintf(id, sizeof(id), "%02X%02X%02X", mac[3], mac[4], mac[5]);
    String candidate = String(id);

    prefs_.begin("cupcake_auth", true);
    deviceId_ = prefs_.getString("device_id", "");
    token_ = prefs_.getString("token", "");
    ownerId_ = prefs_.getString("owner", "");
    bound_ = prefs_.getBool("bound", false);
    prefs_.end();

    if (deviceId_.isEmpty()) {
        deviceId_ = candidate;
        prefs_.begin("cupcake_auth", false);
        prefs_.putString("device_id", deviceId_);
        prefs_.end();
    }
}

void DeviceAuth::load() {
    prefs_.begin("cupcake_auth", true);
    token_ = prefs_.getString("token", "");
    ownerId_ = prefs_.getString("owner", "");
    bound_ = prefs_.getBool("bound", false);
    prefs_.end();
}

void DeviceAuth::saveState() {
    prefs_.begin("cupcake_auth", false);
    prefs_.putString("token", token_);
    prefs_.putString("owner", ownerId_);
    prefs_.putBool("bound", bound_);
    prefs_.end();
    load();
}

bool DeviceAuth::authorized(const String& token) const {
    if (!bound_) return false;
    return token.length() && token == token_;
}

bool DeviceAuth::handleCommand(const String& action, JsonObjectConst params, JsonObject data) {
    const String t = params["token"].isNull() ? "" : String(params["token"].as<const char*>());
    const String owner = params["owner"].isNull() ? "" : String(params["owner"].as<const char*>());

    if (action == "challenge" || action == "status") {
        data["bound"] = bound_;
        data["device_id"] = deviceId_;
        data["authorized"] = authorized(t);
        data["owner"] = ownerId_;   // label only (account id later); not the secret
        return true;
    }

    if (action == "pair") {
        if (bound_) {
            if (authorized(t)) {
                data["already"] = true;
                data["device_id"] = deviceId_;
                if (owner.length()) {
                    ownerId_ = owner;
                    saveState();
                }
                return true;
            }
            data["message"] = "device already paired with another owner";
            return false;
        }
        if (t.length() < 16) {
            data["message"] = "token too short";
            return false;
        }
        token_ = t;
        ownerId_ = owner;
        bound_ = true;
        saveState();
        data["device_id"] = deviceId_;
        return true;
    }

    if (action == "unpair") {
        if (bound_ && !authorized(t)) {
            data["message"] = "invalid token";
            return false;
        }
        factoryReset();
        data["unpaired"] = true;
        return true;
    }

    data["message"] = "unknown auth action";
    return false;
}

void DeviceAuth::factoryReset() {
    prefs_.begin("cupcake_auth", false);
    prefs_.clear();
    prefs_.end();
    bound_ = false;
    token_ = "";
    ownerId_ = "";
    load();
}
