#ifndef IMU_H
#define IMU_H

// MPU6050 IMU driver wrapper. Fills the shared IMUData struct
// (see comm/protocol.h) used by the BLE protocol encoder.

#include <Adafruit_MPU6050.h>

#include <cstdint>

#include "comm/protocol.h"
#include "config.h"

class IMUSensor {
public:
    IMUSensor() = default;

    bool begin(uint8_t address = IMU_ADDRESS);
    bool read(IMUData& data);
    bool isInitialized() const { return initialized_; }

private:
    Adafruit_MPU6050 mpu_;
    bool initialized_ = false;
};

#endif // IMU_H
