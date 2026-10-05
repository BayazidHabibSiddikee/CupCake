#include "comm/protocol.h"

#include <ArduinoJson.h>

#include <cstring>

ProtocolHandler::ProtocolHandler() = default;

size_t ProtocolHandler::writeHeader(uint8_t type, uint32_t seq, uint32_t ts,
                                     uint8_t* buf, size_t max) {
    if (buf == nullptr || max < 9) {
        return 0;
    }
    buf[0] = type;
    // Little-endian header (ESP32 is LE; Android decodes with ByteOrder.LITTLE_ENDIAN).
    memcpy(buf + 1, &seq, sizeof(seq));
    memcpy(buf + 5, &ts, sizeof(ts));
    return 9;
}

size_t ProtocolHandler::encodeSensorData(const IMUData& data, uint8_t* buffer,
                                         size_t maxLen) {
    // [type:1][seq:4][ts:4][accel:3xf32][gyro:3xf32][temp:f32] = 37 bytes
    constexpr size_t kLen = 9 + 7 * sizeof(float);
    if (buffer == nullptr || maxLen < kLen) {
        return 0;
    }
    size_t pos = writeHeader(MSG_TYPE_SENSOR, data.sequence, data.timestamp,
                             buffer, maxLen);
    if (pos == 0) {
        return 0;
    }
    memcpy(buffer + pos, data.accel, sizeof(data.accel));
    pos += sizeof(data.accel);
    memcpy(buffer + pos, data.gyro, sizeof(data.gyro));
    pos += sizeof(data.gyro);
    memcpy(buffer + pos, &data.temp, sizeof(data.temp));
    pos += sizeof(data.temp);
    return pos;
}

size_t ProtocolHandler::encodeBattery(float voltage, float percentage,
                                       uint8_t* buffer, size_t maxLen) {
    // [type:1][voltage:f32][percentage:f32] = 9 bytes
    constexpr size_t kLen = 1 + 2 * sizeof(float);
    if (buffer == nullptr || maxLen < kLen) {
        return 0;
    }
    buffer[0] = MSG_TYPE_BATTERY;
    memcpy(buffer + 1, &voltage, sizeof(voltage));
    memcpy(buffer + 1 + sizeof(voltage), &percentage, sizeof(percentage));
    return kLen;
}

size_t ProtocolHandler::encodeEvent(const char* event,
                                     const JsonDocument& data, uint8_t* buffer,
                                     size_t maxLen) {
    // [type:1][event_len:1][event][json...]
    if (buffer == nullptr || event == nullptr || maxLen < 2) {
        return 0;
    }
    const size_t eventLen = strlen(event);
    if (eventLen > 255 || 2 + eventLen >= maxLen) {
        return 0;
    }
    buffer[0] = MSG_TYPE_EVENT;
    buffer[1] = (uint8_t) eventLen;
    memcpy(buffer + 2, event, eventLen);

    const size_t jsonLen =
        serializeJson(data, buffer + 2 + eventLen, maxLen - 2 - eventLen);
    return 2 + eventLen + jsonLen;
}

// Copy one JSON object of params into cmd.params without aliasing the
// (possibly stack-local) source document.
static void copyParams(JsonObjectConst src, JsonDocument& dst) {
    dst.clear();
    for (JsonPairConst kv : src) {
        dst[kv.key()] = kv.value();
    }
}

bool ProtocolHandler::decodeCommand(const uint8_t* buffer, size_t len,
                                     Command& cmd) {
    if (buffer == nullptr || len == 0) {
        return false;
    }

    // JSON form: {"type":"...","id":"...","action":"...","params":{...}}
    if (buffer[0] == '{') {
        JsonDocument doc;
        if (deserializeJson(doc, buffer, len) != DeserializationError::Ok) {
            return false;
        }
        cmd.type = (doc["type"] | "control");
        cmd.id = (doc["id"] | "");
        cmd.action = (doc["action"] | "");
        cmd.params.clear();
        if (doc["params"].is<JsonObjectConst>()) {
            copyParams(doc["params"].as<JsonObjectConst>(), cmd.params);
        }
        return true;
    }

    // Binary form: [type:1][id_len:1][id][action_len:1][action][json_params]
    if (buffer[0] != MSG_TYPE_COMMAND || len < 3) {
        return false;
    }
    size_t pos = 1;
    const uint8_t idLen = buffer[pos++];
    if (pos + idLen + 1 > len) {
        return false;
    }
    cmd.id.assign(reinterpret_cast<const char*>(buffer + pos), idLen);
    pos += idLen;

    const uint8_t actionLen = buffer[pos++];
    if (pos + actionLen > len) {
        return false;
    }
    cmd.action.assign(reinterpret_cast<const char*>(buffer + pos), actionLen);
    pos += actionLen;

    // Binary frames carry no type string; they are control-plane commands.
    cmd.type = "control";
    cmd.params.clear();
    if (pos < len) {
        if (deserializeJson(cmd.params, buffer + pos, len - pos) !=
            DeserializationError::Ok) {
            return false;
        }
    }
    return true;
}
