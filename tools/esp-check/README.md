# esp-check

Host-side checker for the CupCake → ESP companion link. No app-side changes needed.

```sh
tools/esp-check/esp_check.py --listen 30     # with 30s of live FACE watching
tools/esp-check/esp_check.py                 # one-shot device/web checks
```

With the debug app running on a USB-attached phone it:

1. forwards the app's server (port 8080) to the host,
2. reads the emotion index the board discovers at `/assets/marin/emotions`,
3. fetches every emotion image and asserts it is **byte-identical** to the PNG in
   `app/src/main/assets/marin/emotions/` — proof the board and the phone show the
   same art,
4. opens `/esp` and prints live `type=face` messages (`expression`, `emotion`,
   `image`) as the companion talks.

Typical FACE stream while sending a message:

```
FACE expression=thinking emotion=thinking image=/assets/marin/emotions/thinking.png
FACE expression=neutral  emotion=normal   image=/assets/marin/emotions/normal.png
FACE expression=heart_eyes emotion=lovely image=/assets/marin/emotions/lovely.png
```

Wire note: `payload` is an encoded JSON **string** in both directions (see
`IncomingMessage.payload`), so consumers double-parse it.
