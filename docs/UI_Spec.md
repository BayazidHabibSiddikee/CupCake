# UI_Spec.md — CupCake Sound, Mic & Movement UI Specification

## 1. Overview

### 1.1 Purpose
This document specifies the user interface and user experience requirements for the CupCake companion app, focusing on sound/mic and movement control features.

### 1.2 Scope
- Sound Awareness toggle and settings
- Movement/gesture controls
- Voice/Speech output controls
- Notification displays for audio/movement events

## 2. UI Components

### 2.1 Sound Awareness Card
- **Location**: Settings → Sound Awareness (or "Hearing" menu)
- **Type**: Toggle Card
- **Controls**:
  - Toggle switch to enable/disable sound awareness
  - Permission request button (visible when permission not granted)
  - Status indicator (listening/not listening)
- **States**:
  - DEFAULT: Toggle off, no permission requested
  - PERMISSION_REQUESTED: Toggle on, permission pending
  - LISTENING: Toggle on, listening active, shows "Listening…"
  - STOPPED: Toggle on, service stopped

### 2.2 Movement Controls Card
- **Location**: Settings → Movement (or Home Screen)
- **Controls**:
  - Toggle for movement activation
  - Gesture visualization area (shows last gesture)
  - Animation speed slider
  - Pattern selector (straight, curved, random)
- **States**:
  - DEFAULT: Movement disabled
  - ENABLED: Movement active, shows gesture indicators
  - ERROR: Movement service errored

### 2.3 Voice/Speech Control Card
- **Location**: Settings → Voice
- **Controls**:
  - Toggle for voice output
  - Volume slider
  - Speech rate slider
  - Language selector
- **States**:
  - DEFAULT: Voice off, sliders disabled
  - ENABLED: Voice on, sliders active

### 2.4 Status Indicators
- **Listening indicator**: Animated mic icon when listening
- **Speaking indicator**: Animated wave icon when speaking
- **Movement indicator**: Animated floating arrow when moving
- **Error indicator**: Red warning icon with error message

## 3. Layout Requirements

### 3.1 Sound Awareness Screen
```
┌──────────────────────────────┐
│  🔊 Sound Awareness          │
│                              │
│  [━━━━━━━━━━━━━━━] ON       │
│  Listen for sounds around    │
│  you and react to them       │
│                              │
│  ┌────────────────────────┐  │
│  │ Enable microphone      │  │
│  │ [ALLOW]                │  │
│  └────────────────────────┘  │
│                              │
│  Status: Listening every 5s  │
│  [━━━━━━━━━━━━━]            │
│                              │
│  [Back] [Save]               │
└──────────────────────────────┘
```

### 3.2 Movement Screen
```
┌──────────────────────────────┐
│  🎮 Movement Controls        │
│                              │
│  [━━━━━━━━━━━━━━━] ON       │
│  Control companion movement  │
│                              │
│  Animation Speed: [====] 1.0 │
│  Motion Pattern: [Straight]  │
│  Randomize: [✓]              │
│                              │
│  Gestures: TAP, SWIPE, SHAKE │
│                              │
│  [Back] [Save]               │
└──────────────────────────────┘
```

### 3.3 Voice Control Screen
```
┌──────────────────────────────┐
│  🎙️ Voice Controls           │
│                              │
│  [━━━━━━━━━━━━━━━] ON       │
│  Enable companion voice      │
│                              │
│  Volume: [===] 50            │
│  Speed: [===] 1.0x           │
│  Language: [English 🇺🇸]     │
│                              │
│  [Back] [Save]               │
└──────────────────────────────┘
```

## 4. Animation & Transitions
- **Fading transitions**: 200ms between screens
- **Toggle animation**: 150ms sliding indicator
- **Notification**: Brief pulse animation on new sound event
- **Movement indicator**: Pulsing ring around pet

## 5. Interaction Patterns
- **Tap**: Quick response to sound (500ms feedback)
- **Long press**: Enable continuous monitoring
- **Swipe**: Trigger greeting gesture
- **Shake**: Trigger recognition gesture

## 6. Accessibility
- **Screen reader**: All toggles and buttons have descriptive labels
- **Large text**: Support for system font scaling
- **High contrast**: Ensure sufficient contrast ratios
- **Motion reduction**: Respect system preference, reduce animations

## 7. Responsive Design
- **Phone**: Single column layout
- **Tablet**: Two-column layout when space allows

## 8. Error States
- **Permission denied**: Show explanation, force re-request
- **Service unavailable**: Show offline indicator, allow manual start
- **Model loading**: Show loading spinner

## 9. Localization
- Support for English, Bangla, Japanese
- Sound category labels in user's preferred language

## 10. Performance
- Audio processing UI thread isolated
- Animation smooth at 60fps
- Memory usage <50MB for continuous monitoring
