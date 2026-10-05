#include "power/sleep.h"

#include <Arduino.h>
#include <esp_sleep.h>

#include "utils/logger.h"

void SleepManager::begin() {
    begun_ = true;
    lastActivity_ = millis();
}

void SleepManager::poke() {
    lastActivity_ = millis();
}

bool SleepManager::shouldSleep() {
    if (!begun_) {
        return false;
    }
    // Auto-sleep only when the idle window has elapsed. The window is
    // refreshed by poke(); BLE connect/disconnect and commands should
    // call poke() via the app layer. Default 60 s keeps the default
    // config (sleep disabled via commands) safe.
    return (millis() - lastActivity_) >= idleTimeoutMs_;
}

void SleepManager::deepSleep(uint32_t durationMs) {
    LOGI("Deep sleep for %lu ms", (unsigned long) durationMs);
    Serial.flush();
    esp_sleep_enable_timer_wakeup((uint64_t) durationMs * 1000ULL);
    esp_deep_sleep_start();
}
