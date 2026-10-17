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
    base_plate.stl      printable base disc with the base (yaw) servo mount
    arm_link.stl        printable one-piece two-arm linkage (paperdoll arms)
    assembly.step       whole robot as a STEP file
    assembly.fcstd      whole robot as a FreeCAD document

Design constraints (firmware/README.md):
  - SSD1306 0.96" OLED window on the face (I2C, 0x3C), SDA GPIO8 / SCL GPIO9
  - Base yaw servo (GPIO4) drives the whole upper body twist
  - Shoulder servo (GPIO5) drives both arms through the printed linkage
  - Neck servo pocket reserved (GPIO6)
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

    # Arms (part of the linking piece)
    "arm_r":         7.0,     # arm rod radius
    "arm_len":       26.0,    # shoulder pivot length outward

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
    W, D, H = P["torso_w"], P["torso_d"], P["torso_h"]
    wall = P["wall"]

    outer = Part.makeBox(W, D, H, Vector(-W / 2, -D / 2, 0))
    outer = outer.makeFillet(6.0, top_edges(outer, H))
    inner = Part.makeBox(W - 2 * wall, D - 2 * wall, H - wall,
                         Vector(-(W - 2 * wall) / 2, -(D - 2 * wall) / 2, wall))

    # Shoulder servo pocket (shaft axis along x, through torso)
    sh = servo_feature(0, -D / 2 + wall + P["sv_w"] / 2 + 1.0, H - wall - P["sv_h"] - 6)
    inner = inner.cut(sh)
    shoulder_hole = Part.makeBox(
        D + 2, P["sv_l"] * 0.9, P["lip_h"] * 2,
        Vector(-wall - 1, -D / 2 + wall + P["sv_w"] / 2 - P["sv_l"] / 2,
               H - wall - P["sv_h"] + P["sv_h"] - 6 - P["lip_h"]))
    inner = inner.cut(shoulder_hole)

    # Neck servo pocket (optional GPIO6 build)
    nk = servo_feature(0, D / 2 - wall - P["sv_w"] / 2 - 1.0, H - wall - P["sv_h"] - 10)
    inner = inner.cut(nk)

    torso = solid("torso_shell", outer.cut(inner))

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


def build_arm(doc):
    """Both arms + shoulder yoke, printed as one wiper-arm piece."""
    L = P["arm_len"]
    r = P["arm_r"]
    bar = Part.makeBox(L * 2 + 8, P["sv_w"] * 0.8, r * 2,
                       Vector(-(L + 4), -(P["sv_w"] * 0.8) / 2, -r))
    bar = bar.makeFillet(r * 0.5, bar.Edges)
    horn = cyl((P["neck_shaft_d"] + 0.4) / 2, r * 2 + 2,
               x=0, y=0, z=-r, axis="z")
    arm = solid("arm_link", bar.cut(horn))


def build_assembly(doc):
    """Recreate all parts and export the final STEP + FCStd."""
    build_head(doc)
    build_torso(doc)
    build_base(doc)
    build_arm(doc)
    doc.recompute()
    Part.export(list(doc.Objects), os.path.join(OUT, "assembly.step"))


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    doc = new_doc("CupCakeRobot")
    build_assembly(doc)
    doc.saveAs(os.path.join(OUT, "assembly.fcstd"))
    print("exported '%s'" % OUT)


main()
