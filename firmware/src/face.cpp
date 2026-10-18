#ifndef CUPCAKE_NO_FACE

#include "face.h"
#include <Wire.h>

// ---------------------------------------------------------------------------
// Blocky drawing helpers (pixel-art feel on a native 128x64 canvas)
// ---------------------------------------------------------------------------

static U8G2_SSD1306_128X64_NONAME_F_HW_I2C* g = nullptr;

static void filledOval(int cx, int cy, int rx, int ry, bool open) {
    if (open) {
        g->setDrawColor(1);
        g->drawDisc(cx, cy, rx);
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int)(rx * sqrtf(max(0.f, 1.f - (dy * dy) / float(ry * ry))));
            g->drawHLine(cx - dx, cy + dy, dx * 2 + 1);
        }
    } else {
        g->drawCircle(cx, cy, rx);
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int)(rx * sqrtf(max(0.f, 1.f - (dy * dy) / float(ry * ry))));
            g->drawHLine(cx - dx, cy + dy, 1);
            g->drawHLine(cx + dx, cy + dy, 1);
        }
    }
}

/** Blocky up-arc (closed happy eye) or down-arc (frown mouth). */
static void blockyArc(int cx, int cy, int halfW, int ry, bool up) {
    for (int dx = -halfW; dx <= halfW; dx++) {
        int dy = (int)(ry * sqrtf(max(0.f, 1.f - (dx * dx) / float(halfW * halfW))));
        if (up) dy = -dy;
        g->drawBox(cx + dx, cy + dy, 2, 2);
    }
}

static void zzz(int x, int y, int phase) {
    g->setFont(u8g2_font_6x10_tf);
    static const char* frames[] = {"z", "zZ", "zZz", "Zz"};
    g->drawStr(x, y + ((phase % 2) * 2 - 1), frames[phase % 4]);
}

// ---------------------------------------------------------------------------
// Faces (each drawn on the 128x64 canvas; eye centers at ~x=42/86, y=26)
// ---------------------------------------------------------------------------

static void faceHappy() {
    g->clearBuffer();
    blockyArc(42, 22, 16, 9, true);
    blockyArc(86, 22, 16, 9, true);
    g->drawBox(34, 24, 5, 2);   // blush specks
    g->drawBox(89, 24, 5, 2);
    blockyArc(64, 38, 10, 6, true);   // w-shaped smile
    g->drawBox(63, 44, 4, 2);
}

static void faceSleepy() {
    g->clearBuffer();
    g->drawBox(28, 22, 26, 4);
    g->drawBox(74, 22, 26, 4);
    g->drawBox(32, 37, 12, 3);
    g->drawBox(52, 25, 2, 6);
    g->drawBox(52, 31, 12, 3);
    g->drawBox(64, 43, 6, 3);
    g->setFont(u8g2_font_6x10_tf);
}

static void faceSurprised() {
    g->clearBuffer();
    filledOval(42, 24, 10, 12, false);
    filledOval(86, 24, 10, 12, false);
    filledOval(64, 46, 5, 7, true);
}

static void faceSad() {
    g->clearBuffer();
    g->drawBox(30, 20, 14, 4);
    g->drawBox(26, 24, 6, 6);
    g->drawBox(84, 20, 14, 4);
    g->drawBox(94, 24, 6, 6);
    g->drawBox(32, 30, 8, 4);   // tears
    g->drawBox(94, 30, 8, 4);
    blockyArc(64, 46, 12, 6, false);  // frown
}

static void faceAngry() {
    g->clearBuffer();
    g->drawLine(18, 10, 52, 20);
    g->drawLine(20, 14, 50, 22);
    g->drawLine(110, 10, 76, 20);
    g->drawLine(108, 14, 78, 22);
    g->drawLine(24, 26, 46, 36);
    g->drawLine(46, 36, 40, 30);
    g->drawLine(104, 26, 84, 36);
    g->drawLine(84, 36, 90, 30);
    blockyArc(64, 50, 10, 5, false);
    g->drawBox(94, 8, 3, 3);
    g->drawBox(99, 5, 3, 3);
    g->drawBox(98, 11, 3, 3);
}

static void faceRage() {
    faceAngry();
    // Extra "steam" puffs + tighter brows
    g->drawBox(30, 6, 4, 4);
    g->drawBox(96, 6, 4, 4);
}

static void faceThinking() {
    g->clearBuffer();
    filledOval(40, 20, 9, 10, true);
    g->drawDisc(38, 18, 3);            // pupils top-left
    filledOval(88, 20, 9, 10, true);
    g->drawDisc(86, 18, 3);
    g->drawBox(60, 46, 14, 3);
    g->drawBox(74, 43, 4, 3);
}

static void faceThinkingDots(int phase) {
    static const int xs[] = {100, 108, 114};
    static const int ys[] = {22, 14, 8};
    for (int i = 0; i < 3; i++) {
        int r = (phase + i) % 3;
        g->drawBox(xs[i] - r, ys[i] - r, r * 2 + 2, r * 2 + 2);
    }
}

