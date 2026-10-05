#ifndef SLEEP_H
#define SLEEP_H

// Deep-sleep manager. Auto-sleep is opt-in: shouldSleep() returns true
// only when a non-zero sleep duration is configured AND the idle timeout
// has elapsed since the last activity poke.

#include <cstdint>

class SleepManager {
public:
    SleepManager() = default;

    void begin();
    bool shouldSleep();
    void deepSleep(uint32_t durationMs);
    void poke(); // reset the idle timer (call on BLE activity)

    void setIdleTimeout(uint32_t ms) { idleTimeoutMs_ = ms; }

private:
    uint32_t idleTimeoutMs_ = 60000; // 60 s default idle window
    uint32_t lastActivity_ = 0;
    bool begun_ = false;
};

#endif // SLEEP_H
