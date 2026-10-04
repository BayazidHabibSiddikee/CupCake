#ifndef CONFIG_H
#define CONFIG_H

// Firmware version
#define FIRMWARE_VERSION "1.0.0"

// Device name for BLE
#define DEVICE_NAME "CupCake-ESP32"

// Pin assignments (ESP32-S3 DevKitC-1)
#define I2C_SDA_PIN 8
#define I2C_SCL_PIN 9

#define BATTERY_ADC_PIN 4
#define BATTERY_R1 100000.0  // 100k upper resistor
#define BATTERY_R2 10000.0   // 10k lower resistor (divider ratio 11:1)

// IMU (MPU6050)
#define IMU_ADDRESS 0x68
#define IMU_INT_PIN 10

// LED
#define STATUS_LED_PIN 48  // Built-in RGB LED on ESP32-S3

// BLE
#define BLE_DEVICE_NAME "CupCake"
#define BLE_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define BLE_TX_CHAR_UUID "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
#define BLE_RX_CHAR_UUID "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

// Default configuration
#define DEFAULT_SENSOR_INTERVAL_MS 200
#define DEFAULT_SLEEP_DURATION_MS 0  // 0 = disabled

// NVS namespace
#define NVS_NAMESPACE "cupcake"

// Logging
#define LOG_LEVEL 3  // 0=none, 1=error, 2=warn, 3=info, 4=debug, 5=verbose

#endif // CONFIG_H