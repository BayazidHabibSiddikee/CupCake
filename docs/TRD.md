# TRD.md — CupCake Sound, Mic & Movement Test Requirements Document

## 1. Overview
Test Requirements Document (TRD) for CupCake companion app's sound, mic, and movement features.

## 2. Test Scope
- Sound Detection: Microphone audio capture and YAMNet classification
- Speech Recognition: STT pipeline and integration with chat
- Movement/Gesture: PetRigView animation and gesture handling
- Audio/Speech Output: TTS playback and audio focus management
- Permissions: Runtime permission requests and handling

## 3. Test Cases

### 3.1 Sound Detection Tests

**TC-SOUND-001: Microphone Permission Request**
- Precondition: App launched, navigated to Settings → Sound Awareness
- Action: Tap "Enable Sound Awareness"
- Expected: Permission dialog appears, user can grant/deny
- Pass: Permission dialog shows with Allow/Cancel options
- Fail: No dialog appears or wrong permissions shown

**TC-SOUND-002: Sound Detection - Music**
- Precondition: Sound Awareness enabled, mic permission granted
- Action: Play music or loud beat near phone
- Expected: Bot responds with music-related quip, notification shows "Music detected"
- Pass: Bot responds within 5 seconds with music category
- Fail: No response or incorrect category

**TC-SOUND-003: Sound Detection - Crying**
- Precondition: Sound Awareness enabled
- Action: Play crying/sobbing sound
- Expected: Bot responds with comforting message
- Pass: Bot recognizes "crying" category
- Fail: No response or wrong category

**TC-SOUND-004: Sound Detection - Laughter**
- Precondition: Sound Awareness enabled
- Action: Play laughter sound
- Expected: Bot responds with happy/greeting quip
- Pass: Bot responds with laughter category
- Fail: No response or wrong category

**TC-SOUND-005: Sound Detection - Alert**
- Precondition: Sound Awareness enabled
- Action: Play alarm or siren sound
- Expected: Bot responds with alert/annoyed message
- Pass: Bot recognizes "alert" category
- Fail: No response

**TC-SOUND-006: Sound Detection - Silence/No Sound**
- Precondition: Sound Awareness enabled
- Action: Make no sound
- Expected: Bot does not respond, no notifications
- Pass: No response, battery not drained
- Fail: Frequent false positives

**TC-SOUND-007: Multiple Sounds in Sequence**
- Precondition: Sound Awareness enabled
- Action: Play music → laughter → cry → alarm in sequence
- Expected: Bot responds to each sound correctly
- Pass: Each category responds correctly
- Fail: Some categories missed or wrong category

### 3.2 Speech Recognition Tests

**TC-STT-001: STT Permission Check**
- Precondition: App launched, navigated to Sound Awareness
- Action: Tap toggle to enable
- Expected: Permission dialog appears for RECORD_AUDIO
- Pass: Permission dialog shows
- Fail: No permission dialog

**TC-STT-002: Speech Recognition - Speech**
- Precondition: Sound Awareness enabled
- Action: Speak a phrase
- Expected: STT recognizes speech, transliteration shown in chat
- Pass: Transcript appears in chat
- Fail: No transcript or error

**TC-STT-003: STT - No Speech**
- Precondition: STT enabled
- Action: No speech
- Expected: No transcript, no error
- Pass: No response, no errors

**TC-STT-004: STT - Multiple Attempts**
- Precondition: STT enabled
- Action: Speak multiple phrases
- Expected: Each phrase recognized
- Pass: All phrases transliterated
- Fail: Some phrases missed

### 3.3 Movement/Gesture Tests

**TC-MOVE-001: Gesture Detection - Tap**
- Precondition: Movement controls enabled
- Action: Tap pet overlay
- Expected: Pet responds with tap animation, may emit greeting
- Pass: Animation plays, response triggers
- Fail: No response

**TC-MOVE-002: Gesture Detection - Swipe**
- Precondition: Movement enabled
- Action: Swipe across pet overlay
- Expected: Pet turns toward swipe direction
- Pass: Pet turns correctly
- Fail: No response

**TC-MOVE-003: Gesture Detection - Shake**
- Precondition: Movement enabled
- Action: Shake phone
- Expected: Pet reacts with startle animation
- Pass: Startle animation plays
- Fail: No response

**TC-MOVE-004: Continuous Movement**
- Precondition: Movement enabled
- Action: Leave phone idle for 30 seconds
- Expected: Pet bikes while idle (idle animation)
- Pass: Continuous movement, not stuck
- Fail: Pet stuck or frozen

**TC-MOVE-005: Movement Speed Control**
- Precondition: Movement enabled
- Action: Adjust animation speed slider
- Expected: Animation speed changes
- Pass: Animation matches speed setting
- Fail: Speed not changing

### 3.4 Audio/Speech Tests

**TC-AUDIO-001: TTS Playback**
- Precondition: Voice enabled, mic permission granted
- Action: Trigger sound event (e.g., laughter)
- Expected: Bot speaks with voice
- Pass: Audio plays, text matches expected quip
- Fail: No audio or wrong text

**TC-AUDIO-002: Voice Toggle**
- Precondition: Voice enabled
- Action: Toggle voice off, trigger sound
- Expected: No audio plays
- Pass: No audio
- Fail: Audio still plays

**TC-AUDIO-003: Concurrent Sound + Speech**
- Precondition: Sound Awareness enabled
- Action: When bot speaks, trigger new sound
- Expected: New sound detected, bot pauses and responds
- Pass: Bot responds correctly
- Fail: Crash or no response

## 4. Test Environment Requirements
- Android 13+ device or emulator
- 4GB+ RAM
- Microphone functional
- Audio recording permission granted
- Battery optimization disabled (for continuous monitoring)

## 5. Test Data
- Sound files: music.mp3, crying.mp3, laughter.mp3, scream.mp3, alarm.mp3
- Test phrases: "Hello CupCake", "I need your help", "Tell me a story"

## 6. Automation Strategy
- **Unit Tests**: JUnit5 for service logic, SpeechToTextManager, SoundClassifier
- **Integration Tests**: Robotium/Uiautomator for UI and service level
- **E2E Tests**: Full flow testing with FFmpeg for sound generation

## 7. Test Metrics
- **Coverage Target**: 80%+
- **Latency Budget**: Sound detection <5s, STT <3s, TTS <1s
- **Reliability**: 99.9% uptime during testing

## 8. Defect Reporting
- Severity: Critical (app crash), High (feature missing), Medium (bug), Low (cosmetic)
- Priority: P0 (must fix), P1 (should fix), P2 (nice to have)
- Status: New → In Progress → In Review → Done

## 9. Regression Tests
- Existing tests for ChatViewModel, GameManager, PersonaEngine
- Ensure no regression in existing features

## 10. Test Evidence
- Screenshots of test results
- Video recordings of key interactions
- Logcat excerpts for diagnosis

## 11. Test Sign-off
- [ ] Test plan approved
- [ ] Test cases executed
- [ ] Defects logged
- [ ] Acceptance criteria verified
- [ ] Test report attached

## 12. Notes
- All tests should be reproducible
- Test data should be documented
- Critical bugs should be logged with steps to reproduce
