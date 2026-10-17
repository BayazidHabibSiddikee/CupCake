# CupCake — Architecture

Deep-dive on how the app is put together. For the product/feature view see [README.md](README.md).

---

## Table of contents

- [System overview](#system-overview)
- [Layer map](#layer-map)
- [AI stack](#ai-stack)
- [Chat pipeline](#chat-pipeline)
- [Game subsystem](#game-subsystem)
- [Points economy](#points-economy)
- [Engagement subsystem](#engagement-subsystem)
- [Data layer](#data-layer)
- [Device & network layer](#device--network-layer)
- [UI layer](#ui-layer)
- [Dependency injection](#dependency-injection)
- [Build system](#build-system)
- [Testing strategy](#testing-strategy)
- [Known limitations](#known-limitations)

---

## System overview

```
                         ┌───────────────────────────────────────────────┐
                         │                 ANDROID APP                    │
                         │                                                │
   ┌─────────┐  WebSocket │  ┌─────────┐   ┌────────────┐   ┌──────────┐  │
   │ ESP32-S3│◄───────────┼─►│ network │   │    ui      │   │   ai     │  │
   │  face/  │   BLE NUS  │  │ EspWS / │   │ Compose +  │   │ Llama /  │  │
   │  motor/ │◄───────────┼─►│ Ble /   │◄─►│ ViewModels │◄─►│ prompt / │  │
   │  audio  │  protocol  │  │ WiFi    │   └─────┬──────┘   │ emotion  │  │
   └─────────┘            │  └─────────┘         │          └────┬─────┘  │
        ▲                 │        ▲             │               │        │
        │ PCM             │        │             ▼               ▼        │
   ┌────┴─────┐           │  ┌─────┴──────┐  ┌─────────┐   ┌───────────┐  │
   │ speakers │           │  │    game    │  │  data   │   │  tts      │  │
   └──────────┘           │  │ engines /  │  │ Room /  │   │ TtsManager│  │
                          │  │ GameManager│  │ stores  │   └───────────┘  │
                          │  └─────┬──────┘  └─────────┘                  │
                          │        │                                     │
                          │  ┌─────┴──────────────┐   ┌───────────────┐   │
                          │  │ points / demands / │   │ WorkManager   │   │
                          │  │ proactive / energy │◄──│ workers       │   │
                          │  └────────────────────┘   └───────────────┘   │
                          └───────────────────────────────────────────────┘
                                     │ (optional)
                                     ▼
                    OpenAI · Ollama · vLLM · custom (SSE / NDJSON)
```

Three roles in one system:

1. **On-device brain** — GGUF through llama.cpp/JNI; works offline; owns the LilTii persona.
2. **Companion runtime** — chat, TTS, emotion→face, games, points, demands, background work.
3. **Device bridge** — WebSocket + BLE to the ESP32 gadget for face/motor/audio/sensors.

---

## Layer map

| Package | Responsibility | Key types |
|---------|----------------|-----------|
| `com.cupcake.ai` | Models, prompts, emotions, characters, TTS glue, engagement | `LlamaEngine`, `LilTiiPromptTemplate`, `EmotionClassifier`, `EmotionAssets`, `CharacterManager`, `ModelDownloadManager`, `BanglishTranslator`, `ProactiveEngagementManager`, `DemandManager` |
| `com.cupcake.game` | Game engines + orchestration + economy | `Game`, `GameManager`, `GameLLMRouter`, `PointsManager`, `PointsStore`, `JealousyTracker`, `BotTrashTalk`, `ToneGuard`, `ChessEngine` |
| `com.cupcake.ui` | Compose screens + ViewModels + theme | `MainActivity`, `HomeScreen`, `ChatScreen`, `GameScreen`, `PointsScreen`, `SettingsScreen`, `CharacterScreen`, `DeviceScreen` |
| `com.cupcake.data` | Persistence + models + repositories | `AppDatabase`, `ChatMessage`, `ModelConfig`, `UserProfileStore`, `ChatSessionStore` |
| `com.cupcake.domain` | Use cases + repository contracts | `*UseCase`, `ChatRepository`, `ModelRepository` |
| `com.cupcake.network` | ESP bridge | `EspWebSocketServer`, `ProtocolDecoder`, `WifiProvisioning`, `EspFace` |
| `com.cupcake.ble` | BLE transport | `BleGattClient` (NUS) |
| `com.cupcake.protocol` | Wire models | `SensorData`, `BatteryData`, `EventData`, `Command`, `CommandResponse`, `DecodedMessage` |
| `com.cupcake.tts` | Voice | `TtsManager` |
| `com.cupcake.service` | Foreground services | `BleForegroundService`, `ShimejiPetService` |
| `com.cupcake.di` | Hilt graph | `AppModule` |

---

## AI stack

### `LlamaEngine` (inference)
- Thin Kotlin wrapper over `libllama_jni.so` (CMake target in `app/src/main/cpp/`).
- `loadModel()` copies the GGUF out of `assets/models/` on first run, validates the GGUF magic
  (a truncated asset otherwise fails with an opaque native `-1`), and reports byte progress.
- `generateStream(prompt)` returns a `ReceiveChannel<String>` of tokens.
- `Config`: `modelFileName`, `nCtx=2048`, `nThreads=recommendedThreadCount()`, `nBatch=512`,
  `maxTokens=256`, `temperature=0.7`, `topP=0.9`, `topK=40`, `repeatPenalty=1.1`,
  `repeatLastN=64`, `seed=-1`.
- Singleton via `LlamaEngine.getInstance()`; Hilt provider calls `initialize(context)`.

### `ModelPreloader` / `ModelDownloadManager`
- `ModelPreloader.preload()` warms the active model in the background at app start so the first
  reply (and the first game line) isn't a cold start.
- `ModelDownloadManager.CATALOG` describes available GGUFs; the bundled
  `liltii-v0.2-v3-q4_0.gguf` (`isBundled = true`, 284,087,232 B) is the only entry today.
  `resolveStoredActiveModel()` tolerates renamed/removed models, and `pruneStaleModels()` deletes
  leftovers from older builds to reclaim private storage.

### `LilTiiPromptTemplate`
The persona contract lives here:
- `SYSTEM_INSTRUCTIONS` — "You are LilTii…", the allowed tag set, Banglish mix.
- `CUTE_STYLE` — a one-block style line injected into on-device chat prompts: exactly one
  `[mood]`, one `*action*`, a 1–2 sentence warm reply, 1–2 emoji, with a worked example whose tag
  `EmotionAssets.tagOf()` can read.
- `buildPrompt(userMessage)` — system + few-shot script + `User:`/`Bot:` turns, used by the
  evaluator.

### `EmotionClassifier` → `EmotionAssets`
- `EmotionClassifier` scores text across joy / affection / jealousy / surprise / sadness / anger /
  thinking with keyword sets and weights.
- `EmotionAssets` is the single shared vocabulary: a `[tag]` in the reply wins over lexical
  detection (`tagOf`, `stemForText`), maps to app art stems, an ESP face, and HTTP/asset paths.
  This is why the tag contract matters — it drives image, voice language, and hardware together.

### `ToneGuard`
Every game line passes through `ToneGuard.sanitize()` after generation: collapse whitespace,
replace hostile content with a playful line (whole-word `HOSTILE` regex so
"study"/"diet"/"class" aren't false positives), clamp to 220 chars. Keeps a competitive LLM from
producing abuse that would otherwise be shown, spoken, and persisted.

### `BanglishTranslator`
Post-processes model output into in-character Banglish gyaru phrasing (used on the final chat
response and driven by the character's `personality`).

### `LilTiiEvaluator` (answer grading)
`evaluate(config, iterations=20)` runs a fixed 20-prompt mix (some with an `expectedEmotion`) and
scores each reply:
`+1` for a tag anywhere, `+1` for a tag in the allowed set, `+1` for a tag at index 0, `+1` for an
`*action*`, `+1` for Bengali script, `+1` when the emotion matches expectation. `passed = score ≥ 3`;
report passes at `overallPassRate ≥ 0.8`. Exposed in-app at **Settings → LilTii Eval**.

---

## Chat pipeline

```
user text
   │
   ▼
ChatViewModel.sendMessage()
   │  build character system prompt (CharacterManager, + optional gameContext)
   │  + UserProfileStore.toPromptBlock()
   │  + LilTiiPromptTemplate.CUTE_STYLE
   ▼
LlamaEngine.generateStream(prompt)          (or OllamaEngine for remote providers)
   │  token stream → _currentResponse (rendered live)
   │  re-derive emotion every N tokens
   │     ├─► espServer.sendFaceExpression(stem, emotion, imagePath)   → board face
   │     ├─► espServer.sendMotorAction(action)                        → board motor
   │     └─► ShimejiPetService.showSpeech(...)                        → floating pet
   ▼
final text → BanglishTranslator.translate(text, personality)
   │
   ├─► persist via ChatSessionStore / Room (ChatMessage.assistant)
   ├─► TtsManager.speakStreaming(text) { pcmChunk -> espServer.sendAudio(chunk) }
   └─► settle face on final emotion
```

`ChatViewModel.updateFace()` (≈L111) is the single place that pushes emotion to the overlay and
the board; `sendMotorAction` fires when the classifier detects a strong mood. This keeps the
phone art, the overlay, and the physical board in sync from one source of truth.

---

## Game subsystem

```
             GameScreen ── GameViewModel ── GameManager
                                             │  ├─ currentGame: Game.State
                                             │  ├─ engines (per GameType)
                                             │  ├─ JealousyTracker  → adaptive difficulty + anger
                                             │  ├─ BotTrashTalk     → tiered Banglish lines
                                             │  ├─ GameLLMRouter    → on-device dialogue (+ ToneGuard)
                                             │  ├─ PointsManager    → awards
                                             │  └─ gameReactions / gameComments (SharedFlow)
                                             ▼
                        GameViewModel → TTS + ESP face/motor + chat bubble
```

- **`Game` / `Game.GameType`** — 15 games (`TIC_TAC_TOE`, `TIC_TAC_TOE_4X4`, `CONNECT_FOUR`,
  `CHESS`, `MASTERMIND`, `MEMORY_MATCH`, `HANGMAN`, `WORD_SCRAMBLE`, `NUMBER_BASE`,
  `REACTION_TIME`, `MATH_QUIZ`, `GUESS_NUMBER`, `ROCK_PAPER_SCISSORS`, `CLICKER`, `PING_PONG`).
- **Engines** are pure Kotlin (e.g. `TicTacToeEngine`, `ConnectFourEngine`, `ChessEngine`,
  `MathQuizEngine`, `GuessNumberEngine`) so they are unit-testable and provide `bestMove`.
- **`GameManager`** owns one active `Game.State`, enforces legality via the engines, detects
  win/draw, and emits a `GameReaction` on completion. Chess uses `chessHumanMove(from,to,promo)`;
  the bot replies on a 600 ms delay through `ChessEngine.bestMove(difficulty)`.
- **`MOVE_NOTIFY_EXEMPT`** — for games where the bot speaks once per round (TTT, 4×4, ping pong,
  chess) the raw human move does not trigger a redundant LLM call.
- **Adaptive difficulty** — `JealousyTracker.getAdaptiveDifficulty(difficulty)` raises search
  depth/pressure as the human's win streak grows; `getAngerLevel`/`getMoodRank` feed the UI banner.
- **`BotTrashTalk`** — deterministic taunts by tier (`CALM → FURIOUS`), plus `welcomeLine`,
  `gloatLine`, `rallyLine`. `triggerReaction` uses the curated taunt on a human win (mood `ANGRY`,
  or `SAD` when anger is high and the streak is long) and the LLM+gloat path on a bot win.
- **Board UIs** — `GameBoardUIs.kt` (grid/word/reflex boards), `ChessBoardUI.kt` (tap-to-move,
  legal-move dots, check + last-move highlights, promotion picker), `PingPongBoardUI.kt`
  (real-time canvas rally with normalised physics and a drag paddle).
- **Never muted** — `GameLLMRouter` deliberately ignores remote providers so games always use the
  warm local model, with canned-line fallback if it isn't loaded.

---

## Points economy

`PointsManager` owns two currencies and persists everything through `PointsStore`:

```
awardPoints(base, gameType, won)         spendPoints(amount): Boolean
   coins += earned                          require(amount > 0)
   xp    += earned                          if coins < amount -> false (no change)
   stats[gameType].{played,wins,points}     coins -= amount; persist()
   recomputeLevel()  (while xp >= next)     → true
   levelUpEvents.tryEmit(level)
   checkAchievements()
   persist()
```

- **Two currencies, on purpose.** Coins are a spendable wallet (demands drain it); XP is a
  lifetime score that only grows. Fulfilling a demand can never cost you a level.
- **Level curve** — `getXpForLevel(level)` is a cumulative exponential (`BASE=100`, `GROWTH=1.5`,
  level 1 = 0). `getLevelProgress()` returns `(xp - floor, ceiling - floor)` both non-negative,
  which fixes the old `-100 / 50` display, and `xpProgress` is clamped to `0..1`.
- **`spendPoints` is a hard gate** — returns false and changes nothing when the balance is low,
  which is what makes demands a real trade-off.
- **Persistence** — `PointsSnapshot` (points, xp, level, totals, per-game stats, achievements)
  via `PointsStore`. `SharedPrefsPointsStore` in the app; `InMemoryPointsStore` in tests;
  `PointsManager()` convenience constructor = in-memory for tests/previews.
- **`PointsManager.get()`** exposes the process-wide instance and `spendPersisted(context, amount)`
  spends safely from a cold start (notification tap with no UI alive).

Rewards scale with `multiplier` (win 1.5 / loss 0.5) × difficulty bonus
(`CHESS 2.0`, `CONNECT_FOUR`/`MASTERMIND 1.5`, `PING_PONG`/`TIC_TAC_TOE_4X4 1.3`, `MATH_QUIZ`/
`NUMBER_BASE 1.2`, `MEMORY_MATCH 1.1`). Achievements include per-game win milestones, total-games
tiers, lifetime-XP milestones (keyed off XP so spending can't retrigger them), per-game "perfect"
streaks, and `polymath` for 10+ distinct games.

---

## Engagement subsystem

Two independent background loops plus the energy budget.

### Marin's demands — `DemandManager` + `DemandWorker` + `DemandActionReceiver`

```
WorkManager (45–150 min) ──► DemandWorker ──► DemandManager.pollDemand()
                                                 │  pending & fresh? reschedule
                                                 │  stale (>6 h)? dismiss(penalize)
                                                 ▼
                                       rollDemand() → append chat line
                                                 → DemandNotifier.notifyDemand()
                                                   [Fulfill (N pts)] [Ignore]
                                                          │
                        BroadcastReceiver ────────────────┘
                          Fulfill → PointsManager.spendPoints / spendPersisted
                                    └─ ok: affection += reward, cancel, happy line
                                    └─ no: "not enough points" notification
                          Ignore  → affection -= sulk, sulky line
```

- Catalog: `hungry, snack, attention, cosplay, rematch, sleepy` — each with `cost`, `affection`,
  `sulk`, and both outcome lines.
- `pickDemand()` never repeats the previous id; `affectionAfter()` floors at zero.
- Affection + pending state persist in `cupcake_marin_demands`; `ensureLoaded()` re-syncs memory
  from prefs so a cold-started receiver doesn't lose affection.
- The same object exposes `currentDemand` / `affection` / `lastEvent` StateFlows for the home
  banner, and `*FromUi()` convenience wrappers using the context captured in `initialize()`.

### Proactive outreach — `ProactiveEngagementManager`
Mood-aware (`ANGRY / LOVED / PLAYFUL / LONELY / NORMAL`, inferred from recent chat) background
messages: 1–48 h random intervals, silence-treatment probability when angry, 1–3 message bursts,
capped at 15 before the user replies. Delivers to the session store, the overlay, and a
notification. Resets on user activity (`ChatViewModel` hooks `onUserActivity`).

### Energy — `EnergyManager`
30-minute budget in `energy_state.json`: 30 s per message, 1 min per game (games are best-effort
only and never blocked), +30 min per ad reward, Pro upgrade bypasses it (`billing-ktx`).

---

## Data layer

- **Room** — `AppDatabase` with `Converters` for enum/list columns and
  message and prompt DAOs; `ChatMessage.assistant/user` factories stamp conversation id + model.
- **`ChatSessionStore`** — in-memory working set of messages per character with a
  `messages(characterId): StateFlow<List<ChatMessage>>`, persisted through Room; used by chat,
  proactive outreach, and demands. `ChatSessionStore.get()` for non-Hilt callers.
- **`UserProfileStore`** — SharedPreferences-backed `UserProfile` with
  `toPromptBlock()` (name/age/relationship/likes/… "always address them as X").
- **`ModelConfig`** — provider (`LOCAL_LLAMA`, `CUSTOM_OPENAI`, `CUSTOM_OLLAMA`, `CUSTOM_VLLM`,
  `CUSTOM_OTHER`), endpoint resolution with sane defaults, generation params, streaming toggle.
- **`CharacterManager`** — singleton holding built-in + custom characters, selection,
  save/delete, SillyTavern import/export, and `getSystemPromptForCharacter(id, gameContext)`.

---

## Device & network layer

### WebSocket (`EspWebSocketServer`)
Ktor/Netty server on port 8080; the board (or a test client) connects and exchanges JSON frames.
Server → board messages: `face`, `motor`, `audio`, `command`, `config`. `buildFacePayload()`
(`EspFace.kt`) is a pure function — `{expression, duration, emotion?, image?}` — so the wire
contract is unit-testable without hardware. Faces: `neutral, happy, sad, angry, rage, excited,
thinking, sleepy, wink, heart_eyes`. Motors: `idle, nod, shake, tantrum, dance, wave,
look_left, look_right`. `sendAudio(ByteArray)` pushes TTS PCM.

### BLE (`BleGattClient`)
Nordic UART Service (service `6E400001-…`, TX notify `6E400003-…`, RX write `6E400002-…`):
scan → connect → MTU negotiation → notifications → command writes, decoded by `ProtocolDecoder`
into `SensorData` / `BatteryData` / `EventData` / `CommandResponse`. Permission gating lives in
the callers (`DeviceSetupViewModel`), so the class itself is `@SuppressLint("MissingPermission")`.
`WifiProvisioning` + `WifiSetupScreen` push SSID/credentials over the same link.

### Services
`BleForegroundService` (`connectedDevice`) for long scans/connections; `ShimejiPetService`
(`specialUse`) renders the floating companion overlay above other apps.

---

## UI layer

Jetpack Compose + Material 3 + Navigation Compose. `MainActivity` hosts the `NavHost`:

| Route | Screen / ViewModel |
|-------|--------------------|
| `home` | `HomeScreen` / `HomeViewModel` — bond card (level, XP, coins), demand banner, game grid, quick actions |
| `chat/{characterId}` | `ChatScreen` / `ChatViewModel` — streaming, live face, TTS |
| `games?gameType={gameType}` | `GameScreen` / `GameViewModel` — everything game |
| `points` | `PointsScreen` / `PointsViewModel` — level, stats, achievements |
| `characters` | `CharacterScreen` / `CharacterViewModel` |
| `systemprompt/{promptId?}` | `SystemPromptScreen` / `SystemPromptViewModel` |
| `settings` | `SettingsScreen` / `SettingsViewModel` (+ dev entry to `liltii-eval`) |
| `device/{deviceId}` | `DeviceScreen` / `DeviceSetupViewModel` |
| `wifi-setup` | `WifiSetupScreen` |
| `liltii-eval` | `LilTiiEvalScreen` / `LilTiiEvalViewModel` |

ViewModels are `@HiltViewModel` and expose `StateFlow`s collected with
`collectAsStateWithLifecycle()`. Screens stay dumb; all logic is in the manager layer.

---

## Dependency injection

Hilt, single `AppModule` (`@InstallIn(SingletonComponent)`):

- **Engines/managers as `@Singleton`**: `LlamaEngine` (init), `OllamaEngine`, `CharacterManager`
  (init), `EnergyManager`, `TtsManager` (init), `EspWebSocketServer`, `GameManager`,
  `PointsManager` + `PointsStore`, `GameLLMRouter`, `PointsViewModel`.
- **Infra**: `AppDatabase`, `OkHttpClient`, `Retrofit` (+ `OpenAiApi`, `OllamaApi`), `ApiClient`,
  repositories, and a 4-thread `ListeningExecutorService`.
- Some components keep a static `getInstance()`/`get()` for non-Hilt callers (workers,
  receivers) — e.g. `PointsManager.get()`, `ChatSessionStore.get()`, `LlamaEngine.getInstance()`.

---

## Build system

| Aspect | Value |
|--------|-------|
| Namespace / appId | `com.cupcake` (debug: `com.cupcake.debug`) |
| SDK | `compileSdk 34`, `targetSdk 34`, `minSdk 26` |
| Kotlin/Java | JVM 17 |
| Native | CMake 3.22.1, `-O3`, NEON on, OpenMP/llamafile off; debug ABI `arm64-v8a` |
| Assets | `*.gguf` stored uncompressed; model gitignored |
| Release | R8 + resource shrinking, keystore from env/`gradle.properties` (debug fallback) |
| Plugins | AGP, Kotlin, Parcelize, Hilt, KSP, SafeArgs, kotlinx-serialization |

Optional native helper task `downloadOllamaBinary` (SHA-256-verified) runs on `preBuild`.

---

## Testing strategy

Everything must be testable **offline and without hardware**, so the codebase pushes Android
dependencies to constructor parameters and keeps pure logic in `object`s / functions.

```bash
./gradlew :app:testDebugUnitTest   # 149 tests, 24 classes, 0 failures
./gradlew :app:lintDebug           # 0 errors, 133 warnings
./gradlew :app:assembleDebug
```

| Layer | How it's tested |
|-------|-----------------|
| Game engines | node-count / legality assertions (`ChessEngineTest` perft tables, TTT/ConnectFour/MathQuiz/GuessNumber) |
| Answer contract | `AnswerFormatTest` — prompt carries persona/few-shot, `CUTE_STYLE` parseable, scorer gives full marks to a valid reply and flags missing/invalid/misplaced tags; `LilTiiEvaluatorTest` runs the 20-prompt harness against a stubbed engine |
| Economy | `PointsManagerTest` — coins vs XP, spend gating, persistence round-trip, level curves, level-up events; `DemandManagerTest` — catalog invariants, no-repeat pick, affection floor |
| Tone safety | `ToneGuardTest` — hostile replacement, whole-word false-positive guards, length clamp |
| Hardware contracts | `EspFaceTest` (face payload), `WifiProvisioningTest` (provisioning payloads), `ProtocolDecoder` via protocol tests |
| Data/domain | `ChatSessionStoreTest`, `ConvertersTest`, `ModelsTest`, `UseCasesTest` |
| Characters | `CharacterManagerTest`, `SillyTavernCardParserTest` |
| Live model | `LilTiiEvaluator` via in-app `LilTiiEvalScreen` (device) |
| UI | `app/src/androidTest` Espresso/Compose (`connectedDebugAndroidTest`, device required) |

Testing choices worth keeping: `PointsManager()` convenience constructor + `InMemoryPointsStore`
give a real manager with no Android; `DemandManager` exposes `catalog()/pickDemand()/
affectionAfter()` as `internal` pure functions so the rules are testable without a
`Context`; the ESP face payload is a pure function.

Facade note: `LilTiiPromptTemplate`, `ToneGuard`, `EmotionAssets` are the seams that make the
"answer" behaviour assertable without a GPU or a phone.

---

## Known limitations

- **On-device verification pending.** The dev phone was disconnected during the last session, so
  the chess board, points persistence, and demand notifications have not been exercised on
  hardware. Unit tests, lint, and the APK build are green.
- **Single bundled model.** `ModelDownloadManager.CATALOG` has one entry; remote download is
  wired but unused.
- **Energy ad reward / Pro** are shells (`rewardAdWatched`, `purchasePro`) without a live ad SDK
  or Play Billing flow.
- **ESP firmware build.** `pio run -e esp32dev` fails resolving `Network.h` with the pioarduino
  core-3.x; `[env:esp32s3]` warns about an unknown `build_flags +` option.
- **Instrumented tests** exist but need a device/emulator and are not part of the default run.
- **Lint warnings** (133) are non-blocking: mostly `GradleDependency` version-update suggestions,
  plus `ObsoleteSdkInt`/`IconLauncherShape`/`UnusedResources` cleanup opportunities.
