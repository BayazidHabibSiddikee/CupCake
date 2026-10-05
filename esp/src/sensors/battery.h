#ifndef BATTERY_H
#define BATTERY_H

// Battery voltage monitor for a simple resistor-divider into an ADC pin.
// Percentage is a linear estimate between emptyV (0%) and fullV (100%).

#include <cstdint>

class BatteryMonitor {
public:
    BatteryMonitor() = default;

    void begin(int adcPin, float r1, float r2);
    float readVoltage();
    float readPercentage();

private:
    int adcPin_ = -1;
    float r1_ = 100000.0f;
    float r2_ = 10000.0f;

    static constexpr float kAdcRef = 3.3f;
    static constexpr int kAdcMax = 4095; // 12-bit default on ESP32-S3 Arduino
    static constexpr float kEmptyV = 3.0f;
    static constexpr float kFullV = 4.2f;
};

#endif // BATTERY_H
