# CupCake Companion — ESP32-S3 Firmware

Firmware for the physical companion robot: SSD1306 0.96" OLED face, 2 servos
(base twist + shoulder swing), 3.7 V lithium battery. Talks to the CupCake app
over the exact protocol the app already ships:

- **BLE** — Nordic UART Service, used for provisioning + short commands.
  Matches `BleGattClient.kt` / `ProtocolDecoder.kt`.
- **WiFi + WebSocket** — board connects as a client to the phone's
  `EspWebSocketServer` at `http://<phone-ip>:8080/esp` for live faces/motors.
  Matches `network/EspWebSocketServer.kt`.

## Flashing

```bash
cd firmware
pio run -t upload            # or: pio run -t uploadfs for anything OTA later
pio run -t monitor           # 115200 baud
```

Nothing on the phone side needs changing — WiFi provisioning via the app's
Settings → Companion Device screen works out of the box.

## Wiring

| Function | GPIO | Notes |
|---|---|---|
| OLED SDA   | 8  | SSD1306 I2C addr `0x3C` |
| OLED SCL   | 9  | |
| Servo BASE (yaw twist) | 4  | 50 Hz, 500–2400 µs |
| Servo SHOULDER (arm swing via linkage) | 5 | |
| Servo NECK (reserved) | 6 | populated build only |
| Battery ADC | 2 | 2× 100 kΩ divider from battery+ |

### Power tree

```
Li-ion 3.7V cell
   │
   ├── TP4056 (charge + protection, USB-C on the back panel)
   │
   └── MT3608 boost ↔ 5.0V rail
         ├── ESP32-S3 dev board (5V pin → onboard 3.3V LDO)
         ├── Servo rail  ──← 470–1000 µF electrolytic across +5/GND
         └── (optional) MAX98357A amp + speaker behind the side grilles
```

The shoulder servo is the biggest transient draw; the bulk cap keeps the
ESP32 from browning out during arm swings. Battery sense taps *before* the
boost so the ADC sees true cell voltage (3.3 V when charging Pre-Charge etc).
Do **not** power servos from the ESP32's 3V3 pin.

### Mechanical notes
- Neck: leave ~3 mm slack-channel for the I2C + servo + power wires so head
  rotation doesn't snag. On this build head rotation is the base servo
  (body twist) — no neck servo needed.
- The alternately-driven two-arm linkage means "wave" and "dance" both read
  as symmetric motion; the left-right shoulder swing drives them alternately.

## Protocol quick reference

**BLE envelope** (UTF-8 JSON on RX characteristic):

```json
{"id":"42","type":"wifi","action":"connect","params":{"ssid":"Home","password":"1234"}}
{"id":"43","type":"face","params":{"expression":"happy"}}
{"id":"44","type":"motor","params":{"action":"wave"}}
```

Reply on TX: `{"type":"resp","id":"42","status":"ok","data":{...}}`

WiFi scan result `data.networks` is a JSON *string* of `[{ssid,rssi,secure}]`,
because the app's `WifiProvisioner.parseScan` expects that shape.

Binary frames over the same characteristic:
- `0x04 + int32 ts` ← phone ping; we reply `0x84 + int32 ts` (pong).
- `0x02 + float voltage + float pct` (little-endian, 9 bytes) — battery,
  matching `ProtocolDecoder.BATTERY_PAYLOAD_SIZE`.

**WebSocket envelope** (board connects out to phone:8080/esp):

```json
{"type":"face","payload":"{\"expression\":\"happy\",\"duration\":2000}","timestamp":...}
{"type":"motor","payload":"{\"action\":\"dance\"}","timestamp":...}
```

We reply to `ping` with `pong`, and push `{"type":"status","payload":"{...}"}` on connect.

## Face vocabulary

Firmware accepts both vocabularies so nothing needs a mapping layer:

| App FaceExpression | App emotion tags / stems | Face |
|---|---|---|
| neutral | normal, confident, eating | neutral |
| happy | laugh, happy | happy |
| excited / surprised | excited, shock | wide eyes + open mouth |
| sleepy | sleepy | closed + zZz |
| sad | sad | tears + frown |
| angry / rage | angry, annoyed | brows + frown + anger vein |
| thinking | thinking | eyes-up + drifting dots |
| confused | confused | tilted pupils + "?" |
| wink | flirty | wink |
| heart_eyes | lovely | hearts + smile |

Unknown names → neutral.
