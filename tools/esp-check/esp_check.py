#!/usr/bin/env python3
"""End-to-end check for the CupCake -> ESP companion link.

Runs against a phone with the debug app installed (USB/adb). It:

  1. forwards the app's WebSocket/HTTP server (port 8080) to the host,
  2. reads the emotion index the board would discover,
  3. fetches every emotion image and asserts the served bytes are byte-identical
     to the PNGs bundled in the repo (proof the board shows the SAME art),
  4. opens the ESP WebSocket and prints live FACE messages, so you can watch the
     emotion change while the companion talks.

Usage:
    tools/esp-check/esp_check.py [--listen 30] [--serial <adb-serial>]
"""
import argparse
import base64
import hashlib
import json
import os
import socket
import subprocess
import sys
import time
import urllib.request

REPO_EMOTIONS = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "app", "src", "main", "assets", "marin", "emotions",
)
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def adb(serial, *args):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    return subprocess.run(cmd, capture_output=True, text=True)


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def http_get(port, path):
    with urllib.request.urlopen(f"http://127.0.0.1:{port}{path}", timeout=10) as r:
        return r.read()


def ws_read_frames(port, seconds, path="/esp"):
    """Minimal RFC6455 text-frame reader (no third-party deps)."""
    key = base64.b64encode(os.urandom(16)).decode()
    sock = socket.create_connection(("127.0.0.1", port), timeout=5)
    req = (
        f"GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n"
        f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
    )
    sock.sendall(req.encode())
    # Consume the HTTP 101 response so frame parsing starts at real frames.
    header = b""
    while b"\r\n\r\n" not in header:
        part = sock.recv(1)
        if not part:
            sock.close(); return
        header += part
    if b"101" not in header.split(b"\r\n")[0]:
        print(f"  handshake failed: {header.splitlines()[0].decode(errors='replace')}")
        sock.close(); return
    sock.settimeout(0.5)
    end = time.time() + seconds
    buf = b""
    last_ping = time.time()
    while time.time() < end:
        if time.time() - last_ping > 10:
            sock.sendall(b"\x89\x80" + os.urandom(4))  # masked ping, ignored by server
            last_ping = time.time()
        try:
            chunk = sock.recv(4096)
        except socket.timeout:
            continue
        if not chunk:
            break
        buf += chunk
        while len(buf) >= 2:
            opcode = buf[0] & 0x0F
            length = buf[1] & 0x7F
            offset = 2
            if length == 126:
                if len(buf) < 4:
                    break
                length = int.from_bytes(buf[2:4], "big")
                offset = 4
            elif length == 127:
                if len(buf) < 10:
                    break
                length = int.from_bytes(buf[2:10], "big")
                offset = 10
            if len(buf) < offset + length:
                break
            payload = buf[offset:offset + length]
            buf = buf[offset + length:]
            if opcode == 0x1:
                yield payload.decode("utf-8", "replace")
            elif opcode == 0x8:
                return
    sock.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=float, default=0.0,
                    help="seconds to listen on the ESP websocket for FACE messages")
    ap.add_argument("--serial", default=None)
    args = ap.parse_args()

    dev = adb(args.serial, "devices")
    if "device" not in dev.stdout:
        print("no adb device attached"); return 2

    port = free_port()
    fwd = adb(args.serial, "forward", f"tcp:{port}", "tcp:8080")
    if fwd.returncode != 0:
        print("adb forward failed:", fwd.stderr.strip()); return 2
    print(f"forwarded 127.0.0.1:{port} -> phone:8080")

    failures = 0

    print("\n== emotion index ==")
    index = json.loads(http_get(port, "/assets/marin/emotions"))
    for item in index:
        print(f"  {item['emotion']:9} -> esp face '{item['face']}'  {item['image']}")

    print("\n== served images == repo assets ==")
    for item in index:
        stem = item["emotion"]
        served = http_get(port, item["image"])
        if not served.startswith(PNG_MAGIC):
            print(f"  FAIL {stem}: not a PNG"); failures += 1; continue
        local_path = os.path.join(REPO_EMOTIONS, f"{stem}.png")
        if not os.path.exists(local_path):
            print(f"  SKIP {stem}: no repo counterpart"); continue
        with open(local_path, "rb") as f:
            local = f.read()
        same = hashlib.sha256(served).hexdigest() == hashlib.sha256(local).hexdigest()
        print(f"  {'OK  ' if same else 'FAIL'} {stem}: {len(served)} bytes"
              f"{'' if same else ' (differs from repo)'}")
        failures += 0 if same else 1

    if args.listen > 0:
        print(f"\n== live FACE messages on /esp ({args.listen:g}s) ==")
        print("  (talk to the companion / start a game to trigger faces)")
        for raw in ws_read_frames(port, args.listen):
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                print(f"  raw: {raw}"); continue
            payload = msg.get("payload", {})
            if isinstance(payload, str):  # contract: payload is an encoded JSON string
                try:
                    payload = json.loads(payload)
                except json.JSONDecodeError:
                    pass
            if msg.get("type") == "face":
                print(f"  FACE expression={payload.get('expression')} "
                      f"emotion={payload.get('emotion')} image={payload.get('image')}")
            else:
                print(f"  {msg.get('type')}: {json.dumps(payload)[:120]}")

    adb(args.serial, "forward", "--remove", f"tcp:{port}")
    print(f"\n{'FAILURES: %d' % failures if failures else 'all checks passed'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
