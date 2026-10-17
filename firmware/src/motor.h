#pragma once
#include <ESP32Servo.h>

/**
 * Servo rig for the 2-servo build:
 *  - BASE     (yaw twist)  GPIO 4  -> look_left / look_right / shake
 *  - SHOULDER (single swing driving both arms via the linkage) GPIO 5
 *     -> wave / dance / tantrum / nod (bob)
 * The optional NECK servo (GPIO 6) is reserved but disabled unless installed.
 *
 * Actions run as timed sequences on the loop; the Servos object interpolates
 * toward a per-phase target so motion is eased, not snappy.
 */
class Motors {
public:
    static constexpr int PIN_BASE = 4;
    static constexpr int PIN_SHOULDER = 5;
    static constexpr int PIN_NECK = 6;   // not installed; ignored

    // Mechanical limits (tune after first power-on!)
    static constexpr int BASE_HOME = 90;
    static constexpr int BASE_MIN = 30;
    static constexpr int BASE_MAX = 150;
    static constexpr int ARM_HOME = 90;
    static constexpr int ARM_MIN = 20;
    static constexpr int ARM_MAX = 130;

    void begin();
    /** Queue one of the MotorAction names from the app protocol. */
    void execute(const String& action);
    /** Call from loop(); steps the running sequence. */
    void tick();

    /** True once a queued sequence has finished (used to return to idle face). */
    bool busy = false;

private:
    Servo base_;
    Servo arm_;
    int basePen_ = BASE_HOME;
    int armPen_ = ARM_HOME;
    int baseTarget_ = BASE_HOME;
    int armTarget_ = ARM_HOME;
    uint32_t phaseEnd_ = 0;
    int step_ = -1;
    String action_;

    void seek(int* value, int target, int speed);
    void home();
    void nextStep();
};
