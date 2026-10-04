# System Prompt with Image Support

## Overview

The CupCake app supports rich system prompts that can include both text and images. Users can upload images (diagrams, documentation, screenshots, code snippets) which are processed via OCR/vision to extract text content, then combined with the text prompt to form a complete system instruction.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTEM PROMPT FLOW                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Uploads Images                                            │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────┐    ┌─────────────────┐                   │
│  │  Image Picker   │───►│  OCR/Vision     │                   │
│  │  (Gallery/Cam)  │    │  Processing     │                   │
│  └─────────────────┘    └────────┬────────┘                   │
│                                  │                             │
│                                  ▼                             │
│  ┌─────────────────┐    ┌─────────────────┐                   │
│  │  System Prompt  │◄───│  Extracted Text │                   │
│  │  Editor         │    │  + Descriptions │                   │
│  └────────┬────────┘    └─────────────────┘                   │
│           │                                                   │
│           ▼                                                   │
│  ┌─────────────────────────────────────────┐                 │
│  │         Combined System Prompt          │                 │
│  │  [Text Prompt]                          │                 │
│  │  [Image 1: description + extracted text]│                 │
│  │  [Image 2: description + extracted text]│                 │
│  │  ...                                    │                 │
│  └────────────────┬────────────────────────┘                 │
│                   │                                           │
│                   ▼                                           │
│  ┌─────────────────────────────────────────┐                 │
│  │      Prepended to Every Conversation    │                 │
│  └─────────────────────────────────────────┘                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Model

```kotlin
data class SystemPrompt(
    val id: String,
    val name: String,                    // User-friendly name
    val text: String,                    // Main prompt text
    val images: List<PromptImage>,       // Attached images
    val createdAt: Instant,
    val version: Int                     // For optimistic updates
)

data class PromptImage(
    val id: String,
    val uri: String,                     // content:// URI
    val mimeType: String,                // image/png, image/jpeg, etc.
    val description: String,             // User-provided description
    val extractedText: String,           // OCR/vision extracted text
    val thumbnailUri: String?,           // Cached thumbnail
    val uploadedAt: Instant
)
```

## Image Processing Pipeline

### 1. Upload & Storage
- User selects images via system picker (gallery, camera, files)
- Images copied to app's private files directory
- Thumbnails generated (256x256) for UI display
- Metadata stored in Room database

### 2. Text Extraction (Async)
- **On-device OCR**: ML Kit Text Recognition (supports 100+ languages)
- **Vision LLM** (optional): Send to vision-capable model (GPT-4V, LLaVA, Qwen-VL)
- Extracted text stored alongside image metadata

### 3. Prompt Composition
When a conversation uses a system prompt with images:

```kotlin
fun SystemPrompt.fullPrompt(): String {
    val builder = StringBuilder(text)
    if (images.isNotEmpty()) {
        builder.append("\n\n=== ATTACHED VISUAL CONTEXT ===")
        images.forEachIndexed { index, image ->
            builder.append("\n\n[Image ${index + 1}: ${image.description}]")
            if (image.extractedText.isNotEmpty()) {
                builder.append("\nExtracted Text:\n${image.extractedText}")
            }
        }
        builder.append("\n=== END VISUAL CONTEXT ===")
    }
    return builder.toString()
}
```

## UI Flow

### System Prompt Management Screen
1. **List View**: All saved prompts with preview
2. **Create/Edit**: Name, text area, image attachments
3. **Image Picker**: Gallery/camera/document picker
4. **Image Cards**: Thumbnail, description, extracted text preview
5. **Delete**: Remove images or entire prompt

### Conversation Integration
1. **Chat Header**: Shows active system prompt name + image count
2. **Settings**: Select system prompt per conversation
3. **Model Config**: Links system prompt to model configuration

## Implementation Details

### Database Schema
```sql
CREATE TABLE system_prompts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    text TEXT NOT NULL,
    images TEXT NOT NULL,  -- JSON array of PromptImage
    created_at INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);
```

### Repository Interface
```kotlin
interface SystemPromptRepository {
    fun getAllPrompts(): Flow<List<SystemPrompt>>
    suspend fun getPrompt(id: String): SystemPrompt?
    suspend fun savePrompt(prompt: SystemPrompt): SystemPrompt
    suspend fun updatePrompt(prompt: SystemPrompt)
    suspend fun deletePrompt(id: String)
    suspend fun addImageToPrompt(promptId: String, image: PromptImage): SystemPrompt
}
```

### Use Cases
- `GetSystemPromptsUseCase`: List all prompts
- `GetSystemPromptUseCase`: Get single prompt
- `SaveSystemPromptUseCase`: Create new prompt
- `UpdateSystemPromptUseCase`: Update existing
- `DeleteSystemPromptUseCase`: Delete prompt
- `AddImageToPromptUseCase`: Attach image to prompt

## OCR Integration (ML Kit)

```kotlin
// In a Worker or Coroutine
private suspend fun extractTextFromImage(uri: String): String {
    val inputImage = InputImage.fromFilePath(context, Uri.parse(uri))
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(inputImage).await()
    return result.text
}
```

## Privacy & Security

- Images stored in app-private storage (not accessible to other apps)
- No automatic cloud upload for OCR (uses on-device ML Kit)
- User can delete images/prompts at any time
- Encrypted database (SQLCipher) for sensitive prompts

## Future Enhancements

1. **Vision Model Integration**: Use Qwen-VL or LLaVA for better image understanding
2. **Prompt Templates**: Pre-built prompts for common use cases
3. **Version History**: Track changes to system prompts
4. **Sharing**: Export/import prompts with images
5. **Auto-categorization**: ML-based prompt categorization