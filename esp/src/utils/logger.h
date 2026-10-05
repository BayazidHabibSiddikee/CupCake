#ifndef LOGGER_H
#define LOGGER_H

// Thin logging facade over the Arduino-ESP32 log macros.
// Arduino.h (esp32-hal-log.h) provides log_e/log_w/log_i/log_d/log_v.
// Including Arduino.h here keeps translation units self-contained.

#include <Arduino.h>

#define LOGE(...) log_e(__VA_ARGS__)
#define LOGW(...) log_w(__VA_ARGS__)
#define LOGI(...) log_i(__VA_ARGS__)
#define LOGD(...) log_d(__VA_ARGS__)
#define LOGV(...) log_v(__VA_ARGS__)

#endif // LOGGER_H
