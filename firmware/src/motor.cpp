#include "motor.h"
#include <Arduino.h>

void Motors::begin() {
    ESP32PWM::allocateTimer(0);
    ESP32PWM::allocateTimer(1);
    ESP32PWM::allocateTimer(2);
    ESP32PWM::allocateTimer(3);
    base_.setPeriodHertz(50);
    base_.attach(PIN_BASE, 500, 2400);
    base_.write(BASE_HOME);
    arm_.setPeriodHertz(50);
    arm_.attach(PIN_SHOULDER, 500, 2400);
    arm_.write(ARM_HOME);
}

void Motors::seek(int* value, int target, int speed) {
    if (*value < target) *value = min(target, *value + speed);
    else if (*value > target) *value = max(target, *value - speed);
}

void Motors::home() {
    baseTarget_ = BASE_HOME;
    armTarget_ = ARM_HOME;
}

void Motors::execute(const String& action) {
    action_ = action;
    step_ = 0;
    busy = true;
    nextStep();
}

void Motors::nextStep() {
    const uint32_t ms = 260;   // per-phase duration
    phaseEnd_ = millis() + ms;

    if (action_ == "look_left") {
        baseTarget_ = (step_ % 2 == 0) ? BASE_MAX : BASE_HOME;
        if (step_ >= 2) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "look_right") {
        baseTarget_ = (step_ % 2 == 0) ? BASE_MIN : BASE_HOME;
        if (step_ >= 2) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "shake") {
        baseTarget_ = (step_ % 2 == 0) ? BASE_MAX : BASE_MIN;
        if (step_ >= 4) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "wave") {
        armTarget_ = (step_ % 2 == 0) ? ARM_MAX : ARM_MIN;
        if (step_ >= 5) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "dance") {
        baseTarget_ = (step_ % 2 == 0) ? BASE_MAX : BASE_MIN;
        armTarget_  = (step_ % 2 == 0) ? ARM_MAX : ARM_MIN;
        if (step_ >= 7) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "tantrum") {
        baseTarget_ = (step_ % 2 == 0) ? BASE_MAX - 10 : BASE_MIN + 10;
        armTarget_  = (step_ % 2 == 0) ? ARM_MAX : ARM_MIN;
        if (step_ >= 9) { step_ = -1; home(); busy = false; return; }
    } else if (action_ == "nod") {
        // No neck servo: a small shoulder bob sells the nod.
        armTarget_ = (step_ % 2 == 0) ? ARM_HOME + 14 : ARM_HOME - 6;
        if (step_ >= 3) { step_ = -1; home(); busy = false; return; }
    } else { // "idle" / unknown
        step_ = -1;
        home();
        busy = false;
        return;
    }
    step_++;
}

void Motors::tick() {
    if (step_ < 0) return;
    seek(&basePen_, baseTarget_, 4);
    seek(&armPen_, armTarget_, 4);
    base_.write(basePen_);
    arm_.write(armPen_);
    if (millis() > phaseEnd_) nextStep();
}
