# PRD.md — CupCake Activity-Aware Companion

## 1. Overview
- **Project**: CupCake (Android companion app)
- **Document Date**: 10/26/2026
- **Owner**: SWE Team (auto-generated via task orchestration)
- **Version**: 1.0

## 2. Problem Statement
The CupCake companion app currently responds to user input and sounds, but lacks contextual awareness of what the user is doing. The companion should:
- Detect what apps/activities the user is actively using (gaming, YouTube/social media, browsing, chatting, productivity)
- Adapt its personality and responses based on the detected activity
- Make the companion more creative, interactive, and contextually relevant
- Leverage the AI engine to generate activity-aware responses

## 3. Goals & Objectives
- **G1**: Implement activity/process classification system using Android APIs
- **G2**: Pass detected activity context to the AI engine as metadata
- **G3**: Modify AI responses based on detected activity context
- **G4**: Make the companion's reactions more creative and interactive
- **G5**: Document the full architecture and implementation

## 4. Scope
### In Scope
- Activity/process monitoring using Android APIs (AccessibilityService, ActivityManager, etc.)
- Classification system that identifies: gaming, YouTube/social media, browsing, chatting, productivity, other
- AI engine integration to pass context to the LLM
- Companion response modification based on activity context
- UI indicators showing the companion's awareness

### Out of Scope
- Automatic app switching or control (only monitoring, not control)
- Background monitoring when app is completely closed (except where necessary)
- Integration with non-Android platforms
- Performance optimization beyond minimal requirements

## 5. System Architecture

### 5.1 Component Diagram
```
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│  Activity     │     │ ActivityMonitor │     │ Classification│
│  Watcher      │────▶│  Service        │────▶│  Engine      │
│  (Accessibility/ActivityManager)│      │              │
└──────────────┘     └──────────────────┘     └──────────────┘
                              │
                              ▼
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│  Context      │◀─────│  AI Engine       │◀────│  Chat        │
│  Builder      │     │  (Llama/Ollama)  │     │  ViewModel   │
└──────────────┘     └──────────────────┘     └──────────────┘
```

### 5.2 Key Flows
1. **Activity Monitoring**: Watch for foreground activity changes
2. **Classification**: Use app package names to classify activity type
3. **Context Building**: Create context metadata for AI prompts
4. **AI Response**: Modify AI response based on detected activity

## 6. Test Cases
- Test activity detection for known apps
- Test context passing to AI engine
- Test AI response modification based on context
- Test edge cases (minimized app, background state)

## 7. Timeline
- **Phase 1**: Research and design (1h)
- **Phase 2**: Implementation (2h)
- **Phase 3**: Testing and validation (1h)
- **Total**: 4 hours
