#ifndef OTA_H
#define OTA_H

// Minimal OTA update manager built on the Arduino Update class.
// Firmware chunks arrive over BLE (see "ota/write" command); end()
// finalises the update. handle() is a no-op poll hook kept for symmetry
// with the main loop.

#include <cstddef>
#include <cstdint>

class OTAManager {
public:
    OTAManager() = default;

    // Open an update slot. Safe to call when no update is active.
    bool begin();
    // Write one firmware chunk. Returns bytes written.
    size_t write(const uint8_t* data, size_t len);
    // Poll hook (currently no background work required).
    void handle();
    // Finalise and verify. Returns true on success (caller reboots).
    bool end();
    void abort();

    bool isActive() const { return active_; }

private:
    bool active_ = false;
};

#endif // OTA_H
