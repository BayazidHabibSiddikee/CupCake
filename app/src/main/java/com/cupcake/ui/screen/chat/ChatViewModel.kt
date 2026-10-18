package com.cupcake.ui.screen.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.LlamaEngine
import com.cupcake.ai.OllamaEngine
import com.cupcake.ai.PromptTemplates
import com.cupcake.data.model.Character
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.SystemPrompt
import com.cupcake.data.UserProfileStore
import com.cupcake.game.GameManager
import com.cupcake.network.EspWebSocketServer
import com.cupcake.service.ShimejiPetService
import com.cupcake.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.cupcake.chat.ChatSessionStore
import com.cupcake.domain.usecase.GetApiProvidersUseCase
import com.cupcake.domain.repository.ModelRepository
import com.cupcake.domain.repository.SystemPromptRepository
import com.cupcake.data.model.ApiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: ChatSessionStore,
    private val ollamaEngine: OllamaEngine,
    private val characterManager: CharacterManager,
    private val gameManager: GameManager,
    private val espServer: EspWebSocketServer,
    private val ttsManager: TtsManager,
    private val localSpeaker: com.cupcake.tts.LocalSpeaker,
    private val energyManager: EnergyManager,
    private val getApiProvidersUseCase: GetApiProvidersUseCase,
    private val modelRepository: ModelRepository,
    private val systemPromptRepository: SystemPromptRepository,
    private val modelDownloadManager: com.cupcake.ai.ModelDownloadManager,
    private val userProfileStore: UserProfileStore
) : ViewModel() {

    val characterId: String =
        savedStateHandle.get<String>("characterId") ?: "cupcake_kitagawa"

    val conversationId: String = sessionStore.conversationIdFor(characterId)

    sealed interface ModelLoadState {
        data object Idle : ModelLoadState
        data class Loading(val detail: String = "") : ModelLoadState
        data object Ready : ModelLoadState
        data class Error(val message: String) : ModelLoadState
    }

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val modelLoadState = _modelLoadState.asStateFlow()

    // UI State - messages live in the shared per-character session store,
    // so leaving and reopening a character resumes the same session.
    val messages: kotlinx.coroutines.flow.StateFlow<List<ChatMessage>> =
        sessionStore.messages(characterId)

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse = _currentResponse.asStateFlow()

    private val _currentCharacter = MutableStateFlow<Character?>(null)
    val currentCharacter = _currentCharacter.asStateFlow()

    private val _energy = MutableStateFlow(0L)
    val energy = _energy.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro = _isPro.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    private val _modelConfig = MutableStateFlow(ModelConfig.default())
    val modelConfig = _modelConfig.asStateFlow()

    /** Configured remote providers, offered by the chat config sheet. */
    private val _providers = MutableStateFlow<List<ApiProvider>>(emptyList())
    val providers = _providers.asStateFlow()

    private val _systemPrompt = MutableStateFlow<SystemPrompt?>(null)
    val systemPrompt = _systemPrompt.asStateFlow()

    private val _gameState = MutableStateFlow<com.cupcake.game.GameManager.Result?>(null)
    val gameState = _gameState.asStateFlow()

    /** Live face stem the companion is "wearing" while it talks (drives the in-app face + ESP). */
    private val _liveEmotionStem = MutableStateFlow<String?>(null)
    val liveEmotionStem = _liveEmotionStem.asStateFlow()

    private var lastPushedFaceStem: String? = null

    /** Job that occasionally lets the companion speak first while the chat is open. */
    private var idleChatterJob: Job? = null

    /** Updates the live emotion face, the floating pet, and pushes the image to the ESP board. */
    private fun pushEmotion(stem: String, duration: Int = 5000, tag: String? = null) {
        _liveEmotionStem.value = stem
        if (stem == lastPushedFaceStem) return
        lastPushedFaceStem = stem

        // Reflect the emotion on the floating companion overlay (static art, no VRM needed).
        if (ShimejiPetService.isRunning) {
            ShimejiPetService.setFace(appContext, com.cupcake.ai.EmotionAssets.assetForStem(stem))
        } else {
            // Overlay denied or service dead: hold the face and replay it on the
            // next attach instead of silently dropping it.
            ShimejiPetService.stashFace(com.cupcake.ai.EmotionAssets.assetForStem(stem))
        }

        // The raw tag carries the richer firmware vocabulary (heart_eyes, wink,
        // rage, excited) that folding to the canonical art stem would destroy.
        espServer.sendFaceExpression(
            expression = com.cupcake.ai.EmotionAssets.espFaceForTag(tag)
                ?: com.cupcake.ai.EmotionAssets.espFaceForStem(stem),
            duration = duration,
            emotion = stem,
            imagePath = com.cupcake.ai.EmotionAssets.httpPathForStem(stem)
        )
    }

    /** Re-derives the current emotion from partially/fully generated text and pushes on change. */
    private fun refreshLiveEmotion(text: String) {
        if (text.length < 3) return
        val stem = com.cupcake.ai.EmotionAssets.stemForText(text)
        if (stem != _liveEmotionStem.value) pushEmotion(stem, tag = com.cupcake.ai.EmotionAssets.tagOf(text))
    }

    private var systemPromptCache = ""

    init {
        setupObservers()
        setupGameReactions()
        setupEspCallbacks()
        loadCurrentCharacter()
        ensureModelLoaded()
        scheduleIdleChatter()
    }

    /**
     * After a random idle interval the companion may drop a spontaneous one-liner
     * into the chat and pop it up in the floating character's speech bubble.
     */
    private fun scheduleIdleChatter() {
        idleChatterJob?.cancel()
        idleChatterJob = viewModelScope.launch {
            delay(45_000L + kotlin.random.Random.nextLong(90_000L))
            maybeSendIdleChatter()
            scheduleIdleChatter()
        }
    }

    private fun maybeSendIdleChatter() {
        if (_isGenerating.value) return
        if (messages.value.isEmpty()) return
        if (kotlin.random.Random.nextInt(100) >= 35) return

        val text = IDLE_CHATTER_LINES.random()
        addMessage(ChatMessage.assistant(text, conversationId, modelUsed = "idle"))
        refreshLiveEmotion(text)

        if (ShimejiPetService.isRunning) {
            ShimejiPetService.showSpeech(
                appContext,
                text,
                com.cupcake.ai.EmotionAssets.assetForText(text)
            )
        }
    }

    /**
     * Checks model availability and loads on-device model or verifies remote connection.
     */
    fun ensureModelLoaded() {
        val config = _modelConfig.value
        if (config.isLocal()) {
            val targetModel = config.modelName
            if (LlamaEngine.getInstance().isReady() && LlamaEngine.getInstance().getLoadedModelName() == targetModel) {
                _modelLoadState.value = ModelLoadState.Ready
                drainPendingQueue()
                return
            }
            _modelLoadState.value = ModelLoadState.Loading("Loading $targetModel...")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    LlamaEngine.initialize(appContext)
                    val result = LlamaEngine.getInstance().loadModel(
                        context = appContext,
                        config = LlamaEngine.Config(
                            modelFileName = "models/$targetModel",
                            nCtx = 2048,
                            nThreads = com.cupcake.ai.recommendedThreadCount(),
                            nBatch = 512,
                            maxTokens = config.maxTokens,
                            temperature = config.temperature,
                            topP = config.topP,
                            topK = config.topK
                        )
                    ) { copiedBytes, totalBytes ->
                        if (totalBytes > 0) {
                            val pct = (copiedBytes * 100 / totalBytes).toInt()
                            _modelLoadState.value = ModelLoadState.Loading("Loading model: $pct%")
                        }
                    }
                    _modelLoadState.value = if (result.isSuccess) {
                        ModelLoadState.Ready
                    } else {
                        ModelLoadState.Error(result.exceptionOrNull()?.message ?: "Failed to load model")
                    }
                    if (result.isSuccess) drainPendingQueue()
                } catch (e: Exception) {
                    _modelLoadState.value = ModelLoadState.Error(e.message ?: "Failed to load model")
                }
            }
        } else {
            _modelLoadState.value = ModelLoadState.Loading("Connecting...")
            viewModelScope.launch(Dispatchers.IO) {
                val baseUrl = config.effectiveEndpoint()
                val result = ollamaEngine.checkConnection(baseUrl)
                result.consumeEach { connectionResult ->
                    _modelLoadState.value = connectionResult.fold(
                        onSuccess = { connected ->
                            if (connected) ModelLoadState.Ready
                            else ModelLoadState.Error("Init failed")
                        },
                        onFailure = { ModelLoadState.Error(it.message ?: "Connection failed") }
                    )
                }
                if (_modelLoadState.value is ModelLoadState.Ready) drainPendingQueue()
            }
        }
    }

    private fun setupObservers() {
        viewModelScope.launch {
            energyManager.energy.collect { energy ->
                _energy.value = energy
            }
        }
        viewModelScope.launch {
            energyManager.isPro.collect { pro ->
                _isPro.value = pro
            }
        }
        viewModelScope.launch {
            modelDownloadManager.activeModel.collect { activeLocalModel ->
                if (_modelConfig.value.isLocal()) {
                    if (_modelConfig.value.modelName != activeLocalModel) {
                        _modelConfig.value = _modelConfig.value.copy(modelName = activeLocalModel)
                        ensureModelLoaded()
                    }
                }
            }
        }
        viewModelScope.launch {
            getApiProvidersUseCase().collect { providerList ->
                _providers.value = providerList
                val active = providerList.firstOrNull { it.isEnabled }
                if (active != null) {
                    val fallback = if (active.providerType == ModelConfig.ModelProvider.CUSTOM_OLLAMA) "qwen2.5:0.5b" else "gpt-3.5-turbo"
                    val chosenModel = active.models.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallback
                    val newConfig = _modelConfig.value.copy(
                        provider = active.providerType,
                        customEndpoint = active.baseUrl,
                        apiKey = active.apiKey,
                        modelName = chosenModel
                    )
                    _modelConfig.value = newConfig
                } else {
                    val localModel = modelDownloadManager.activeModel.value
                    _modelConfig.value = ModelConfig(
                        provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
                        modelName = localModel
                    )
                }
                ensureModelLoaded()
            }
        }
    }

    private fun setupGameReactions() {
        // GameManager persists every reaction/comment into this character's chat
        // session itself, so we only handle the live effects here (TTS + ESP32).
        viewModelScope.launch {
            gameManager.gameReactions.collect { reaction ->
                // Face follows what the companion is actually saying (same art as the phone).
                refreshLiveEmotion(reaction.text)

                // Motor action
                val motorAction = if (reaction.humanWon) EspWebSocketServer.MotorAction.TANTUM
                else EspWebSocketServer.MotorAction.DANCE
                espServer.sendMotorAction(motorAction)

                // TTS
                speakResponse(reaction.text)
            }
        }
    }

    private fun setupEspCallbacks() {
        espServer.setOnConnectionChangeListener { sessionId, connected ->
            viewModelScope.launch {
                _connectedDevices.value = espServer.getConnectedDevices()
            }
        }
        _connectedDevices.value = espServer.getConnectedDevices()
    }

    private fun loadCurrentCharacter() {
        val character = characterManager.getCharacter(characterId)
            ?: characterManager.getCurrentCharacter()
        _currentCharacter.value = character
        if (character != null) {
            systemPromptCache = characterManager.getSystemPromptForCharacter(character.id)
            val rawFirst = character.firstMessage?.ifBlank { null }
                ?: "*smiles cheerfully and waves* Hey! I was waiting for you! What's on your mind today?"
            if (sessionStore.messages(character.id).value.isEmpty()) {
                val cleanFirst = PromptTemplates.replaceTemplateMacros(rawFirst, character.name, userName())
                sessionStore.addMessage(
                    character.id,
                    ChatMessage.assistant(
                        content = cleanFirst,
                        conversationId = sessionStore.conversationIdFor(character.id)
                    )
                )
            }
        }
    }

    /** Name to address the user by (from the Settings profile form). */
    private fun userName(): String = userProfileStore.current().displayName()

    /** Extra system-prompt lines describing the user, or "" when the profile is empty. */
    private fun userProfileBlock(): String = userProfileStore.current().toPromptBlock()

    private fun buildOllamaMessages(): List<OllamaEngine.OllamaMessage> {
        val charName = _currentCharacter.value?.name ?: "Companion"
        val messageList = mutableListOf<OllamaEngine.OllamaMessage>()
        
        // Add system prompt if available
        val rawSys = _systemPrompt.value?.fullPrompt() ?: systemPromptCache
        if (rawSys.isNotBlank()) {
            val cleanSys = PromptTemplates.replaceTemplateMacros(rawSys, charName, userName())
            val profile = userProfileBlock()
            val mood = com.cupcake.ai.MoodStore.angerPrompt(appContext)
            val withMood = listOf(cleanSys, profile, mood).filter { it.isNotBlank() }.joinToString("\n")
            messageList.add(OllamaEngine.OllamaMessage(role = "system", content = withMood))
        }
        
        // Add conversation messages
        val conversationMessages = messages.value
        val hasAssistant = conversationMessages.any { it.role == ChatMessage.MessageRole.ASSISTANT }
        if (!hasAssistant) {
            val defaultGreeting = _currentCharacter.value?.firstMessage?.ifBlank { null }
                ?: "*smiles cheerfully and waves* Hey! I was waiting for you! What's on your mind today?"
            val cleanGreeting = PromptTemplates.replaceTemplateMacros(defaultGreeting, charName, userName())
            messageList.add(OllamaEngine.OllamaMessage(role = "assistant", content = cleanGreeting))
        }

        for (msg in conversationMessages) {
            val role = when (msg.role) {
                ChatMessage.MessageRole.USER -> "user"
                ChatMessage.MessageRole.ASSISTANT -> "assistant"
                ChatMessage.MessageRole.SYSTEM -> "system"
                ChatMessage.MessageRole.TOOL -> "tool"
            }
            val cleanContent = PromptTemplates.replaceTemplateMacros(msg.content, charName, userName())
            messageList.add(OllamaEngine.OllamaMessage(role = role, content = cleanContent))
        }
        
        return messageList
    }

    fun sendMessage(text: String) {
        val sanitized = sanitizeInput(text)
        if (sanitized.isBlank()) return

        // Queue instead of dropping: while a reply is streaming, a follow-up text
        // used to be silently eaten by the isGenerating gate.
        if (_isGenerating.value) {
            pendingUserMessages.addLast(sanitized)
            return
        }

        // Preserve the user's message bubble BEFORE the model-lock check below —
        // bailing without adding the message is what made "emoji-only" texts
        // disappear. The inverse also pays off: the sanitized text is what the
        // user sees on screen, so don't mangle quotes/emojis away.
        val config = _modelConfig.value
        val isReady = if (config.isLocal()) LlamaEngine.getInstance().isReady() else ollamaEngine.isReady()
        if (!isReady) {
            ensureModelLoaded()
            addMessage(
                ChatMessage.system(
                    if (config.isLocal()) "⏳ Local model is loading, please wait a moment..."
                    else "⏳ Model is still loading, please wait a moment and try again.",
                    conversationId
                )
            )
            // Hold the text and replay it once the engine finishes loading instead
            // of silently eating the message.
            pendingUserMessages.addLast(sanitized)
            return
        }

        // Check energy
        energyManager.consumeEnergy() // Consume it but don't block if they don't have it (make it simple!)

        val userMessage = ChatMessage.user(sanitized, conversationId)
        addMessage(userMessage)
        com.cupcake.ai.ProactiveEngagementManager.onUserActivity(appContext)
        scheduleIdleChatter()

        startGeneration(sanitized, config)
    }

    private val pendingUserMessages = ArrayDeque<String>()

    /** Replays the last held message when the engine becomes usable. */
    private fun drainPendingQueue() {
        if (_isGenerating.value) return
        val next = pendingUserMessages.removeFirstOrNull() ?: return
        viewModelScope.launch { sendMessage(next) }
    }

    /** Head of the work: bubble already added, model confirmed ready. */
    private fun startGeneration(userText: String, config: ModelConfig) = viewModelScope.launch {
        _isGenerating.value = true
        _currentResponse.value = ""
        // Show a "thinking" face the moment the companion starts composing.
        pushEmotion("thinking")

        var fullResponse = ""

            try {
                if (config.isLocal()) {
                    val prompt = buildPrompt()
                    // Grammar ladder: the fully-forced `[tag] *action* reply`
                    // grammar can wedge a 0.6B model into an empty decode until
                    // the 90s watchdog fires. Retry downwards, last run fully
                    // unconstrained, rather than dropping the turn.
                    val engine = LlamaEngine.getInstance()
                    val grammarLadder = listOf(
                        com.cupcake.ai.EmotionGrammar.TAG_ACTION,
                        com.cupcake.ai.EmotionGrammar.TAG_ONLY,
                        null
                    )
                    var decodeFailed: Exception? = null
                    for (grammar in grammarLadder) {
                        engine.setGrammar(grammar)
                        var attempt = ""
                        var tick = 0
                        try {
                            for (token in engine.generateStream(prompt, preempt = true)) {
                                attempt += token
                                _currentResponse.value = attempt
                                if (++tick % 3 == 0) refreshLiveEmotion(attempt)
                            }
                            decodeFailed = null
                        } catch (e: Exception) {
                            decodeFailed = e
                            Log.w(
                                "ChatViewModel",
                                "attempt with grammar=${grammar?.take(10) ?: "none"} failed: ${e.message}"
                            )
                        }
                        refreshLiveEmotion(attempt)
                        if (attempt.isNotBlank()) {
                            fullResponse = attempt
                            break
                        }
                    }
                    if (fullResponse.isBlank() && decodeFailed != null) throw decodeFailed
                } else {
                    val personality = currentCharacter.value?.personality ?: ""
                    val messages = buildOllamaMessages()
                    val endpoint = config.effectiveEndpoint()
                    var tick = 0
                    ollamaEngine.generateStream(
                        baseUrl = endpoint,
                        config = config,
                        messages = messages,
                        callback = object : OllamaEngine.GenerateCallback {
                            override fun onToken(token: String) {
                                fullResponse += token
                                _currentResponse.value = fullResponse
                                if (++tick % 3 == 0) refreshLiveEmotion(fullResponse)
                            }

                            override fun onComplete() {
                                // Handled in finally — translating here flashed a
                                // fully "translated" bubble mid-stream and then the
                                // finally-block translated AGAIN (garbled flicker).
                            }

                            override fun onError(error: String) {
                                Log.e("ChatViewModel", "Generation failed: $error")
                                addMessage(
                                    ChatMessage.system(
                                        "❌ Request failed: $error",
                                        conversationId
                                    )
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Generation failed", e)
                addMessage(
                    ChatMessage.system(
                        "❌ Generation failed: ${e.message}",
                        conversationId
                    )
                )
            } finally {
                _isGenerating.value = false
                _currentResponse.value = ""

                if (fullResponse.isNotBlank()) {
                    // Settle the live face on the raw response (containing tags) BEFORE translation
                    refreshLiveEmotion(fullResponse.trim())

                    // Translate output
                    val personality = currentCharacter.value?.personality ?: ""
                    val translatedText = com.cupcake.ai.BanglishTranslator.translate(fullResponse.trim(), personality)

                    // Add assistant message
                    val assistantMessage = ChatMessage.assistant(
                        translatedText,
                        conversationId,
                        modelUsed = _modelConfig.value.modelName
                    )
                    addMessage(assistantMessage)

                    // Send to ESP32 for TTS (use translated text for speech)
                    sendToEspForTts(translatedText)
                } else {
                    // Zero-token stream (HTTP error body, non-stream reply the
                    // parser didn't understand, or a wedged engine) used to vanish
                    // the typing bubble with no error. Keep the companion alive by
                    // answering in-character instead of dead air.
                    val fallbackName = _currentCharacter.value?.name ?: "CupCake"
                    val fallbackLine = when ((0..2).random()) {
                        0 -> "*tilts head* Hmm… $fallbackName zoned out for a second there. Say that again?"
                        1 -> "*scratches head* My thoughts got all fuzzy… what were we talking about?"
                        else -> "*blinks* Eh? I lost you for a moment~ try me again! ✨"
                    }
                    addMessage(
                        ChatMessage.assistant(
                            fallbackLine,
                            conversationId,
                            modelUsed = _modelConfig.value.modelName
                        )
                    )
                }

                // A pending follow-up (or a held "model loading" text) now gets its
                // turn instead of vanishing.
                drainPendingQueue()
            }
        }

    /** Removes invisible padding — nothing else. Stripping quotes/emojis here
     *  mangled authentic input ("i love you 😍") and made emoji-only texts drop
     *  as blank; the 0.6B model handles raw text fine. */
    private fun sanitizeInput(text: String): String {
        return text.trim()
    }

    private fun buildPrompt(): String {
        val charName = _currentCharacter.value?.name ?: "CupCake"
        val customSys = _systemPrompt.value?.fullPrompt()
        val rawSystemPrompt = if (!customSys.isNullOrBlank()) {
            customSys
        } else {
            characterManager.getSystemPromptForCharacter(
                _currentCharacter.value?.id ?: characterId
            )
        }
        
        // Compact system prompt and sanitize macros
        val cleanSystemPrompt = PromptTemplates.replaceTemplateMacros(rawSystemPrompt, charName, userName())
        // Nudge the small on-device model into the cute [emotion] *action* reply format
        val profile = userProfileBlock()
        // The style line must always survive the length budget below, otherwise the
        // model silently loses the mood-tag + emoji instructions on long characters.
        val style = com.cupcake.ai.LilTiiPromptTemplate.CUTE_STYLE
        val body = buildString {
            append(cleanSystemPrompt)
            if (profile.isNotBlank()) {
                append("\n")
                append(profile)
            }
        }
        val bodyBudget = (1400 - style.length - 1).coerceAtLeast(200)
        // Mood is appended *after* the length budget so a grumpy/silent-treatment line
        // can never be truncated away on long characters.
        val mood = com.cupcake.ai.MoodStore.angerPrompt(appContext)
        val moodLine = if (mood.isNotBlank()) "\n$mood" else ""
        val systemPrompt = "$style\n" + (if (body.length > bodyBudget) body.take(bodyBudget) else body) + moodLine
        
        val builder = StringBuilder()
        builder.append("<|im_start|>system\n")
        builder.append(systemPrompt)
        builder.append("<|im_end|>\n")
        
        // Take recent dialogue messages budgeted to fit within context (last 6 messages)
        val recentMessages = messages.value.filter {
            it.role == ChatMessage.MessageRole.USER || it.role == ChatMessage.MessageRole.ASSISTANT
        }.takeLast(6)

        // Cold-start anchor: If no prior assistant message exists in history,
        // provide the character's initial greeting so small models learn the voice, formatting, and length.
        val hasAssistantHistory = recentMessages.any { it.role == ChatMessage.MessageRole.ASSISTANT }
        if (!hasAssistantHistory) {
            val defaultGreeting = _currentCharacter.value?.firstMessage?.ifBlank { null }
                ?: "*smiles cheerfully and waves* Hey! I was waiting for you! What's on your mind today?"
            val cleanGreeting = PromptTemplates.replaceTemplateMacros(defaultGreeting, charName, userName())
            builder.append("<|im_start|>assistant\n")
            builder.append(cleanGreeting)
            builder.append("<|im_end|>\n")
        }

        for (msg in recentMessages) {
            val rawContent = if (msg.content.length > 400) msg.content.take(400) else msg.content
            val cleanContent = PromptTemplates.replaceTemplateMacros(rawContent, charName, userName())
            val role = if (msg.role == ChatMessage.MessageRole.USER) "user" else "assistant"
            builder.append("<|im_start|>")
            builder.append(role)
            builder.append("\n")
            builder.append(cleanContent)
            builder.append("<|im_end|>\n")
        }

        builder.append("<|im_start|>assistant\n")
        return builder.toString()
    }

    private fun addMessage(message: ChatMessage) {
        sessionStore.addMessage(characterId, message)
    }

    /**
     * One voice pipeline, two destinations. sendToEspForTts()/speakResponse() used
     * to build two independent TTS streams that raced on identical millisecond
     * utterance IDs and could double-speak or double-stream. Everything — the
     * phone speaker, the ESP "speak" command and the ESP audio stream — now shares
     * one synthesized stream.
     */
    private fun speakAsCompanion(rawText: String) {
        // Same strip the local path applies: [emotion] tags and *actions* are
        // choreography, not speech. ESP text and audio chunks must never see them.
        val speech = com.cupcake.ai.EmotionAssets.speechText(rawText)
        if (speech.isBlank()) return
        // ESP firmware TTS (spoken by the doll itself)
        espServer.sendCommand("speak", mapOf("text" to speech))
        // Phone speaker (deliberately no audio focus — mixes over background music)
        localSpeaker.speak(speech)
        // Raw PCM stream to any connected ESP speaker. The TTS writes a WAV
        // envelope; the firmware wants bare PCM, so drop the RIFF header.
        viewModelScope.launch(Dispatchers.IO) {
            var headerHandled = false
            try {
                ttsManager.speakStreaming(speech) { chunk ->
                    val clean = if (!headerHandled && chunk.size > 48 &&
                        chunk[0] == 'R'.toByte() && chunk[1] == 'I'.toByte() &&
                        chunk[2] == 'F'.toByte() && chunk[3] == 'F'.toByte()
                    ) {
                        headerHandled = true
                        chunk.copyOfRange(44, chunk.size)
                    } else {
                        chunk
                    }
                    espServer.sendAudio(clean)
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "ESP audio stream failed: ${e.message}")
            }
        }
    }

    private fun sendToEspForTts(text: String) {
        speakAsCompanion(text)
    }

    private fun speakResponse(text: String) {
        speakAsCompanion(text)
    }

    // Game controls
    fun startGame(gameType: com.cupcake.game.Game.GameType, difficulty: com.cupcake.game.TicTacToeEngine.Difficulty = com.cupcake.game.TicTacToeEngine.Difficulty.NORMAL) {
        if (!energyManager.consumeEnergy(com.cupcake.ai.EnergyManager.ENERGY_PER_GAME)) return
        val state = gameManager.startGame(gameType, difficulty)
        _gameState.value = com.cupcake.game.GameManager.Result(true, "Game started", state)
    }

    fun makeGameMove(position: Int) {
        val result = gameManager.makeHumanMove(position)
        _gameState.value = result
    }

    fun endGame() {
        _gameState.value = null
    }

    // Character management
    fun getAvailableCharacters(): List<Character> = characterManager.getAllCharacters()

    fun clearChat() {
        sessionStore.clear(characterId)
    }

    // Energy/Ad
    fun watchAdForEnergy() {
        // Trigger ad loading in UI
        _adRequested.value = true
    }

    private val _adRequested = MutableStateFlow(false)
    val adRequested = _adRequested.asStateFlow()

    fun onAdWatched() {
        _adRequested.value = false
        viewModelScope.launch { energyManager.watchRewardedAd() }
    }

    fun upgradeToPro() {
        viewModelScope.launch { energyManager.purchasePro() }
    }

    /** Applies a config chosen in the chat config sheet and persists it for this conversation. */
    fun updateModelConfig(config: ModelConfig) {
        _modelConfig.value = config
        viewModelScope.launch {
            runCatching { modelRepository.updateConfig(conversationId, config) }
                .onFailure { Log.w("ChatViewModel", "Failed to persist model config", it) }
        }
        ensureModelLoaded()
    }

    /**
     * Sets (or clears, when [text] is blank) the conversation's custom system prompt and
     * persists it, so the next reply uses it instead of the character's default prompt.
     */
    fun updateSystemPrompt(text: String?) {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            _systemPrompt.value = null
            return
        }
        val existing = _systemPrompt.value
        val updated = existing?.copy(text = trimmed, version = existing.version + 1)
            ?: SystemPrompt(id = "sys_$conversationId", name = "Chat prompt", text = trimmed)
        _systemPrompt.value = updated
        viewModelScope.launch {
            runCatching { systemPromptRepository.savePrompt(updated) }
                .onFailure { Log.w("ChatViewModel", "Failed to persist system prompt", it) }
        }
    }

    // ESP32 controls
    fun sendFaceExpression(expression: String) {
        espServer.sendFaceExpression(expression)
    }

    fun sendMotorAction(action: String) {
        espServer.sendMotorAction(action)
    }

    fun refreshDevices() {
        _connectedDevices.value = espServer.getConnectedDevices()
    }

    override fun onCleared() {
        idleChatterJob?.cancel()
        espServer.setOnConnectionChangeListener(null)
        super.onCleared()
    }
}

/** Cute spontaneous lines the companion may send on its own while the chat is open. */
private val IDLE_CHATTER_LINES = listOf(
    "*hums softly and glances at you* [lovely] Still there? I like having you around~",
    "[excited] Ooh, I just thought of something fun! Want to hear it?",
    "*stretches* [normal] Taking a tiny break... don't wander off too far, okay?",
    "[lovely] Hey, you've been quiet. Everything okay?",
    "*tilts head* [thinking] What are you up to right now?",
    "[flirty] I was thinking about you again... oops, said that out loud!",
    "*brings you a little snack* [eating] You should eat something too, you know!",
    "[confident] I bet you're doing amazing today. Just saying~"
)