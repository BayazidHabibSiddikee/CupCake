# CupCake Robot — FreeCAD parametric model

`companion_robot.py` builds the full printable robot from a single parameter
table (top of the file). Edit the `P` dict to resize anything; head, torso,
servo pockets, base and arms all rebuild from the same numbers.

## Rebuilding (headless)

```bash
freecadcmd cad/companion_robot.py
```

Outputs land in `cad/out/`:

| File              | What it is                                            |
|-------------------|-------------------------------------------------------|
| `assembly.fcstd`  | Editable FreeCAD project (all 6 parts)                |
| `assembly.step`   | Export of every part for CAM/Cura/PrusaSlicer import   |
| `head_parts.step` | `head_shell` (OLED window + horn hex) and `head_back`  |
| `torso_parts.step`| `torso_shell` (shoulder + neck pockets) and `torso_lid`|

Or open the generated `.fcstd` in the GUI, tweak parameters through the
script's `P` dict, and re-run the same file inside the FreeCAD Python console
(`exec(open('cad/companion_robot.py').read())`).

## Print settings

- 0.2 mm layers, 3 perimeters for servo pockets
- 15% gyroid infill for shells
- head_shell and head_back stand upright with no supports (face window is
  bridged by the bezel lip)
- arm_link prints flat, arms down; base_plate prints bottom-up

## Hardware fit

| Pocket      | Meant for                                    |
|-------------|----------------------------------------------|
| Face window | SSD1306 0.96" OLED (128x64, I2C bus on GPIO8/9)|
| Neck hole   | Yaw servo horn output shaft (GPIO4, whole head twist) |
| Torso front top pocket | Shoulder servo (GPIO5) driving `arm_link` |
| Torso rear top pocket  | Optional neck servo (GPIO6)              |
| Torso cavity| 503035 3.7 V Li cell + TP4056 + MT3608 5 V rail |
| Lid cavity  | ESP32-S3 devkit standing, USB-C at the lid slot |

Wiring keeps to firmware/README.md: battery ADC divider on GPIO2, wire
escape slots on `head_back` for the I2C + servo leads.
