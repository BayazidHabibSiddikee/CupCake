#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <ArduinoJson.h>
#include <string>
#include <vector>

struct IMUData {
    uint32_t timestamp;
    uint32_t sequence;
    float accel[3];
    float gyro[3];
    float temp;
};

struct Command {
    std::string type;      // "config", "control", "ota", "info"
    std::string id;        // Command ID for response
    std::string action;    // Specific action
    JsonDocument params;   // Parameters
};

class ProtocolHandler {
public:
    ProtocolHandler();

    // Encode sensor data to binary buffer
    size_t encodeSensorData(const IMUData& data, uint8_t* buffer, size_t maxLen);

    // Encode battery info
    size_t encodeBattery(float voltage, float percentage, uint8_t* buffer, size_t maxLen);

    // Encode event (button, gesture, etc.)
    size_t encodeEvent(const char* event, const JsonDocument& data, uint8_t* buffer, size_t maxLen);

    // Decode command from binary buffer
    bool decodeCommand(const uint8_t* buffer, size_t len, Command& cmd);

    // Message type identifiers
    static constexpr uint8_t MSG_TYPE_SENSOR = 0x01;
    static constexpr uint8_t MSG_TYPE_BATTERY = 0x02;
    static constexpr uint8_t MSG_TYPE_EVENT = 0x03;
    static constexpr uint8_t MSG_TYPE_RESPONSE = 0x80;
    static constexpr uint8_t MSG_TYPE_COMMAND = 0x81;

private:
    // Simple binary format:
    // [type:1][sequence:4][timestamp:4][payload...]
    // For commands: [type:1][id_len:1][id][action_len:1][action][json_params]

    size_t writeHeader(uint8_t type, uint32_t seq, uint32_t ts, uint8_t* buf, size_t max);
};

#endif // PROTOCOL_H