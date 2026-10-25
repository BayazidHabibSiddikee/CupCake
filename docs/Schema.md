# Schema.md — CupCake Sound, Mic & Movement Data Schema

## 1. Overview
This document defines the data models, schemas, and serialization formats used by the CupCake companion app's sound, mic, and movement features.

## 2. Core Sound & Audio Schema

### 2.1 SoundEvent
```
SoundEvent {
  timestamp: Long (epoch ms)
  category: String (fixed set: "music", "crying", "gunshot", "laughter", "scream", "talk", "applause", "animal", "nature", "alert", "other")
  label: String (YAMNet label, e.g., "MusicalInstrument")
  confidence: Float (0.0 - 1.0)
  duration: Float (seconds)
}
```

### 2.2 AudioSample
```
AudioSample {
  format: String ("PCM_16BIT")
  sampleRate: Int (16000)
  channelCount: Int (1)
  data: ByteArray (raw PCM samples)
  duration: Float (seconds)
}
```

### 2.3 SoundClassificationResult
```
SoundClassificationResult {
  event: SoundEvent
  processingTimeMs: Long
  modelVersion: String
}
```

## 3. Speech & TTS Schema

### 3.1 SpeechRequest
```
SpeechRequest {
  text: String (UTF-8)
  language: String ("en", "bn", "ja")
  voice: String (e.g., "female_en", "male_bn")
  rate: Float (0.5 - 2.0)
  pitch: Float (0.5 - 1.5)
  volume: Float (0.0 - 1.0)
  priority: Int (0 = normal, 1 = urgent)
  correlationId: String (unique ID for this utterance)
}
```

### 3.2 SpeechResponse
```
SpeechResponse {
  requestId: String
  status: String ("queued", "playing", "completed", "error")
  errorMessage: String?
  durationMs: Long?
  confidence: Float?
}
```

### 3.3 TtsManager
```
TtsManager {
  engine: String (e.g., "local_tts", "online_tts")
  voice: String
  locale: Locale
  speaking: Boolean
  queue: List<SpeechRequest>
}
```

## 4. Movement & Gesture Schema

### 4.1 Gesture
```
Gesture {
  type: String (fixed: "TAP", "SWIPE", "SHAKE", "PINCH", "ROTATE")
  direction: String? (e.g., "LEFT", "RIGHT", "UP", "DOWN", "CLOCKWISE")
  intensity: Float (0.0 - 1.0)
  durationMs: Long (time from start to end)
  timestamp: Long (epoch ms)
}
```

### 4.2 Gait
```
Gait {
  type: String (fixed: "IDLE", "RUN", "JUMP", "SPIN", "WAVE", "HOP", "BOUNCE", "SLEEP")
  frame: Int (current animation frame)
  duration: Long (ms since gait started)
  params: GaitParams
}

GaitParams {
  bobAmplitude: Float (0.0 - 1.0)
  swayAmplitude: Float (0.0 - 1.0)
  tiltAngle: Float (-1.0 - 1.0)
  stepRate: Float (steps per second)
}
```

### 4.3 PetRigView State
```
PetRigViewState {
  x: Float
  y: Float
  rotation: Float
  scale: Float
  bounceOffset: Float
  swayOffset: Float
  tiltAngle: Float
  currentGait: Gait
  isSpeaking: Boolean
  lastGesture: Gesture?
}
```

## 5. SoundReactionService Schema

### 5.1 Service Event
```
ServiceEvent {
  type: String ("SOUND_DETECTED", "SPEECH_RECOGNIZED", "STT_COMPLETE", "STT_TIMEOUT", "SERVICE_START", "SERVICE_STOP")
  payload: Map<String, Any>
  timestamp: Long
  correlationId: String
}
```

### 5.2 SoundReactionService Configuration
```
SoundReactionConfig {
  listenIntervalMs: Int (5000)
  listenWindowMs: Int (2500)
  requiredHits: Int (2)
  strongHitConfidence: Float (0.6)
  debounceMs: Long (8000)
  micPauseMs: Long (45000)
  soundCategories: List<String>
}
```

## 6. WebSocket/WebRTC Schema (ESP32 Communication)

### 6.1 WebSocket Message
```
WebSocketMessage {
  type: String ("MESSAGE", "MOTOR", "FACE", "STATE")
  payload: Map<String, Any>
  timestamp: Long
}

MotorCommand {
  action: String (e.g., "wave", "nod", "turn")
  durationMs: Int
}

FaceExpression {
  emotion: String ("happy", "sad", "surprised", "angry", "neutral", "thinking")
  intensity: Float (0.0 - 1.0)
}
```

## 7. Notification Schema

### 7.1 Sound Notification
```
SoundNotification {
  title: String ("CupCake is listening")
  text: String (e.g., "Music detected, 85% confidence")
  sound: String (notification sound, e.g., "default")
  ongoing: Boolean (true)
  priority: Int (3 = high)
}
```

## 8. Database Schema (Room)

### 8.1 SoundHistory
```
SoundHistory {
  id: Long (primary key, auto-generated)
  timestamp: Long
  category: String
  label: String
  confidence: Float
  userId: Long
  note: String?
}
```

### 8.2 Settings
```
Settings {
  id: Long (primary key, auto-generated)
  key: String (e.g., "sound_awareness", "movement_enabled", "voice_enabled")
  value: String
  updatedAt: Long
}
```

## 9. Logging Schema

### 9.1 Audit Log Entry
```
AuditLog {
  timestamp: Long
  thread: String
  level: String ("DEBUG", "INFO", "WARN", "ERROR")
  logger: String
  message: String
  exception: String?
  context: Map<String, Any>?
}
```

## 10. Validation Rules

### 10.1 Sound Event
- category must be valid (from fixed set)
- confidence must be 0.0 - 1.0
- timestamp must be > 0

### 10.2 Motion Control
- animationSpeed must be 0.1 - 3.0
- intensity must be 0.0 - 1.0

### 10.3 TTS
- rate must be 0.5 - 2.0
- pitch must be 0.5 - 1.5

## 11. Serialization Format
- **JSON**: Primary format for API and IPC
- **Protobuf**: Potential for future high-performance IPC
- **SharedPreferences**: Application settings storage

## 12. Backward Compatibility
- Schema changes will be backward-compatible
- Field removals will require migration
- Existing data will not be migrated unless necessary
