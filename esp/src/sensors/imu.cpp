#include "sensors/imu.h"

#include <Wire.h>

#include "config.h"
#include "utils/logger.h"

bool IMUSensor::begin(uint8_t address) {
    if (!mpu_.begin(address, &Wire)) {
        LOGE("MPU6050 not found at 0x%02X", address);
        return false;
    }
    mpu_.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu_.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu_.setFilterBandwidth(MPU6050_BAND_21_HZ);
    initialized_ = true;
    LOGI("MPU6050 initialised (accel +-8G, gyro +-500dps)");
    return true;
}

bool IMUSensor::read(IMUData& data) {
    if (!initialized_) {
        return false;
    }
    sensors_event_t accel, gyro, temp;
    mpu_.getEvent(&accel, &gyro, &temp);

    data.accel[0] = accel.acceleration.x;
    data.accel[1] = accel.acceleration.y;
    data.accel[2] = accel.acceleration.z;
    data.gyro[0] = gyro.gyro.x;
    data.gyro[1] = gyro.gyro.y;
    data.gyro[2] = gyro.gyro.z;
    data.temp = temp.temperature;
    // timestamp/sequence are filled in by the caller (sensor task)
    return true;
}
