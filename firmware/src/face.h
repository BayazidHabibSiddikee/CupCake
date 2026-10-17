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
 */

#include <U8g2lib.h>

class Face {
public:
    static constexpr int W = 128;
    static constexpr int H = 64;

    void begin();
    /** Sets the current expression by name ("happy", "confused", ...). */
    void set(const String& expression);
    /** Current expression name. */
    const String& current() const { return expr_; }
    /** Call from loop(); animates blink / idle motion at ~15 fps. */
    void tick();

private:
    U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2_{};
    String expr_ = "neutral";
    uint32_t lastFrame_ = 0;
    uint32_t blinkAt_ = 3000;
    bool blinking_ = false;
    uint32_t blinkUntil_ = 0;

    void render(uint32_t now);
    void renderBlinkOverlay();
};
