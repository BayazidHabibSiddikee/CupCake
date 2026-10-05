#ifndef UTILS_CONFIG_H
#define UTILS_CONFIG_H

// Persistent device configuration backed by NVS Preferences.
// NOTE: the macro header is <config.h> (include/config.h); this header
// is always included as "utils/config.h" so there is no ambiguity.

#include <Arduino.h>
#include <Preferences.h>

#include <cstdint>

class ConfigManager {
public:
    ConfigManager() = default;

    void begin();

    uint32_t getSensorInterval();
    void setSensorInterval(uint32_t ms);

    uint32_t getSleepDuration();
    void setSleepDuration(uint32_t ms);

    String getDeviceId();

private:
    Preferences prefs_;
    bool begun_ = false;
};

#endif // UTILS_CONFIG_H
