#include "power/ota.h"

#include <Arduino.h>
#include <Update.h>

#include "utils/logger.h"

bool OTAManager::begin() {
    if (active_) {
        return true;
    }
    if (!Update.begin(UPDATE_SIZE_UNKNOWN)) {
        LOGE("OTA begin failed");
        Update.printError(Serial);
        return false;
    }
    active_ = true;
    LOGI("OTA update slot opened");
    return true;
}

size_t OTAManager::write(const uint8_t* data, size_t len) {
    if (!active_) {
        return 0;
    }
    size_t written = Update.write(const_cast<uint8_t*>(data), len);
    if (written != len) {
        LOGW("OTA short write: %u/%u", (unsigned) written, (unsigned) len);
    }
    return written;
}

void OTAManager::handle() {
    // No background work required for the Arduino Update flow.
}

bool OTAManager::end() {
    if (!active_) {
        return false;
    }
    active_ = false;
    if (!Update.end(true)) {
        LOGE("OTA end failed");
        Update.printError(Serial);
        return false;
    }
    LOGI("OTA update complete, %u bytes", (unsigned) Update.size());
    return true;
}

void OTAManager::abort() {
    if (active_) {
        Update.abort();
        active_ = false;
    }
}
