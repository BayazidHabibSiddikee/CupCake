#include "utils/config.h"

#include "config.h"

void ConfigManager::begin() {
    if (begun_) {
        return;
    }
    prefs_.begin(NVS_NAMESPACE, false);
    begun_ = true;
}

uint32_t ConfigManager::getSensorInterval() {
    if (!begun_) {
        return DEFAULT_SENSOR_INTERVAL_MS;
    }
    return prefs_.getUInt("sens_int", DEFAULT_SENSOR_INTERVAL_MS);
}

void ConfigManager::setSensorInterval(uint32_t ms) {
    if (!begun_) {
        return;
    }
    prefs_.putUInt("sens_int", ms);
}

uint32_t ConfigManager::getSleepDuration() {
    if (!begun_) {
        return DEFAULT_SLEEP_DURATION_MS;
    }
    return prefs_.getUInt("sleep_ms", DEFAULT_SLEEP_DURATION_MS);
}

void ConfigManager::setSleepDuration(uint32_t ms) {
    if (!begun_) {
        return;
    }
    prefs_.putUInt("sleep_ms", ms);
}

String ConfigManager::getDeviceId() {
    if (!begun_) {
        return String(DEVICE_NAME);
    }
    String id = prefs_.getString("dev_id", "");
    if (id.isEmpty()) {
        // Stable default derived from the ESP32 MAC.
        uint64_t mac = ESP.getEfuseMac();
        char buf[32];
        snprintf(buf, sizeof(buf), "%s-%04X", DEVICE_NAME,
                 (unsigned) (mac & 0xFFFF));
        id = String(buf);
        prefs_.putString("dev_id", id);
    }
    return id;
}
