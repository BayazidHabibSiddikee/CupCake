#ifndef PINOUT_H
#define PINOUT_H

// ESP32-S3 DevKitC-1 Pinout Reference
// https://docs.espressif.com/projects/esp-idf/en/latest/esp32s3/hw-reference/esp32s3/user-guide-devkitc-1.html

// I2C
#define I2C_SDA_PIN     8   // GPIO8
#define I2C_SCL_PIN     9   // GPIO9

// SPI (if needed)
#define SPI_MOSI_PIN    11  // GPIO11
#define SPI_MISO_PIN    13  // GPIO13
#define SPI_CLK_PIN     12  // GPIO12
#define SPI_CS_PIN      10  // GPIO10

// UART0 (USB-CDC)
#define UART0_TX_PIN    43  // GPIO43
#define UART0_RX_PIN    44  // GPIO44

// UART1 (available)
#define UART1_TX_PIN    17  // GPIO17
#define UART1_RX_PIN    18  // GPIO18

// ADC
#define ADC1_CH0_PIN    1   // GPIO1
#define ADC1_CH1_PIN    2   // GPIO2
#define ADC1_CH2_PIN    3   // GPIO3
#define ADC1_CH3_PIN    4   // GPIO4
#define ADC1_CH4_PIN    5   // GPIO5
#define ADC1_CH5_PIN    6   // GPIO6
#define ADC1_CH6_PIN    7   // GPIO7
#define ADC1_CH7_PIN    8   // GPIO8 (shared with I2C SDA)
#define ADC2_CH0_PIN    9   // GPIO9 (shared with I2C SCL)
#define ADC2_CH1_PIN    10  // GPIO10
#define ADC2_CH2_PIN    11  // GPIO11
#define ADC2_CH3_PIN    12  // GPIO12
#define ADC2_CH4_PIN    13  // GPIO13
#define ADC2_CH5_PIN    14  // GPIO14
#define ADC2_CH6_PIN    15  // GPIO15
#define ADC2_CH7_PIN    16  // GPIO16
#define ADC2_CH8_PIN    17  // GPIO17
#define ADC2_CH9_PIN    18  // GPIO18

// Battery voltage divider (using ADC1_CH3 = GPIO4)
#define BATTERY_ADC_PIN ADC1_CH3_PIN

// Built-in RGB LED (WS2812 on GPIO48)
#define RGB_LED_PIN 48

// BOOT button
#define BOOT_BUTTON_PIN 0

// Available GPIOs for custom use
// GPIO19-21, 26-27, 33-38, 39-42, 45-47

// Strapping pins (avoid using at boot)
// GPIO0, GPIO3, GPIO45, GPIO46

// Input-only pins (no output driver)
// GPIO34, GPIO35, GPIO36, GPIO37, GPIO38, GPIO39

#endif // PINOUT_H