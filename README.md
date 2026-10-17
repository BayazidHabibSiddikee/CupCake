# CupCake

An Android **AI companion** app: a cute, clingy, competitive gyaru (Marin Kitagawa / "LilTii")
that runs a small language model **on-device**, talks out loud, emotes on a physical ESP32
gadget, chats with you, plays 15 mini-games against you, and nags you for snacks — which cost
points you earn by winning.

- **No cloud required.** The companion runs on a bundled GGUF (~271 MiB, Qwen2.5-0.5B fine-tune) through llama.cpp via JNI. Remote OpenAI / Ollama / vLLM providers are optional.
- **Voice + face.** Android `TextToSpeech` streams PCM to the board; emotion tags in every reply drive the on-screen art, the floating pet overlay, and the ESP32's own face/motor moves.
- **Games that escalate.** Win too much and Marin gets genuinely annoyed: trash-talk tiers up, her difficulty adapts, and her mood changes what she says.

---

## Table of contents

- [Feature tour](#feature-tour)
- [Screens](#screens)
- [The games](#the-games)
- [The points economy](#the-points-economy)
- [Marin's demands](#marins-demands)
- [On-device models](#on-device-models)
- [Building](#building)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Configuration](#configuration)
- [Docs](#docs)
- [License](#license)

---

## Feature tour

### On-device companionship
- **Bundled LilTii LLM** (`models/liltii-v0.2-v3-q4_0.gguf`) loaded through `LlamaEngine` (JNI, CMake, NEON, OpenMP off) — see [ARCHITECTURE.md](ARCHITECTURE.md#ai-stack).
- **`[emotion] *action* reply` contract.** Every reply starts with a bracketed mood and an action beat. `EmotionAssets` maps the tag to 2D art and to an ESP face; the same tag picks the TTS voice language.
- **Banglish personality** — replies mix English and casual Bangla; `BanglishTranslator` converts dry model output into in-character gyaru lines when needed.
- **Voice** — `TtsManager` auto-detects Bangla vs. Latin text, streams PCM chunks (`speakStreaming`) to the ESP32 for audio playback while the phone speaks.
- **Chat streaming** with per-conversation model config, system prompt, and token-by-token rendering.
- **Emotion classifier** (`EmotionClassifier`) scores joy / affection / jealousy / surprise / sadness / anger / thinking from text, used by both chat and the games.

### Character system
- Built-in **Marin Kitagawa** character plus **custom characters** you create or import.
- **SillyTavern card** import/export (`SillyTavernCardParser`) — bring a friend's persona file and it becomes a character with avatar, prompt, and greeting.
- Per-character system prompt, avatar (static or animated GIF via Coil), and personality string.
- **User profile** (`UserProfileStore`) injected into the prompt: name, age, relationship, likes/dislikes — Marin addresses you correctly.

### Physical gadget integration
- **ESP32-S3 over WebSocket** (`EspWebSocketServer`, Ktor): `face`, `motor`, `audio`, and `command` frames.
- **BLE (Nordic UART Service)** (`BleGattClient`) for provisioning and direct serial-ish comms, with a binary `ProtocolDecoder` for sensor/IMU/battery/event packets.
- **WiFi provisioning** flow (`WifiProvisioning`, `WifiSetupScreen`) to hand credentials to the board.
- **Floating pet overlay** (`ShimejiPetService`) — Marin sits on top of other apps and reacts.
- **Face/motor vocabulary:** `neutral, happy, sad, angry, rage, excited, thinking, sleepy, wink, heart_eyes` and `idle, nod, shake, tantrum, dance, wave, look_left, look_right`.

### Remote providers (optional)
`ModelConfig.ModelProvider`: `LOCAL_LLAMA`, `CUSTOM_OPENAI`, `CUSTOM_OLLAMA`, `CUSTOM_VLLM`, `CUSTOM_OTHER`.
`OllamaEngine` streams NDJSON; OpenAI-compatible endpoints stream SSE. **Games always stay on-device** so a half-configured cloud key can never turn the bot mute.

### Energy & Pro (freemium shell)
`EnergyManager` grants a 30-minute budget persisted to `energy_state.json`; messages cost 30 s, games cost 1 min (games are never blocked). Ad-reward and `billing-ktx` Pro upgrade are wired at the shell level.

---

## Screens

| Route | Screen | Purpose |
|-------|--------|---------|
| `home` | `HomeScreen` | Companion hub: bond level, XP bar, coins, Marin's active demand, game grid, quick actions (pat / snack / tease / dance). |
| `chat/{characterId}` | `ChatScreen` | Streaming chat, live emotion face, TTS, tool/history controls. |
| `games?gameType={gameType}` | `GameScreen` | Game picker + all 15 board UIs, anger/difficulty banner, bot bubble. |
| `points` | `PointsScreen` | Level, XP progress, coins, per-game stats, achievements. |
| `characters` | `CharacterScreen` | Manage/select characters, import & export SillyTavern cards. |
| `systemprompt/{promptId?}` | `SystemPromptScreen` | Edit system prompts, attach images with OCR-derived text. |
| `settings` | `SettingsScreen` | Model providers/endpoints, keys, generation params, dev tools entry. |
| `device/{deviceId}` | `DeviceScreen` | Scan/connect the ESP32, live sensor + battery, send face/motor commands. |
| `wifi-setup` | `WifiSetupScreen` | Push WiFi credentials to the board over BLE. |
| `liltii-eval` | `LilTiiEvalScreen` | On-device model grader (runs 20 prompts, scores tag/action/Bangla compliance). |

---

## The games

15 games in `Game.GameType`, all playable against the bot and all wired into the same
reaction/points/anger plumbing:

| Game | Notes |
|------|-------|
| Tic-Tac-Toe | minimax engine |
| Tic-Tac-Toe 4×4 | deeper search, adaptive |
| Connect Four | search engine, adaptive |
| Chess | **full legal engine** (castling, en passant, promotion, checkmate/stalemate/draws), perft-verified, negamax AI — see `ChessEngine` |
| Checkers-free classics: Hangman, Word Scramble, Memory Match, Number Base, Reaction Time, Math Quiz, Guess Number, Rock-Paper-Scissors, Mastermind, Clicker | engine-backed or reflex |

### Escalating anger + adaptive difficulty
- **`BotTrashTalk`** — deterministic Banglish taunts tiered by how many games you've won in a row (`CALM → FURIOUS`), plus `welcomeLine` / `gloatLine` / `rallyLine`.
- **`JealousyTracker`** — tracks win streaks and computes an **adaptive difficulty** offset applied by the engines (Tic-Tac-Toe, 4×4, Connect Four, Chess).
- **`GameReaction` + `ReactionMood`** (`NEUTRAL / HAPPY / ANGRY / SAD / GLOATING`) — the games emit a structured reaction that maps to a face, a motor action, and spoken lines.
- **`GameLLMRouter`** — generates in-game dialogue from the on-device model with a small LRU cache (game prompts repeat heavily), falling back to canned lines.
- **`ToneGuard`** — every line passes through a hostility filter (whole-word matching so "study"/"diet"/"class" survive) and a 220-char clamp before it is shown, spoken, or persisted.

### Chess
`ChessEngine.kt` is a self-contained, perft-verified engine:

| Position | Depth | Expected nodes |
|----------|-------|----------------|
| initial | 1–4 | 20 / 400 / 8902 / 197281 |
| Kiwipete | 1–3 | 48 / 2039 / 97862 |
| en-passant | 1–4 | 14 / 191 / 2812 / 43238 |
| promotions | 1–3 | 6 / 264 / 9467 |

`ChessBoardUI` is a tap-to-move board (legal-move dots, check highlight, last-move highlight,
promotion picker); the AI difficulty maps to `bestMove(difficulty)` search depth + jitter.

---

## The points economy

Two separate currencies so spending never punishes progress (see `PointsManager`, `PointsStore`):

| | Coins (`points`) | XP (`xp`) |
|---|---|---|
| Earned by | winning/playing games, interactions | same awards |
| Spent on | **Marin's demands** | never |
| Drives | nothing (wallet) | level, achievements |

- **Level curve** is monotonic and starts at zero (`level 1 = 0 XP`), so the UI never shows negative XP. Level-ups run in a `while` loop and emit `levelUpEvents`.
- **`spendPoints(amount)`** is gated — it refuses to go negative, which is what makes demands a real decision.
- **Persistence** goes through the `PointsStore` interface: `SharedPrefsPointsStore` in the app, `InMemoryPointsStore` in tests. Points, XP, level, per-game stats, and achievements all survive restarts.
- Game rewards scale with difficulty (`CHESS ×2.0`, `CONNECT_FOUR/MASTERMIND ×1.5`, `PING_PONG/4×4 ×1.3`, …) and win/loss (`×1.5` win, `×0.5` loss).

---

## Marin's demands

`DemandManager` periodically (WorkManager, ~45–150 min) raises a want, appends it to the chat,
and posts a **high-priority notification with two actions**:

```
┌────────────────────────────────────────────┐
│ Marin is hungry 🍰                          │
│ Gojo-kun, I'm starving over here! Buy me a  │
│ strawberry shortcake? 🍰🥺                   │
│  [ Fulfill (50 pts) ]      [ Ignore ]       │
└────────────────────────────────────────────┘
```

- **Fulfill** spends coins (`PointsManager.spendPoints`, or `spendPersisted` if the process was cold), raises **affection**, cancels the notification, and posts her happy follow-up.
- **Ignore** costs a little affection and gets a sulky line.
- A pending demand **expires after 6 h** (treated as ignored). Demands never repeat back-to-back.
- Catalog: hungry, snack, attention, cosplay, rematch, sleepy — each with a cost, affection reward, and sulk penalty.
- Same subsystem also powers **proactive outreach** (`ProactiveEngagementManager`, mood-aware, 1–48 h, capped at 15 messages) and the **home banner** with a live affection counter.

---

## On-device models

Two fine-tuned companion models are published on Hugging Face. Both are trained to reply in
Banglish with a leading `[emotion]` tag and an optional `*action*` beat, and are intended to be
**deliberately silly** ("dumbass by design") rather than a general assistant.

| Model | Base | Params | Q4_0 size | Hugging Face |
|-------|------|--------|-----------|--------------|
| `liltii-v0.2` | Qwen2.5-0.5B-Instruct | 494M | 271 MiB | [bayazidhs04/liltii-v0.2-qwen2.5-0.5b](https://huggingface.co/bayazidhs04/liltii-v0.2-qwen2.5-0.5b) |
| `bangla-smollm-135m-cupcake` | rnnandi/bangla-smollm-135m | 210M | 127 MiB | [bayazidhs04/bangla-smollm-135m-cupcake](https://huggingface.co/bayazidhs04/bangla-smollm-135m-cupcake) |

The app bundles **`liltii-v0.2-v3-q4_0.gguf`** (284,087,232 B) and exposes it through the
model catalog (`ModelDownloadManager`), which also prunes stale copies from older builds.

### Quantization recipe

Both models ship as **Q4_0 with quantized token embeddings and output tensor** (`--token-embedding-type Q4_0 --output-tensor-type Q4_0`, 4.50 BPW). k-quants (Q2_K/Q3_K/Q4_K) are unusable
for these architectures because the hidden/KV/vocab dimensions are not multiples of 256, so most
tensors fall back to a larger type — producing files that are both bigger and worse. The Q4_0
recipe is ~22% faster at equal parameter count than a default Q4_0 export.

### Benchmarks

llama.cpp `b11669`, 4 threads, all models Q4_0. `PPL`/`BPB` measured on an in-domain
Banglish chat corpus (40,645 bytes); `tag%`/`action%`/`chars` from 25 chat prompts with the
model's own chat template (temp 0.7, seed 42).

| Model | Params | Size | pp64 (t/s) | tg32 (t/s) | PPL ↓ | BPB ↓ | tag% | action% | reply chars |
|-------|--------|------|-----------|-----------|-------|-------|------|---------|-------------|
| **liltii-v0.2** | 494M | 271 MiB | 84.7 | **20.9** | **16.1** | **1.35** | **52** | **56** | **56** |
| Qwen2.5-0.5B-Instruct (base) | 494M | 331 MiB | 71.4 | 17.1 | 25.2 | 1.56 | 0 | 0 | 200 |
| SmolLM2-360M-Instruct | 362M | 217 MiB | 75.7 | 18.8 | 18.6 | 1.55 | 0 | 0 | 182 |
| SmolLM2-135M-Instruct | 134M | 86 MiB | 164.9 | 33.9 | 22.3 | 1.64 | 0 | 4 | 181 |
| Qwen3-0.6B | 596M | 359 MiB | 65.4 | 16.0 | 26.8 | 1.59 | 0 | 0 | 159 |
| Llama-3.2-1B-Instruct | 1.24B | 730 MiB | — | 9.5 | 23.0 | 1.52 | 0 | 12 | 204 |
| bangla-smollm-135m (base) | 210M | 113 MiB | 168.7 | 29.6 | 413.4 | 3.04 | 0 | 0 | 168 |
| bangla-smollm-135m-cupcake | 210M | 127 MiB | 182.0 | 29.0 | 1005.9 | 3.76 | 0 | 0 | 181 |

BPB (bits per byte) is the cross-tokenizer-comparable metric — PPL is not, since the
vocabularies differ. Findings:

- **`liltii-v0.2` is the clear winner.** It is the only model that actually implements the persona
  (52% emotion tags, 56% action beats, 56-char replies instead of 160–200), has the best in-domain
  BPB, and is faster than its own base at equal parameters.
- **The `bangla-smollm-135m` fine-tune is a documented failure.** It regressed against its own base
  (BPB 3.04 → 3.76, PPL 413 → 1006) and rambles in repetitive loops. Kept public for transparency.
- Both published GGUFs embed their chat template, so `llama.cpp`/Transformers chat inference works
  out of the box.

### Running a model outside the app

```bash
llama-cli -hf bayazidhs04/liltii-v0.2-qwen2.5-0.5b \
  --gguf-file liltii-v0.2-v3-q4_0.gguf -cnv
```

---

## Building

**Requirements:** JDK 17, Android SDK 34, NDK/CMake 3.22.1, an arm64 device (debug builds only
ship `arm64-v8a`). ESP32-S3 + PlatformIO for the firmware.

### Android app

```bash
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # install on a connected device
```

Native llama.cpp is built by CMake (`app/src/main/cpp/CMakeLists.txt`) with `-O3`, NEON on,
OpenMP off, llamafile off. The debug variant sets `applicationIdSuffix = ".debug"` →
`com.cupcake.debug`, and bundles the GGUF uncompressed (`noCompress += "gguf"`).

### Release build

`signingConfigs.release` reads real credentials from env / `gradle.properties`
(`CUPCAKE_KEYSTORE_PATH`, `CUPCAKE_KEYSTORE_PASSWORD`, `CUPCAKE_KEY_ALIAS`, `CUPCAKE_KEY_PASSWORD`)
and **falls back to the debug keystore** so local release builds work without secrets — do not
ship builds signed that way. Release enables R8 (`isMinifyEnabled`) and resource shrinking.

### ESP32 firmware

```bash
cd esp
pio run -t upload
pio device monitor
```

> Known issue: `pio run -e esp32dev` currently fails resolving `Network.h` under the pioarduino
> core-3.x; see [ARCHITECTURE.md](ARCHITECTURE.md#known-limitations) for status.

---

## Testing

Everything below runs on the JVM — **no device or emulator needed**.

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:testDebugUnitTest --tests "com.cupcake.game.*"
./gradlew :app:lintDebug              # Android Lint
```

**Current status (this repo, latest run):**

| Check | Result |
|-------|--------|
| `:app:testDebugUnitTest` | **149 tests, 24 classes, 0 failures, 0 errors** |
| `:app:lintDebug` | **0 errors**, 133 warnings (mostly `GradleDependency` update nags) |
| `:app:assembleDebug` | builds `app-debug.apk` |

Test suites and what they lock down:

| Suite | Covers |
|-------|--------|
| `ai.AnswerFormatTest` | the companion **answer contract**: prompt carries persona + few-shot, `CUTE_STYLE` is parseable, and the scorer gives full marks to a correct `[tag] *action* bangla` reply while flagging missing/invalid/misplaced tags |
| `ai.LilTiiEvaluatorTest` | 20-iteration grading harness against a stubbed engine |
| `ai.CharacterManagerTest`, `ai.SillyTavernCardParserTest` | character store, card import/export |
| `ai.EmotionAssetsTest`, `ai.ProactiveEngagementManagerTest`, `ai.ModelDownloadManagerTest` | tag→art→face mapping, outreach moods, catalog pruning |
| `ai.DemandManagerTest` | demand catalog invariants, no-repeat picking, affection floor |
| `game.ChessEngineTest` | perft node counts + legality + AI sanity |
| `game.PointsManagerTest` | coins vs XP separation, spend gating, persistence round-trip, level-up events |
| `game.*EngineTest`, `game.BoardCopyTest` | per-game engines and board immutability |
| `game.GameManagerGamesTest`, `game.GameManagerBotIntegrationTest` | win/draw handling, curated taunts, mood escalation |
| `game.ToneGuardTest` | hostility filter and length clamp |
| `network.EspFaceTest`, `network.WifiProvisioningTest` | ESP face payload contract, provisioning payloads |
| `chat.ChatSessionStoreTest`, `data.*`, `domain.UseCasesTest` | session store, converters, models, use cases |

### Grading the real model on a device

The JVM tests pin the *contract*; the actual model output is graded by `LilTiiEvaluator`, which
runs 20 prompts through the live backend and scores emotion tag / action / Bangla compliance
(pass = score ≥ 3, report passes at ≥ 80%). Open **Settings → LilTii Eval**, or call it
programmatically:

```kotlin
val report = evaluator.evaluate(config, iterations = 20)
// report.overallPassRate, report.emotionTagDistribution, report.results[].notes
```

### Instrumented tests

`app/src/androidTest` targets Espresso + Compose UI test and requires a connected device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

---

## Project structure

```
CupCake/
├── app/                                  # Android app (Kotlin + Compose + Hilt)
│   ├── src/main/
│   │   ├── cpp/                          # llama.cpp JNI + CMake
│   │   ├── assets/models/                # bundled GGUF (gitignored)
│   │   └── java/com/cupcake/
│   │       ├── ai/                       # LlamaEngine, prompt template, emotion, TTS glue,
│   │       │                             # characters, models, proactive + demands
│   │       ├── ble/                      # BLE GATT (Nordic UART) client
│   │       ├── chat/                     # ChatSessionStore
│   │       ├── data/                     # Room, models, repositories, stores
│   │       ├── di/                       # Hilt modules (AppModule)
│   │       ├── domain/                   # use cases + repository contracts
│   │       ├── game/                     # engines, GameManager, points, anger, tone guard
│   │       ├── network/                  # ESP WebSocket, protocol decoder, WiFi provisioning
│   │       ├── protocol/                 # binary protocol models
│   │       ├── service/                  # BLE foreground service, Shimeji overlay
│   │       ├── tts/                      # TtsManager
│   │       └── ui/                       # Compose screens, components, theme
│   ├── src/test/                         # 149 JVM unit tests
│   └── src/androidTest/                  # Espresso/Compose instrumented tests
├── architecture/                         # component-design notes + index
├── docs/                                 # architecture, plan, gap analysis, blog
├── esp/                                  # ESP32 firmware (PlatformIO)
├── training/                             # LoRA / dataset / GGUF pipeline + Kaggle notebooks
├── tools/                                # esp-check helper
├── ARCHITECTURE.md                       # deep-dive system architecture
└── README.md
```

---

## Configuration

### Local model (default)
Nothing to configure — the bundled GGUF is copied out of assets on first launch and warmed by
`ModelPreloader`.

### Remote provider
Settings → add a provider:

| Provider | Example endpoint |
|----------|------------------|
| OpenAI-compatible | `https://api.openai.com/v1` + API key |
| Ollama | `http://localhost:11434/v1` (no key) |
| vLLM | `http://localhost:8000/v1` |
| Other | any OpenAI-compatible base URL |

Per-conversation you can override provider, model, temperature/top-p/top-k, streaming, and the
system prompt.

### Generation defaults
`LlamaEngine.Config`: `nCtx=2048`, `nThreads=recommendedThreadCount()`, `nBatch=512`,
`maxTokens=256`, `temperature=0.7`, `topP=0.9`, `topK=40`, `repeatPenalty=1.1`,
`repeatLastN=64`, `seed=-1`.

---

## Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — layers, AI stack, game/points/demand subsystems, data flow, DI, build & test strategy.
- [docs/plan.md](docs/plan.md) — phased roadmap.
- [docs/GAP_ANALYSIS.md](docs/GAP_ANALYSIS.md) — gap analysis.
- [docs/blog/on-device-companion-models.md](docs/blog/on-device-companion-models.md) — write-up of the model work.
- [architecture/](architecture/) — component-design notes (API gateway, system prompts).

## License

MIT
