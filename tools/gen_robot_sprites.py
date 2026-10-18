#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CupCake robot 2D sprite generator.

Draws the companion as the FreeCAD robot (blue head + white bezel, blue/white
torso, little arms and feet) with an in-screen pixel face per emotion —
so the app sprite is literally "the bot you see in the OLED".

Outputs 512x512 PNGs into app/src/main/assets/marin/emotions/.

    python3 tools/gen_robot_sprites.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "app/src/main/assets/marin/emotions")
S = 4           # supersample factor
W = 512 * S     # canvas

# palette — matches the FreeCAD build (blue shells + white bezel/panel)
BLUE = (91, 140, 239, 255)
BLUE_D = (62, 111, 204, 255)
BLUE_L = (120, 164, 247, 255)
WHITE = (244, 246, 250, 255)
GREY = (215, 221, 232, 255)
SCREEN = (10, 15, 28, 255)
BEZEL = (32, 40, 59, 255)
CYAN = (111, 232, 255, 255)
CYAN_D = (70, 180, 210, 255)
PINK = (255, 156, 199, 255)
RED = (255, 90, 90, 255)
SHADOW = (60, 70, 90, 60)
TEAR = (120, 190, 255, 230)


def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def rot(img, deg, center=None):
    if center is None:
        center = (W // 2, W // 2)
    return img.rotate(deg, resample=Image.Resampling.BICUBIC, center=center)


def robot_sprite(eyes_fn, head_tilt=0.0, arm_phase=0, extras=None):
    """Draw a 512px robot. eyes_fn(d, cx, cy, s) paints the face in the screen.
    arm_phase:  0 both arms hang, +1 arms up-cheerful, -1 arms droop,
                2 opposite swing (one fwd one aft), 3 arm raised (thinking)."""
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    cx = W // 2
    # ground shadow
    d.ellipse([cx - 170*S, 462*S, cx + 170*S, 486*S], fill=SHADOW)

    # ---------------- legs / feet ----------------
    for sx in (-52, 52):
        fx = cx + sx * S
        rrect(d, [fx - 44*S, 424*S, fx + 44*S, 464*S], 18*S, fill=BLUE_D)
        rrect(d, [fx - 16*S, 396*S, fx + 16*S, 430*S], 12*S, fill=BLUE)
        d.rounded_rectangle([fx - 40*S, 428*S, fx + 40*S, 462*S], radius=15*S,
                            outline=BLUE_L, width=max(1, 2*S))

    # ---------------- arms (behind torso) ----------------
    def arm(sx, raise_deg):
        pivot = (cx + sx * 96 * S, 320 * S)
        pad = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        pd = ImageDraw.Draw(pad)
        pd.ellipse([pivot[0] - 13*S, pivot[1] - 13*S, pivot[0] + 13*S, pivot[1] + 13*S],
                   fill=BLUE_D)
        # paddle hanging down-left/right in local frame
        px = pivot[0] + sx * 6 * S
        rrect(pd, [px - 15*S, pivot[1], px + 15*S, pivot[1] + 78*S], 14*S, fill=BLUE)
        pad = rot(pad, -raise_deg * (1 if sx > 0 else -1), pivot)
        img.alpha_composite(pad)

    if arm_phase == 0:
        arm(-1, 10); arm(1, 10)
    elif arm_phase == 1:
        arm(-1, 95); arm(1, 95)
    elif arm_phase == -1:
        arm(-1, 0); arm(1, 0)
    elif arm_phase == 2:
        arm(-1, 70); arm(1, 100)
    elif arm_phase == 3:
        arm(-1, 12); arm(1, 130)

    # ---------------- torso ----------------
    rrect(d, [cx - 104*S, 262*S, cx + 104*S, 448*S], 46*S, fill=BLUE)
    rrect(d, [cx - 66*S, 278*S, cx + 66*S, 430*S], 34*S, fill=WHITE)
    # front panel details: speaker grid + chest light
    for i in range(3):
        dot = [cx - 44*S, 356*S + i*16*S, cx - 40*S, 360*S + i*16*S]
        rrect(d, [cx - 52*S, 352*S + i*16*S, cx - 44*S, 360*S + i*16*S], 3*S, fill=GREY)
        rrect(d, [cx + 44*S, 352*S + i*16*S, cx + 52*S, 360*S + i*16*S], 3*S, fill=GREY)
    d.ellipse([cx - 10*S, 306*S, cx + 10*S, 326*S], fill=CYAN if arm_phase != -1 else GREY)
    # shoulder caps
    for sx in (-104, 104):
        d.ellipse([cx + sx*S - 16*S, 300*S, cx + sx*S + 16*S, 332*S], fill=BLUE_D)

    # ---------------- head (own layer so it can tilt) ----------------
    head = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    hd = ImageDraw.Draw(head)
    hx, hy = cx, 176*S
    # neck
    rrect(hd, [hx - 18*S, hy + 66*S, hx + 18*S, hy + 92*S], 8*S, fill=BLUE_D)
    # head shell
    rrect(hd, [hx - 118*S, hy - 118*S, hx + 118*S, hy + 96*S], 56*S, fill=BLUE)
    # top shine
    rrect(hd, [hx - 84*S, hy - 104*S, hx + 84*S, hy - 60*S], 22*S, fill=BLUE_L)
    # white bezel ring + dark screen
    rrect(hd, [hx - 88*S, hy - 66*S, hx + 88*S, hy + 62*S], 34*S, fill=WHITE)
    rrect(hd, [hx - 80*S, hy - 58*S, hx + 80*S, hy + 54*S], 28*S, fill=SCREEN)
    # heart-light accents either side of the screen (the sparkle cheek lights)
    rrect(hd, [hx - 108*S, hy - 44*S, hx - 92*S, hy - 6*S], 8*S, fill=BLUE_L)
    rrect(hd, [hx + 92*S, hy - 44*S, hx + 108*S, hy - 6*S], 8*S, fill=BLUE_L)

    eyes_fn(hd, hx, hy)  # face inside the screen

    # extras (Zzz, "!?", question mark...) go with the head layer so they tilt too
    if extras:
        extras(hd, hx, hy)

    head = rot(head, head_tilt, (hx, hy + 40*S))
    img.alpha_composite(head)

    img = img.resize((512, 512), Image.Resampling.LANCZOS)
    return img


# ---------------------------------------------------------------------------
# face painters — pixel-glow style inside the OLED window
# screen area: x in [hx-80S, hx+80S], y in [hy-58S, hy+54S]
# ---------------------------------------------------------------------------

def ex(hx, sign):
    """eye centre x"""
    return hx + sign * 30 * S


def e_happy(d, hx, hy):
    for sign in (-1, 1):  # ^ ^ eyes
        cx, cy = ex(hx, sign), hy - 8 * S
        d.arc([cx - 17*S, cy - 10*S, cx + 17*S, cy + 16*S], start=170, end=370,
              fill=CYAN, width=9*S)
    d.arc([hx - 26*S, hy + 14*S, hx + 26*S, hy + 52*S], start=20, end=160,
          fill=CYAN, width=9*S)  # big open smile


def e_normal(d, hx, hy):
    for sign in (-1, 1):
        cx, cy = ex(hx, sign), hy - 6 * S
        rrect(d, [cx - 9*S, cy - 13*S, cx + 9*S, cy + 13*S], 7*S, fill=CYAN)
        d.ellipse([cx - 3*S, cy - 7*S, cx + 5*S, cy + 1*S], fill=(255, 255, 255, 150))
    d.arc([hx - 16*S, hy + 24*S, hx + 16*S, hy + 48*S], start=25, end=155,
          fill=CYAN, width=6*S)


def e_sleepy(d, hx, hy):
    for sign in (-1, 1):  # closed, drooping
        cx, cy = ex(hx, sign), hy - 2 * S
        d.arc([cx - 15*S, cy - 6*S, cx + 15*S, cy + 18*S], start=15, end=165,
              fill=CYAN_D, width=8*S)
    d.line([hx - 14*S, hy + 38*S, hx + 14*S, hy + 38*S], fill=CYAN_D, width=6*S)
    # drifting ZzZ
    for i, sz in enumerate((26, 34, 44)):
        _draw_z(d, hx + 100*S + i * 52*S, hy - 60*S - i * 44*S + 40*S, sz*S)


def _draw_z(d, x, y, s):
    lw = max(2, s // 5)
    d.line([x, y, x + s, y], fill=CYAN, width=lw)
    d.line([x + s, y, x, y + s], fill=CYAN, width=lw)
    d.line([x, y + s, x + s, y + s], fill=CYAN, width=lw)


def e_surprised(d, hx, hy):
    for sign in (-1, 1):
        cx, cy = ex(hx, sign), hy - 6 * S
        d.ellipse([cx - 22*S, cy - 22*S, cx + 22*S, cy + 22*S], fill=CYAN)
        d.ellipse([cx - 8*S, cy - 8*S, cx + 8*S, cy + 8*S], fill=SCREEN)
    d.ellipse([hx - 11*S, hy + 20*S, hx + 11*S, hy + 48*S], outline=CYAN, width=8*S)


def e_sad(d, hx, hy):
    for sign in (-1, 1):  # down-turned eyes
        cx, cy = ex(hx, sign), hy - 4 * S
        d.arc([cx - 15*S, cy - 12*S, cx + 15*S, cy + 12*S], start=195, end=345,
              fill=CYAN_D, width=8*S)
    d.arc([hx - 18*S, hy + 22*S, hx + 18*S, hy + 44*S], start=200, end=340,
          fill=CYAN_D, width=7*S)  # frown
    d.ellipse([hx - 42*S, hy + 26*S, hx - 28*S, hy + 52*S], fill=TEAR)  # tear


def e_angry(d, hx, hy):
    for sign in (-1, 1):  # brows sloping down toward the centre
        d.line([hx + sign*46*S, hy - 40*S, hx + sign*14*S, hy - 26*S],
               fill=RED, width=9*S)
        cx = ex(hx, sign)
        rrect(d, [cx - 9*S, hy - 4*S, cx + 9*S, hy + 12*S], 5*S, fill=RED)
    d.line([hx - 24*S, hy + 44*S, hx - 8*S, hy + 36*S, hx + 8*S, hy + 44*S,
            hx + 24*S, hy + 36*S], fill=RED, width=8*S, joint="curve")  # snarl


def e_thinking(d, hx, hy):
    for sign in (-1, 1):  # squint, looking up-left
        cx, cy = ex(hx, sign), hy - 4 * S
        d.arc([cx - 16*S, cy - 8*S, cx + 16*S, cy + 14*S], start=205, end=335,
              fill=CYAN_D, width=8*S)
        d.line([cx - 14*S, cy - 18*S, cx + 8*S, cy - 10*S], fill=CYAN_D, width=6*S)
    d.arc([hx - 10*S, hy + 32*S, hx + 10*S, hy + 50*S], start=130, end=310,
          fill=CYAN_D, width=6*S)
    # thought bubble with a little dot trail
    d.ellipse([hx + 146*S, hy - 104*S, hx + 216*S, hy - 44*S], outline=GREY, width=4*S)
    for i, (bx, by, r) in enumerate(((128, -18, 8), (110, -34, 6), (94, -46, 4))):
        d.ellipse([hx + bx*S - r*S, hy + by*S - r*S, hx + bx*S + r*S, hy + by*S + r*S],
                  outline=GREY, width=3*S)
    for i in range(3):
        cx2 = hx + 162*S + i * 18*S
        d.ellipse([cx2 - 7*S, hy - 84*S - 7*S, cx2 + 7*S, hy - 84*S + 7*S], fill=GREY)


def e_confused(d, hx, hy):
    for sign, big in ((-1, True), (1, False)):  # one big eye, one small
        r0 = 20*S if big else 11*S
        cx, cy = ex(hx, sign), hy - 4 * S
        d.arc([cx - r0, cy - r0, cx + r0, cy + r0], start=60, end=340,
              fill=CYAN_D, width=6*S)
        d.ellipse([cx - 7*S, cy - 7*S, cx + 7*S, cy + 7*S], fill=CYAN_D)
    # floating question mark
    d.line([hx + 150*S, hy - 96*S, hx + 154*S, hy - 68*S], fill=GREY, width=7*S)
    d.arc([hx + 138*S, hy - 108*S, hx + 168*S, hy - 80*S], start=280, end=140,
          fill=GREY, width=6*S)
    d.ellipse([hx + 146*S, hy - 56*S, hx + 162*S, hy - 40*S], fill=GREY)


def e_annoyed(d, hx, hy):
    for sign in (-1, 1):  # flat, unimpressed
        cx = ex(hx, sign)
        d.line([cx - 15*S, hy + 0*S, cx + 15*S, hy + 0*S], fill=CYAN_D, width=9*S)
    d.line([hx - 18*S, hy + 38*S, hx + 18*S, hy + 38*S], fill=CYAN_D, width=7*S)
    # manga anger tick near the top-right of the head
    d.arc([hx + 124*S, hy - 108*S, hx + 150*S, hy - 80*S], start=60, end=210,
          fill=RED, width=5*S)
    d.line([hx + 156*S, hy - 84*S, hx + 182*S, hy - 108*S], fill=RED, width=6*S)
    d.line([hx + 156*S, hy - 100*S, hx + 182*S, hy - 76*S], fill=RED, width=6*S)


EMOTIONS = {
    "happy":      (e_happy,      0.0,   1),
    "normal":     (e_normal,     0.0,   0),
    "sleepy":     (e_sleepy,     -6.0,  -1),
    "surprised":  (e_surprised,  0.0,   0),
    "sad":        (e_sad,        5.0,   -1),
    "angry":      (e_angry,      3.0,   0),
    "thinking":   (e_thinking,   -4.0,  3),
    "confused":   (e_confused,   -8.0,  2),
    "annoyed":    (e_annoyed,    2.0,   0),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (fn, tilt, arms) in EMOTIONS.items():
        img = robot_sprite(fn, head_tilt=tilt, arm_phase=arms)
        path = os.path.join(OUT, f"{name}.png")
        img.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
