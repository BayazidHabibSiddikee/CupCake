#pragma once

/**
 * Device-auth / pairing: every CupCake unit is bound to exactly ONE owner
 * (phone). The first phone that pairs mints a random SHA-256 hex token and
 * writes it here over BLE; afterwards ALL commands must carry that token in
 * "params.token". Unowned devices only answer auth challenge / pair.
 *
 * The token is opaque on-device (plain hex string, no crypto) — the phone
 * derives meaning later (upcoming single-Google-account ownership: the token
 * becomes a hash over account id + device id, ordering stays "owner → tokens").
 */

#include <Arduino.h>
#include <Preferences.h>
#include <ArduinoJson.h>

class DeviceAuth {
public:
    void begin();

    /** auth command dispatch — fills data and returns ok/error. */
    bool handleCommand(const String& action, JsonObjectConst params, JsonObject data);

    /** True when the supplied token owns this unit. Unowned units reject everything. */
    bool authorized(const String& token) const;

    bool bound() const { return bound_; }
    const String& deviceId() const { return deviceId_; }
    const String& token() const { return token_; }   // for the WS handshake URL

    /** Serial/dev escape hatch: wipe binding. */
    void factoryReset();

private:
    void load();
    void saveState();
    Preferences prefs_;
    String deviceId_;
    String token_;
    String ownerId_;    // future: google account label, opaque today
    bool bound_ = false;
};
