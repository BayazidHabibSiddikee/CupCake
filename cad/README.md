# CupCake Robot — FreeCAD parametric model

`companion_robot.py` builds the full printable robot from a single parameter
table (top of the file). Edit the `P` dict to resize anything; head, torso,
servo pockets, base and arms all rebuild from the same numbers.

## Rebuilding (headless)

```bash
freecadcmd cad/companion_robot.py
```

Outputs land in `cad/out/`:

| File                 | What it is                                             |
|----------------------|--------------------------------------------------------|
| `assembly.fcstd`     | Editable FreeCAD project (walking robot, 10 parts)     |
| `assembly.step`      | Export of every part for CAM/Cura/PrusaSlicer import    |
| `head_parts.step`    | `head_shell` (OLED window + horn hex) and `head_back`   |
| `torso_parts.step`   | `torso_shell` (2 shoulder + 2 hip + neck pockets) + lid |
| `arm_right/left`     | Mirrored arm paddles (in `assembly.step`)               |
| `hip_skirt_*`        | Hip rings with the 70 deg swing window (in `assembly`)  |
| `leg_right/left`     | Legs + foot pads + stop pins (in `assembly.step`)       |

Or open the generated `.fcstd` in the GUI, tweak parameters through the
script's `P` dict, and re-run the same file inside the FreeCAD Python console
(`exec(open('cad/companion_robot.py').read())`).

## Print settings

- 0.2 mm layers, 3 perimeters for servo pockets
- 15% gyroid infill for shells
- head_shell and head_back stand upright with no supports (face window is
  bridged by the bezel lip)
- arms print flat, boss down; legs + skirts print flat (skirts face down)
- base_plate is now OPTIONAL (desk mode) — the walking robot stands on its
  own two feet

## Hardware fit

| Pocket      | Meant for                                    |
|-------------|----------------------------------------------|
| Face window | SSD1306 0.96" OLED (128x64, I2C bus on GPIO8/9)|
| Torso side walls (x4, upper)  | Two shoulder servos, shafts through Low-side walls (z=30) |
| Torso side walls (x4, lower)  | Two hip servos, shafts through the walls (z=8.6)          |
| Head underside| Yaw servo horn (GPIO4, whole head twist)                    |
| Torso rear top pocket | Optional neck servo (GPIO13)                         |
| Torso cavity  | 503035 3.7 V Li cell + TP4056 + MT3608 5 V rail              |
| Lid cavity    | ESP32-S3 devkit standing, USB-C at the lid slot              |

4 servos total (1 yaw + 2 shoulders + 2 hips) + 1 optional neck.

**Servo pin map proposal for the walking body** (update `firmware/src/motor.h`
as part of wiring — current firmware drives a single shoulder pin):

| Servo        | Pin   | Motion                          |
|--------------|-------|---------------------------------|
| Yaw (head)   | GPIO4 | head twist (unchanged)          |
| Shoulder R   | GPIO5 | right arm paddle                |
| Shoulder L   | GPIO18| LEFT arm paddle — mirrored      |
| Hip R        | GPIO23| right leg swing                 |
| Hip L        | GPIO19| left leg swing                  |
| Neck (opt.)  | GPIO13| reserved                       |

Wiring keeps to firmware/README.md: battery ADC divider on GPIO2, wire
escape slots on `head_back` for the I2C + servo leads.


## Motion limits (mechanical, not firmware)

- **Arms:** two independent shoulder pivots -> firmware swings them in
  OPPOSITE phase for a walking gait (or together for a fan-wave). Recommended
  swing <= 25 deg per side so the arm paddle always clears the hip area.
- **Legs:** each hip skirt has a **70-degree open window** (35 deg each side
  of straight-down); the stop pin moulded into the leg plate rides in that
  window and hits the ring edges physically. Even buggy firmware cannot swing
  a leg past 70 deg total. `leg_swing` in the `P` dict tunes the window.
