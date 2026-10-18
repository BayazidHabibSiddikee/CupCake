#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CupCake companion robot — parametric FreeCAD model.

Run headless (no GUI needed):

    freecadcmd cad/companion_robot.py

Outputs to cad/out/:
    head_shell.stl      printable head, face window + servo neck shaft hole
    head_back.stl       printable head rear cap with I2C / power wire ports
    torso_shell.stl     printable body shell, shoulder servo pocket + arm pivots
    torso_lid.stl       printable access lid (battery + ESP32-S3)
    hip_skirt_*         stationary hip rings with the 70 deg swing window
    leg_*               legs with stop pins (hard-limited +/-35 deg swing)
    arm_right / arm_left  two mirrored arm paddles on independent shoulder pivots
    assembly.step       whole robot as a STEP file
    assembly.fcstd      whole robot as a FreeCAD document

Design constraints (firmware/README.md):
  - SSD1306 0.96" OLED window on the face (I2C, 0x3C), SDA GPIO8 / SCL GPIO9
  - Base yaw servo (GPIO4) drives the whole upper body twist
  - TWO shoulder servos, one per arm, mirrored: arms swing in OPPOSITE phase
    for a real walking gait (or together for a fan-wave)
  - TWO hip servos: legs swing fore/aft, HARD limited to 70 deg total
    (+/-35) by printed skirt slots + stop pins — no firmware trust
  - Neck servo pocket reserved (GPIO13)
  - 3.7 V cell -> TP4056 -> MT3608 5 V rail; battery bay sized for ~503035 cell
  - Two printed halves per shell (no supports needed if printed as shown)