static void faceConfused() {
    g->clearBuffer();
    filledOval(42, 26, 9, 9, true);
    g->setDrawColor(0);
    g->drawBox(42, 23, 5, 5);
    g->setDrawColor(1);
    g->drawDisc(44, 25, 2);
    filledOval(88, 26, 9, 9, true);
    g->setDrawColor(0);
    g->drawBox(88, 28, 5, 5);
    g->setDrawColor(1);
    g->drawDisc(92, 25, 2);
    g->setFont(u8g2_font_8x13B_tf);
    g->drawStr(110, 14, "?");
    blockyArc(64, 48, 8, 4, true);
}

static void faceAnnoyed() {
    g->clearBuffer();
    g->drawBox(26, 24, 24, 3);
    g->drawBox(78, 22, 24, 3);
    g->drawBox(56, 42, 14, 3);
    g->drawBox(72, 38, 4, 3);
}

static void faceWink() {
    g->clearBuffer();
    filledOval(42, 24, 8, 10, true);
    g->drawDisc(42, 24, 3);
    blockyArc(86, 24, 10, 6, true);
    g->drawBox(56, 46, 16, 3);
}

static void faceHeartEyes() {
    g->clearBuffer();
    static const uint8_t heart[] = {
        0b01100110, 0b11111111, 0b11111111, 0b01111110,
        0b00111100, 0b00011000, 0b00000000
    };
    g->drawXBMP(30, 14, 8, 7, heart);
    g->drawXBMP(84, 14, 8, 7, heart);
    blockyArc(64, 44, 12, 6, true);
}

static void faceExcited() {
    faceSurprised();
    blockyArc(64, 44, 8, 4, true);
}

static void faceNeutral() {
    g->clearBuffer();
    filledOval(42, 24, 8, 10, true);
    filledOval(86, 24, 8, 10, true);
    g->drawDisc(42, 25, 3);
    g->drawDisc(86, 25, 3);
    g->drawBox(56, 46, 16, 3);
}

// ---------------------------------------------------------------------------
// Face class
// ---------------------------------------------------------------------------

void Face::begin() {
    // Probe BEFORE constructing U8g2: an absent SSD1306 ACKs nothing, so we skip
    // u8g2 init entirely instead of initing then hammering a dead bus 15x/sec.
    Wire.begin();
    Wire.beginTransmission(0x3C);
    if (Wire.endTransmission() != 0) {
        Serial.println("[face] no SSD1306 at 0x3C — displayless mode");
        return;   // u8g2_/g stay null; tick()/set() no-op
    }
    if (!u8g2_) u8g2_ = new U8G2_SSD1306_128X64_NONAME_F_HW_I2C(U8G2_R0, U8X8_PIN_NONE);
    g = u8g2_;
    g->begin();
    g->clear();
    render(0);
}

/** Unified alias map: FaceExpression names + sprite-sheet names + app stems. */
static void drawFace(const String& name, int idlePhase) {
    if      (name == "happy" || name == "laugh")           faceHappy();
    else if (name == "sleepy")                             { faceSleepy(); zzz(92, 16, idlePhase); }
    else if (name == "surprised" || name == "shock")       faceSurprised();
    else if (name == "sad")                                faceSad();
    else if (name == "angry")                              faceAngry();
    else if (name == "rage")                               faceRage();
    else if (name == "thinking")                           { faceThinking(); faceThinkingDots(idlePhase); }
    else if (name == "confused")                           faceConfused();
    else if (name == "annoyed")                            faceAnnoyed();
    else if (name == "wink")                               faceWink();
    else if (name == "heart_eyes" || name == "lovely")     faceHeartEyes();
    else if (name == "excited")                            faceExcited();
    else                                                   faceNeutral();
}

void Face::set(const String& expression) {
    if (expression == expr_) return;
    expr_ = expression;
    blinking_ = false;
    if (!g) return;
    render(millis());
}

void Face::tick() {
    if (!g) return;   // displayless builds / no panel detected
    uint32_t now = millis();
    if (now - lastFrame_ < 66) return;   // ~15 fps
    lastFrame_ = now;

    if (now >= blinkAt_ && expr_ != "sleepy" && expr_ != "surprised") {
        blinking_ = true;
        blinkUntil_ = now + 140;
        blinkAt_ = now + 2800 + random(2600);
    }
    if (blinking_ && now > blinkUntil_) blinking_ = false;

    render(now);
}

void Face::render(uint32_t now) {
    int idlePhase = (now / 400) % 8;
    drawFace(expr_, idlePhase);
    if (blinking_) renderBlinkOverlay();
    g->sendBuffer();
}

/** Overlay closed eyes over the current frame for ~140 ms. */
void Face::renderBlinkOverlay() {
    g->drawBox(32, 22, 22, 3);
    g->drawBox(74, 22, 22, 3);
}

#endif  // CUPCAKE_NO_FACE
