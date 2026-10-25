# PRD.md — CupCake Sound, Mic & Movement Fix

## 1. Overview
- **Project**: CupCake (Android companion app)
- **Document Date**: 10/26/2026
- **Owner**: SWE team (auto-generated via task orchestration)
- **Version**: 1.0

## 2. Problem Statement
The CupCake companion app has three critical issues that prevent it from performing reliably:

### 2.1 Sound Detection (Mic) Issues
- **Symptom**: The app does not respond to sounds despite "sound awareness" being enabled
- **Root Cause**: Missing runtime `RECORD_AUDIO` permission on Android 13+; improper AudioRecord lifecycle management
- **Impact**: Users cannot interact with the companion via sound

### 2.2 Movement / Gesture Issues
- **Symptom**: The virtual pet overlay (ShimejiPetService) does not respond to gestures or user input
- **Root Cause**: Missing gesture processing; PetRigView animation updates not synchronized with service actions
- **Impact**: The companion appears static and unresponsive

### 2.3 Audio/Speech Output Issues
- **Symptom**: The app crashes when attempting sound capture + speech recognition simultaneously
- **Root Cause**: `AudioRecord` and `SpeechRecognizer` conflict when both access the microphone simultaneously
- **Impact**: App crashes (or "gets off") when bot tries to hear and reply

## 3. Goals & Objectives
- **G1**: Fix all three root causes and verify with tests
- **G2**: Ensure audio/sound detection works reliably
- **G3**: Ensure movement/gesture actions respond correctly
- **G4**: Write comprehensive documentation (PRD, Plan, AppFlow, UI_Spec, Schema, TRD)
- **G5**: Commit all changes and push to remote

## 4. Scope
### In Scope
- Sound/mic permission handling
- AudioRecord + SpeechRecognizer thread safety
- Movement/gesture processing in PetRigView
- Documentation files (PRD.md, Plan.md, AppFlow.md, UI_Spec.md, Schema.md, TRD.md)
- Git commits and push

### Out of Scope
- New features not related to sound/mic/movement
- Performance optimization beyond bug fixes
- UI redesign
- New models or assets

## 5. Success Criteria
- [ ] Sound detection responds to all sound categories
- [ ] No crashes when bot tries to hear and reply
- [ ] Movement gestures trigger correctly
- [ ] Documentation files exist and are complete
- [ ] Git push successful to origin/main

## 6. Timeline
- **Phase 1**: Audit & Diagnosis (2h)
- **Phase 2**: Bug Fix (2h)
- **Phase 3**: Documentation (1h)
- **Phase 4**: Validation & Commit (30m)

Total estimated: **5.5 hours**
