#pragma once

/**
 * Face renderer for the 0.96" SSD1306 128x64 I2C OLED.
 *
 * Blocky pixel-art style matching the phone's emotion sheet. Expression names
 * are the union of what the phone sends:
 *  - EspWebSocketServer.FaceExpression: neutral happy sad angry rage excited
 *    thinking sleepy wink heart_eyes
 *  - updated sprite sheet aliases: surprised confused annoyed
 * Unknown names fall back to neutral.
 *
 * Build with -DCUPCAKE_NO_FACE (dev boards without an OLED wired) to compile
 * a do-nothing stub: no I2C traffic, identical public API.
 */

#include <Arduino.h>

#ifdef CUPCAKE_NO_FACE

class Face {
public:
    static constexpr int W = 128;
    static constexpr int H = 64;

    void begin() {
        if (!announced_) {
            announced_ = true;
            Serial.println("[face] disabled (CUPCAKE_NO_FACE build)");
        }
    }
    void set(const String& expression, uint32_t durationMs = 0) { expr_ = expression; revertAt_ = durationMs ? millis() + durationMs : 0; }
    const String& current() const { return expr_; }
    void tick() {}

private:
    bool announced_ = false;
    String expr_ = "neutral";
    uint32_t revertAt_ = 0;
};

#else

#include <U8g2lib.h>

class Face {
public:
    static constexpr int W = 128;
    static constexpr int H = 64;

    void begin();
    /**
     * Sets the current expression by name ("happy", "confused", ...).
     * With a non-zero [durationMs] the previous expression is restored when it
     * expires — without this every face latched forever.
     */
    void set(const String& expression, uint32_t durationMs = 0);
    /** Current expression name. */
    const String& current() const { return expr_; }
    /** Call from loop(); animates blink / idle motion at ~15 fps. */
    void tick();

private:
    // Heap-allocated: U8g2 needs constructor args, so a direct member breaks
    // default construction in the global Face instance.
    U8G2_SSD1306_128X64_NONAME_F_HW_I2C* u8g2_ = nullptr;
    String expr_ = "neutral";
    String prevExpr_ = "neutral";
    uint32_t revertAt_ = 0;
    uint32_t lastFrame_ = 0;
    uint32_t blinkAt_ = 3000;
    bool blinking_ = false;
    uint32_t blinkUntil_ = 0;

    void render(uint32_t now);
    void renderBlinkOverlay();
};

#endif
