# AppFlow.md — CupCake Sound, Mic & Movement Flow

## 1. Overview
This document describes the complete flow of the CupCake companion app, focusing on sound/mic and movement interactions. The flow is broken down into: **User Input → Service → Processing → Response**.

## 2. System Architecture

### 2.1 Component Diagram
```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  MainActivity│     │  SoundReactionSvc│     │  SpeechToTextMan │
│  (UI/Controller)│    │  (Audio Capture+  │     │  (STT Bridge)    │
│                │    │   YAMNet Detect) │     │                  │
└──────┬────────┘     └────────┬─────────┘     └────────┬─────────┘
       │                       │                        │
       └──────────┬─────────────┴────────────┬───────────┘
                  │                         │
                  ▼                         ▼
        ┌──────────────┐         ┌──────────────┐
        │   LocalSpeaker│         │  TtsManager  │
        │  (TTS Play)   │         │  (TTS Engine)│
        └──────────────┘         └──────────────┘
                  │                         │
                  ▼                         ▼
        ┌──────────────┐         ┌──────────────┐
        │ PersonaEngine │         │ MessagingBus │
        │ (Emotion+Face)│         │ (Chat Msg)   │
        └──────────────┘         └──────────────┘
```

### 2.2 Key Flows

#### Flow 1: Sound Detection (Mic)
```
User Claps/Speaks
   │
   ▼
SoundReactionService.onCreate()
   │
   ├── Request RECORD_AUDIO permission
   ├── Initialize SoundClassifier (YAMNet model)
   └── Start AudioRecord
        │
        ▼
   AudioRecord → ShortArray [15600 samples]
        │
        ▼
   SoundClassifier.classify() → FloatArray → YAMNet
        │
        ▼
   SoundEvent output (category, label, confidence)
        │
        ▼
   If talk detected → STT capture
        │
        ▼
   SpeechToTextManager.startListening()
        │
        ▼
   STT result → MessagingBus.sendUserMessage(text)
        │
        ▼
   ChatViewModel → Processing → Response
        │
        ▼
   LocalSpeaker.speak() → TTS → AudioTrack
        │
        ▼
   SoundReactionService.handleEvent()
        │
        ▼
   ShimejiPetService.showSpeech() → PetRigView animation
```

#### Flow 2: Movement/Gesture Response
```
User Gesture (tap, shake, etc.)
   │
   ▼
PetRigView.onGestureReceived()
   │
   ▼
Gait processing (animation cycle)
   │
   ▼
PetRigView.update(float deltaTime)
   │
   ▼
Body animation (bob, sway, tilt, step)
   │
   ▼
ShimejiPetService.showSpeech() → ESP32 via WebSocket
   │
   ▼
ESP32 → Physical robot movement
```

## 3. Process Descriptions

### 3.1 Sound Detection Process
1. **Service Start**: `SoundReactionService.onCreate()` initializes the sound classifier and starts monitoring
2. **Audio Capture**: `AudioRecord` captures 16kHz mono PCM data
3. **Processing**: SoundClassifier (YAMNet) classifies sounds every 2.5 seconds
4. **Response**: If a "talk" sound is detected, STT activation is triggered
5. **Reaction**: If "music", "laughter", etc. detected, bot emits emotion + voice

### 3.2 Movement Process
1. **User Input**: Gesture detected via `PetRigView`
2. **Animation**: `update()` method advances gait cycle
3. **State**: Animation parameters (bob, sway, tilt) are computed
4. **Output**: `showSpeech()` updates overlay and ESP32

### 3.3 Speech/Response Process
1. **Trigger**: Sound event detected
2. **Text**: Compiled speech template selected (random from pool)
3. **TTS**: `LocalSpeaker.speak()` → `TtsManager.speakStreaming()`
4. **Audio**: PCM stream → `AudioTrack` → Playback

## 4. Sequence Diagrams

### 4.1 Sound + Speech + Movement
```
┌────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────┐
│  User      │    │  SoundReaction│    │  SpeechToText│    │  Chat      │
│  makes     │───►│  Service      │───►│  Manager     │───►│  ViewModel │
│  sound     │    │              │    │              │    │            │
└────────────┘    └──────────────┘    └──────────────┘    └────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  LocalSpeaker│
                     │  (TTS Play)  │
                     └──────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  ShimejiPet  │
                     │  Service     │
                     └──────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │   PetRigView │
                     │ (Animation)  │
                     └──────────────┘
```

## 5. Data Models
- **SoundEvent**: { category, label, confidence }
- **SoundClassifier**: YAMNet inference output
- **SpeechToText**: { transcript, language }

## 6. State Transitions
- **Service State**: STARTED → LISTENING → STOPPED
- **Audio State**: IDLE → RECORDING → PAUSED → PLAYING
- **Gait State**: IDLE → RUN → JUMP → SPIN → WAVE → HOP → BOUNCE → SLEEP

## 7. Error Handling
- Permission denied → Service stops
- AudioRecord failure → Service stops gracefully
- STT failure → Retry with next language
- TTS failure → Log warning, continue UI update

## 8. Security & Privacy
- All audio processing is on-device
- No audio data sent to external servers for STT (unless optional)
- Sound detection uses on-device TensorFlow Lite model
