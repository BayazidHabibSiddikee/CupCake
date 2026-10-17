# System Architecture (moved)

This document is **out of date**. It described the original design in which the Android app
connected to an **Ollama server** for inference (smollm / qwen2:0.5b) and the ESP32-S3 acted only
as a sensor node.

That design has been replaced: the companion now runs a bundled GGUF **on-device** through
llama.cpp/JNI, the ESP32 faces the user (face/motor/audio), and the app adds games, a points
economy, and Marin's demands.

## Go here instead

- **[../ARCHITECTURE.md](../ARCHITECTURE.md)** — the current, authoritative system architecture:
  layer map, AI stack, chat pipeline, game subsystem, points economy, engagement subsystem, data
  layer, device/network protocol, UI layer, DI, build system, and testing strategy.
- **[../README.md](../README.md)** — product features, screens, models/benchmarks, build & test.
- **[../architecture/README.md](../architecture/README.md)** — supplementary component design notes.