All dimensions are in millimeters.
"""

import os
import math

import FreeCAD as App
import Part
from FreeCAD import Vector, Rotation, Placement

# ---------------------------------------------------------------------------
# Parameters — tweak these, everything rebuilds
# ---------------------------------------------------------------------------

P = {
    # Global fit
    "wall":          2.0,     # shell wall thickness
    "clearance":     0.35,    # printed fit clearance between parts
    "lip_h":         2.5,     # overlap depth of lids / caps

    # Head (holds the OLED face)
    "head_w":        52.0,    # head width
    "head_d":        40.0,    # head depth
    "head_h":        42.0,    # head height

    # SSD1306 0.96" OLED facial window
    "face_w":        24.0,    # window width over the display
    "face_h":        12.5,    # window height over the display
    "face_y":        +6.0,    # window centre height above head centre
    "screen_x":      -7.0,    # display centre x offset (left eye accent)

    # Neck / yaw
    "neck_shaft_d":  9.0,     # base yaw output shaft through neck
    "neck_shaft_h":  4.0,

    # Torso (battery + board)
    "torso_w":       56.0,
    "torso_d":       44.0,
    "torso_h":       52.0,

    # Battery bay (503035-style Li cell + boost + charger)
    "batt_w":        35.0,
    "batt_d":        32.0,
    "batt_h":        7.0,

    # ESP32-S3 devkit (approx 25.5 x 54 x 12 with pins down)
    "esp_w":         27.0,
    "esp_d":         54.0,
    "esp_h":         15.0,

    # Servo pocket — MG90S/SG90 body
    "sv_l":          23.0,
    "sv_w":          12.5,
    "sv_h":          14.0,    # below the horn flange
    "sv_flange_l":   32.5,
    "sv_flange_h":   2.5,
    "sv_shaft_off":  2.0,     # horn centre from body face
    "sv_wire_slot":  4.0,     # wire escape slot

    # Arms — TWO mirrored shoulder servos, so the arms can swing in OPPOSITE
    # phase (one forward while the other goes back) exactly like walking.
    "shoulder_z":    30.0,    # shoulder pivot height in the torso
    "arm_hang":      34.0,    # arm paddle drop below the shoulder pivot
    "arm_t":         4.0,     # arm paddle thickness (along x)
    "arm_pw":        13.0,    # arm paddle width (along y)
    "boss_r":        6.5,     # pivot boss radius on arm/leg plates

    # Legs — hip servos low in the torso sides, swing limited to 70 deg total
    # (+/-35 deg) by a printed skirt slot + pin on each leg. HARD stops, not
    # firmware trust: the pin physically hits the skirt twice per stride.
    "hip_z":         8.6,     # hip pivot height in the torso
    "leg_h":         24.0,    # beam drop below the hip pivot
    "leg_t":         4.0,     # leg plate thickness (along x)
    "leg_w":         12.0,    # leg beam width (along y)
    "foot_h":        6.0,
    "foot_w":        18.0,    # foot pad width  (along y)
    "foot_t":        9.0,     # foot pad thickness (along x)
    "stop_r":        10.0,    # radius of the stop pin from the pivot
    "stop_pin_r":    1.2,
    "slot_r":        8.5,     # slot inner radius
    "slot_w":        3.0,     # slot radial width
    "skirt_r":       13.5,    # hip skirt ring outer radius
    "leg_swing":     70.0,    # HARD mechanical limit: total swing, degrees

    # Base
    "base_r":        38.0,
    "base_h":        6.0,
}

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
os.makedirs(OUT, exist_ok=True)


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------

def top_edges(shape, z, tol=1e-6):
    """All edges lying exactly in the z plane (works for lines and circles)."""
    out = []
    for e in shape.Edges:
        zs = [v.Z for v in e.Vertexes] or [0.0]
        if abs(max(zs) - z) < tol and abs(min(zs) - z) < tol:
            out.append(e)
    return out


def new_doc(name):
    doc = App.newDocument(name)
    return doc


def solid(name, shape, colour=None):
    obj = App.ActiveDocument.addObject("Part::Feature", name)
    obj.Shape = shape
    if colour:
        obj.ViewObject.ShapeColor = colour
    return obj


def cyl(r, h, x=0, y=0, z=0, axis="z"):
    c = Part.makeCylinder(r, h, Vector(x, y, z), {
        "z": Vector(0, 0, 1), "x": Vector(1, 0, 0), "y": Vector(0, 1, 0)}[axis])
    return c


def servo_feature(x, y, z, clearance=P["clearance"]):
    """Clearance block for an MG90/SG90 servo body + flange, shaft along +z."""
    L, W, H, FL, FH = P["sv_l"], P["sv_w"], P["sv_h"], P["sv_flange_l"], P["sv_flange_h"]
    body = Part.makeBox(L + clearance, W + clearance, H + clearance,
                        Vector(x - (L + clearance) / 2, y - (W + clearance) / 2, z))
    flange = Part.makeBox(FL, W + clearance, FH,
                          Vector(x - FL / 2, y - (W + clearance) / 2, z + H))
    horn = cyl((7.5 + clearance) / 2, P["lip_h"] * 3,
               x=x, y=y, z=z + H - P["lip_h"])
    return body.fuse(flange).fuse(horn)


def servo_feature_x(x_face, y, zc, clearance=P["clearance"]):
    """MG90/SG90 clearance solid, shaft along +x THROUGH the wall at (x_face, y, zc).
    Body occupies the interior side (x < x_face); flange presses the inner wall.
    Returns (feature, hole_axis) — the caller also cuts the through-wall horn hole."""
    L, W, H, FL, FH = P["sv_l"], P["sv_w"], P["sv_h"], P["sv_flange_l"], P["sv_flange_h"]
    x0 = x_face - (H + clearance)
    y0 = y - (L + clearance) / 2
    z0 = zc - (W + clearance) / 2
    body = Part.makeBox(H + clearance, L + clearance, W + clearance, Vector(x0, y0, z0))
    flange = Part.makeBox(FH, FL, W + clearance, Vector(x_face - FH, y - FL / 2, z0))
    horn = Part.makeCylinder((7.5 + clearance) / 2, P["lip_h"] * 3,
                             Vector(x_face - P["lip_h"], y, zc), Vector(1, 0, 0))
    return body.fuse(flange).fuse(horn)


def wall_horn_hole(zc, y=0.0, span=1.5):
    """Horn shaft hole through BOTH side walls at height zc (one cut, two holes)."""
    return Part.makeCylinder((P["neck_shaft_d"] + 0.4) / 2, P["torso_w"] * span,
                             Vector(-P["torso_w"] * span / 2, y, zc), Vector(1, 0, 0))


def yz_ring_sector(r_in, r_out, h, a_total_deg, y0, z0):
    """Annular sector in the y-z plane (rotation axis = x). Angle 0 = straight
    down; sweeps [0 +/- a_total/2] measured from down toward +y (front).
    Solid extrudes h along +x from x=0 (translate afterwards)."""
    seg = 12
    half = a_total_deg / 2.0
    pts = []
    for i in range(seg + 1):
        a = math.radians(-half + (2.0 * half) * i / seg)
        pts.append(Vector(0, y0 + r_out * math.sin(a), z0 - r_out * math.cos(a)))
    for i in range(seg, -1, -1):
        a = math.radians(-half + (2.0 * half) * i / seg)
        pts.append(Vector(0, y0 + r_in * math.sin(a), z0 - r_in * math.cos(a)))
    face = Part.Face(Part.makePolygon(pts + [pts[0]]))
    return face.extrude(Vector(h, 0, 0))


def shell(name, outer, inner):
    """Hollow a solid shell in one go (outer minus inner)."""
    s = outer.cut(inner)
    return s


# ---------------------------------------------------------------------------
# Parts
# ---------------------------------------------------------------------------

def build_head(doc):
    W, D, H = P["head_w"], P["head_d"], P["head_h"]
    wall = P["wall"]

    # --- head shell (front) ---
    outer = Part.makeBox(W, D, H, Vector(-W / 2, -D / 2, -H / 2))
    # rounded look: chamfer the top
    outer = outer.makeFillet(4.0, top_edges(outer, H / 2))
    inner = Part.makeBox(W - 2 * wall, D - 2 * wall, H + 0.1,
                         Vector(-(W - 2 * wall) / 2, -(D - 2 * wall) / 2, -H / 2))

    cut = servo_feature(0, 0, -H / 2)  # neck passage cleared inside
    inner = inner.cut(cut)

    # face window (OLED)
    face = Part.makeBox(P["face_w"] + 0.6, D + 2.0, P["face_h"] + 0.6,
                        Vector(-P["face_w"] / 2 - 0.3 + P["screen_x"],
                               -D / 2 - 1.0,
                               P["face_y"] - (P["face_h"] + 0.6) / 2))
    # left/right heart-spacer accents cut as small windows too
    shell_s = outer.cut(inner).cut(face)

    # servo horn hole in the centre for the yaw shaft
    shell_s = shell_s.cut(cyl((P["neck_shaft_d"] + 0.2) / 2, D,
                              x=0, z=-H / 2, axis="y"))
    head = solid("head_shell", shell_s)

    # --- head back cap (I2C + power ports) ---
    back = Part.makeBox(W, D * 0.35, H * 0.75,
                        Vector(-W / 2, -D * 0.35 / 2, -H * 0.75 / 2))
    backhole1 = Part.makeBox(10, D + 2.0, 5,
                             Vector(-14, -D - 1, -10))     # I2C wires
    backhole2 = Part.makeBox(8, D + 2.0, 4,
                             Vector(6, -D - 1, -12))       # servo wires
    back_s = back.cut(backhole1).cut(backhole2)
    back_cap = solid("head_back", back_s)

    doc.recompute()
    Part.export([head, back_cap], os.path.join(OUT, "head_parts.step"))


def build_torso(doc):
    """Shell with servo pockets for BOTH shoulders (upper sides) and BOTH hips
    (lower sides). Shoulder and hip shafts are colinear y=0 / along x; hip sits
    low (z=hip_z), shoulder high (z=shoulder_z) — the arm paddle clears the
    hip skirt because they live in different x-planes outboard."""
    W, D, H = P["torso_w"], P["torso_d"], P["torso_h"]
    wall = P["wall"]

    outer = Part.makeBox(W, D, H, Vector(-W / 2, -D / 2, 0))
    outer = outer.makeFillet(6.0, top_edges(outer, H))
    inner = Part.makeBox(W - 2 * wall, D - 2 * wall, H - wall,
                         Vector(-(W - 2 * wall) / 2, -(D - 2 * wall) / 2, wall))

    # Neck servo pocket (reserved, GPIO13)
    nk = servo_feature(0, D / 2 - wall - P["sv_w"] / 2 - 1.0, H - wall - P["sv_h"] - 10)
    inner = inner.cut(nk)

    # Two shoulder servo pockets: shafts point +/-x through the side walls.
    face_in = W / 2 - wall
    for side in (+1, -1):
        inner = inner.cut(servo_feature_x(side * face_in, 0, P["shoulder_z"]))

    # Two hip servo pockets, same shaft line but low in the torso.
    for side in (+1, -1):
        inner = inner.cut(servo_feature_x(side * face_in, 0, P["hip_z"]))

    torso_shell = outer.cut(inner)

    # Horn shaft holes through both side walls (one cut per height = 4 holes).
    torso_shell = torso_shell.cut(wall_horn_hole(P["shoulder_z"]))
    torso_shell = torso_shell.cut(wall_horn_hole(P["hip_z"]))

    torso = solid("torso_shell", torso_shell)

    # --- lid ---
    lid = Part.makeBox(W - 2 * wall, D - 2 * wall, wall,
                       Vector(-W / 2 + wall, -D / 2 + wall, H))
    lid_solid = solid("torso_lid", lid)
    doc.recompute()

    Part.export([torso, lid_solid], os.path.join(OUT, "torso_parts.step"))


def build_base(doc):
    R, H = P["base_r"], P["base_h"]
    outer = cyl(R, H)
    outer = outer.makeFillet(3.0, top_edges(outer, H))
    inner = Part.makeBox(P["sv_l"] + P["clearance"], P["sv_w"] + P["clearance"],
                         H - 1.2, Vector(-(P["sv_l"] + P["clearance"]) / 2,
                                         -(P["sv_w"] + P["clearance"]) / 2, 1.2))
    base_shape = outer.cut(inner)
    base_shape = base_shape.cut(cyl((P["neck_shaft_d"] + 0.2) / 2, H, z=0))
    solid("base_plate", base_shape)
    doc.recompute()


def build_arms(doc):
    """Two separate arm paddles on mirrored shoulder pivots, so firmware can
    swing them in OPPOSITE phase (walking arms) or together (fan-wave).
    Both printed flat on the bed, no supports."""
    face_out = P["torso_w"] / 2.0
    z_p = P["shoulder_z"]
    t = P["arm_t"]
    x0 = face_out + 6.0               # inboard face of the paddle (toward torso)

    for side, name in ((+1, "arm_right"), (-1, "arm_left")):
        # paddle plane: parallel to the torso wall, hangs from the pivot
        px0 = x0 * side               # inboard coordinate (right: +x side)
        px1 = (x0 + t) * side         # outboard coordinate
        lo, hi = min(px0, px1), max(px0, px1)
        paddle = Part.makeBox(t, P["arm_pw"], P["arm_hang"] + 6,
                              Vector(lo, -P["arm_pw"] / 2, z_p - P["arm_hang"]))
        # round the hanging end (only the two LONG edges — full-border fillets
        # go invalid on the thin 4 mm paddle)
        z_bot = z_p - P["arm_hang"]
        lo_edges = [e for e in paddle.Edges
                    if abs(max(v.Z for v in e.Vertexes) - z_bot) < 1e-6
                    and abs(min(v.Z for v in e.Vertexes) - z_bot) < 1e-6
                    and abs(abs(e.tangentAt(e.FirstParameter).x) - 1) < 1e-6]
        try:
            cand = paddle.makeFillet(1.5, lo_edges)
            if cand.isValid() and cand.Volume > 0:
                paddle = cand
        except Exception:
            pass
        # boss straddles the pivot; horn nests into the pocket on the inboard face
        if side > 0:
            boss = Part.makeCylinder(P["boss_r"], 4.5,
                                     Vector(x0 - 4.5, 0, z_p), Vector(1, 0, 0))
        else:
            boss = Part.makeCylinder(P["boss_r"], 4.5,
                                     Vector(-x0, 0, z_p), Vector(1, 0, 0))
        pocket = Part.makeCylinder(4.0, 2.8,
                                   Vector(px0, 0, z_p), Vector(-side, 0, 0))
        hub = Part.makeCylinder(1.5, t + 8,
                                Vector(px0 - 4 * side, 0, z_p), Vector(side, 0, 0))
        arm = paddle.fuse(boss).cut(pocket).cut(hub)
        solid(name, arm)
    doc.recompute()


def build_legs(doc):
    """Two legs hanging from hip pivots low in the torso sides. Swing is HARD
    limited to +/-35 deg (70 total): a skirt ring on each torso wall has a
    70-degree open window; a stop pin on the leg rides in that window and hits
    the ring edges physically. No firmware trust required."""
    z_p = P["hip_z"]
    face_out = P["torso_w"] / 2.0
    skirt_h = 3.0
    ring_in = P["slot_r"]                       # 8.5
    ring_out = P["skirt_r"]                     # 13.5
    stop_r = P["stop_r"]                        # 10.0, inside the window band

    for side, name_leg, name_skirt in ((+1, "leg_right", "hip_skirt_right"),
                                       (-1, "leg_left", "hip_skirt_left")):
        # ---- skirt: 290 deg ring (gap of 70 deg facing straight down), on the wall
        skirt = yz_ring_sector(ring_in, ring_out, skirt_h, 360.0 - P["leg_swing"], 0.0, z_p)
        skirt = skirt.rotate(Vector(0, 0, z_p), Vector(1, 0, 0), 180)   # gap now open DOWN
        skirt = skirt.translate(Vector(side * face_out, 0, 0))
        # two bolt lugs up on the ring, screwed into the torso wall face
        for sy in (+1, -1):
            lug = Part.makeBox(skirt_h, 4.0, 4.5,
                               Vector(side * face_out, min(sy * 9, sy * 13), z_p + 5.0))
            skirt = skirt.fuse(lug)
        # horn hub passes through the ring centre
        solid(name_skirt, skirt)

        # ---- leg (rotating piece), plate band x [face_out+3.35 .. face_out+7.35]
        x_in = side * (face_out + 3.35)
        # disc around the pivot (must stay inside the ring's centre hole)
        disc = Part.makeCylinder(9.0, P["leg_t"],
                                 Vector(x_in if side > 0 else x_in - P["leg_t"], 0, z_p),
                                 Vector(1, 0, 0))
        # connector tab down to the stop pin level
        tab = Part.makeBox(P["leg_t"], 3.2, 6.0,
                           Vector(x_in if side > 0 else x_in - P["leg_t"],
                                  -1.6, z_p - stop_r - 1.5))
        # stop pin reaching back into the skirt window
        pin = Part.makeCylinder(P["stop_pin_r"], 4.4,
                                Vector(side * (face_out + 0.6), 0, z_p - stop_r),
                                Vector(side, 0, 0))
        leg = disc.fuse(tab).fuse(pin)
        # horn pocket on the plate inboard face + hub screw through
        pocket = Part.makeCylinder(4.0, 3.0,
                                   Vector(side * (face_out + 3.35), 0, z_p),
                                   Vector(side, 0, 0))
        hub = Part.makeCylinder(1.5, 12,
                                Vector(side * (face_out - 3), 0, z_p), Vector(side, 0, 0))
        # beam down + foot pad
        z_beam_top = z_p - 9.0 + 1.0
        beam = Part.makeBox(P["leg_t"], P["leg_w"], P["leg_h"],
                            Vector(x_in if side > 0 else x_in - P["leg_t"],
                                   -P["leg_w"] / 2, z_beam_top - P["leg_h"]))
        pad = Part.makeBox(P["foot_t"], P["foot_w"], P["foot_h"],
                           Vector(x_in if side > 0 else x_in - P["leg_t"] - (P["foot_t"] - P["leg_t"]),
                                  -P["foot_w"] / 2, z_beam_top - P["leg_h"] - P["foot_h"]))
        leg = leg.fuse(beam).fuse(pad).cut(pocket).cut(hub)
        solid(name_leg, leg)
    doc.recompute()


def build_assembly(doc):
    """Recreate all parts and export the final STEP + FCStd. The old desk
    base_plate is still exported separately but is NOT part of the walking
    assembly anymore — the robot stands on its own two feet now."""
    build_head(doc)
    build_torso(doc)
    build_base(doc)
    build_arms(doc)
    build_legs(doc)
    doc.recompute()
    # walking assembly = head + torso + arms + legs (base plate optional desk mode)
    Part.export([o for o in doc.Objects if o.Name != "base_plate"],
                os.path.join(OUT, "assembly.step"))


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    doc = new_doc("CupCakeRobot")
    build_assembly(doc)
    doc.saveAs(os.path.join(OUT, "assembly.fcstd"))
    print("exported '%s'" % OUT)


main()
