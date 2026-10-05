#include "sensors/battery.h"

#include <Arduino.h>

void BatteryMonitor::begin(int adcPin, float r1, float r2) {
    adcPin_ = adcPin;
    r1_ = r1;
    r2_ = r2;
    analogReadResolution(12);
    analogSetAttenuation(ADC_11db);
    pinMode(adcPin_, INPUT);
}

float BatteryMonitor::readVoltage() {
    if (adcPin_ < 0) {
        return 0.0f;
    }
    // Average a few samples to tame ADC noise.
    const int kSamples = 16;
    uint32_t acc = 0;
    for (int i = 0; i < kSamples; ++i) {
        acc += analogRead(adcPin_);
        delay(1);
    }
    const float raw = (float) acc / (float) kSamples;
    const float vPin = (raw / (float) kAdcMax) * kAdcRef;
    // Divider: Vbat = Vpin * (R1 + R2) / R2
    return vPin * (r1_ + r2_) / r2_;
}

float BatteryMonitor::readPercentage() {
    const float v = readVoltage();
    if (v <= kEmptyV) {
        return 0.0f;
    }
    if (v >= kFullV) {
        return 100.0f;
    }
    return (v - kEmptyV) / (kFullV - kEmptyV) * 100.0f;
}
