# Plan.md — CupCake Sound, Mic & Movement Fix

## 1. Current State

### 6.1 Existing Files & Code
- `app/src/main/java/com/cupcake/service/SoundReactionService.kt` — Microphone sound capture + YAMNet classification
- `app/src/main/java/com/cupcake/stt/SpeechToTextManager.kt` — STT transcription bridge
- `app/src/main/java/com/cupcake/tts/LocalSpeaker.kt` — On-device TTS player
- `app/src/main/java/com/cupcake/pet/PetRigView.kt` — Virtual pet animation + gesture overlay
- `app/src/main/java/com/cupcake/service/ShimejiPetService.kt` — Floating pet service
- `app/src/main/java/com/cupcake/MainActivity.kt` — Main activity with permission handling

### 6.2 Existing Tests
- `app/src/test/java/com/cupcake/service/SoundReactionServiceTest.kt`
- `app/src/test/java/com/cupcake/stt/SpeechToTextManagerTest.kt`

### 6.3 Documentation
- `docs/PRD.md`, `docs/Plan.md`, `docs/AppFlow.md`, `docs/UI_Spec.md`, `docs/Schema.md`, `docs/TRD.md`

## 2. Goals & Objectives
1. Fix sound detection (mic) issues
2. Fix movement/gesture issues
3. Fix audio/speech output issues
4. Write comprehensive documentation
5. Commit and push changes

## 3. Methodology
- Phase 1: Audit & Diagnosis
- Phase 2: Bug Fix
- Phase 3: Documentation
- Phase 4: Validation

## 4. Timeline
- **Day 1**: Audit, diagnosis, bug fixes
- **Day 2**: Documentation, validation, push

## 5. Risks & Mitigations
- **Risk**: Microphone permission missing at runtime → Mitigation: Add permission request flow
- **Risk**: AudioRecord + SpeechRecognizer conflict → Mitigation: Sequential execution with proper lifecycle management
- **Risk**: Movement/gesture desync → Mitigation: Verify animation state updates

## 6. Acceptance Criteria
- [ ] Sound detection works (tested with real sounds)
- [ ] Movement/gesture triggers correctly
- [ ] No crashes when hearing and speaking
- [ ] Documentation files complete
- [ ] Git push successful

## 7. Key Decisions
- **Decision 1**: AudioRecord must stop before STT, restart after
- **Decision 2**: SpeechToTextManager must check RECORD_AUDIO permission
- **Decision 3**: ParticleView animation must sync with service actions

## 8. Dependencies
- None (self-contained fix)

## 9. Assumptions
- All required Android permissions can be granted at runtime
- Build environment has necessary dependencies
- Git remote is configured correctly

## 10. Open Questions
- None

## 11. Notes
- Last commit: 9b922d5 (STT mic arbitration, shrink pet ~20%, hide raw model names)
- Documentation files will be created in docs/ directory
