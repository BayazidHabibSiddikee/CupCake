package com.cupcake.ui.screen.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.OllamaEngine
import com.cupcake.data.model.Character
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.SystemPrompt
import com.cupcake.game.GameManager
import com.cupcake.network.EspWebSocketServer
import com.cupcake.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.cupcake.chat.ChatSessionStore
import com.cupcake.domain.usecase.GetApiProvidersUseCase
import com.cupcake.data.model.ApiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.consumeEach
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
    private val energyManager: EnergyManager,
    private val getApiProvidersUseCase: GetApiProvidersUseCase
) : ViewModel() {

    val characterId: String =
        savedStateHandle.get<String>("characterId") ?: "cute_companion"

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

    private val _systemPrompt = MutableStateFlow<SystemPrompt?>(null)
    val systemPrompt = _systemPrompt.asStateFlow()

    private val _gameState = MutableStateFlow<com.cupcake.game.GameManager.Result?>(null)
    val gameState = _gameState.asStateFlow()

    private var systemPromptCache = ""

    init {
        setupObservers()
        setupGameReactions()
        setupEspCallbacks()
        loadCurrentCharacter()
        ensureModelLoaded()
    }

    /**
     * Checks Ollama server connection. Safe to call repeatedly.
     */
    fun ensureModelLoaded() {
        _modelLoadState.value = ModelLoadState.Loading("Connecting to Ollama...")
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = _modelConfig.value.effectiveEndpoint()
            val result = ollamaEngine.checkConnection(baseUrl)
            result.consumeEach { connectionResult ->
                _modelLoadState.value = connectionResult.fold(
                    onSuccess = { connected ->
                        if (connected) ModelLoadState.Ready
                        else ModelLoadState.Error("Cannot connect to Ollama at $baseUrl")
                    },
                    onFailure = { ModelLoadState.Error(it.message ?: "Connection failed") }
                )
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
            getApiProvidersUseCase().collect { providerList ->
                val active = providerList.firstOrNull { it.isEnabled }
                if (active != null) {
                    val newConfig = _modelConfig.value.copy(
                        provider = active.providerType,
                        customEndpoint = active.baseUrl,
                        apiKey = active.apiKey,
                        modelName = active.models.firstOrNull() ?: _modelConfig.value.modelName
                    )
                    _modelConfig.value = newConfig
                } else {
                    _modelConfig.value = ModelConfig.default()
                }
                ensureModelLoaded()
            }
        }
    }

    private fun setupGameReactions() {
        gameManager.onGameReaction = { reactionText, humanWon, isDraw ->
            viewModelScope.launch {
                // Send to ESP32 for face animation
                val expression = if (humanWon) EspWebSocketServer.FaceExpression.RAGE
                else if (isDraw) EspWebSocketServer.FaceExpression.THINKING
                else EspWebSocketServer.FaceExpression.HAPPY
                
                espServer.sendFaceExpression(expression, 3000)
                
                // Motor action
                val motorAction = if (humanWon) EspWebSocketServer.MotorAction.TANTUM
                else EspWebSocketServer.MotorAction.DANCE
                espServer.sendMotorAction(motorAction)
                
                // TTS
                speakResponse(reactionText)
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
        }
    }

    private fun buildOllamaMessages(): List<OllamaEngine.OllamaMessage> {
        val messageList = mutableListOf<OllamaEngine.OllamaMessage>()
        
        // Add system prompt if available
        val systemPrompt = _systemPrompt.value?.fullPrompt() ?: systemPromptCache
        if (systemPrompt.isNotBlank()) {
            messageList.add(OllamaEngine.OllamaMessage(role = "system", content = systemPrompt))
        }
        
        // Add conversation messages
        val conversationMessages = messages.value
        for (msg in conversationMessages) {
            val role = when (msg.role) {
                ChatMessage.MessageRole.USER -> "user"
                ChatMessage.MessageRole.ASSISTANT -> "assistant"
                ChatMessage.MessageRole.SYSTEM -> "system"
                ChatMessage.MessageRole.TOOL -> "tool"
            }
            messageList.add(OllamaEngine.OllamaMessage(role = role, content = msg.content))
        }
        
        return messageList
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        if (!ollamaEngine.isReady()) {
            ensureModelLoaded()
            addMessage(
                ChatMessage.system(
                    "⏳ Model is still loading, please wait a moment and try again.",
                    conversationId
                )
            )
            return
        }

        // Check energy
        energyManager.consumeEnergy() // Consume it but don't block if they don't have it (make it simple!)
        /*
        if (!energyManager.consumeEnergy()) {
            addMessage(
                ChatMessage.system(
                    "⚡ Energy depleted! Watch an ad or upgrade to Pro to continue.",
                    conversationId
                )
            )
            return
        }
        */

        val userMessage = ChatMessage.user(text, conversationId)
        addMessage(userMessage)

        _isGenerating.value = true
        _currentResponse.value = ""

viewModelScope.launch {
            var fullResponse = ""
            val config = _modelConfig.value
            val endpoint = config.effectiveEndpoint()

            try {
                val personality = currentCharacter.value?.personality ?: ""
                
                // Build messages for Ollama format
                val messages = buildOllamaMessages()
                
                ollamaEngine.generateStream(
                    baseUrl = endpoint,
                    config = config,
                    messages = messages,
                    callback = object : OllamaEngine.GenerateCallback {
                        override fun onToken(token: String) {
                            fullResponse += token
                            _currentResponse.value = fullResponse
                        }

                        override fun onComplete() {
                            _currentResponse.value = com.cupcake.ai.BanglishTranslator.translate(fullResponse, personality)
                        }

                        override fun onError(error: String) {
                            Log.e("ChatViewModel", "Generation failed: $error")
                            addMessage(
                                ChatMessage.system(
                                    "❌ Ollama request failed: $error",
                                    conversationId
                                )
                            )
                        }
                    }
                )
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

                    // Send to ESP32 for TTS
                    sendToEspForTts(fullResponse.trim())
                }
            }
        }
    }

    private fun buildPrompt(): String {
        // Each session is locked to one character, so use this session's
        // character - not the global "current" selection.
        val systemPrompt = characterManager.getSystemPromptForCharacter(
            _currentCharacter.value?.id ?: characterId
        )
        
        val builder = StringBuilder()
        builder.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        
        // Get recent messages for context
        val recentMessages = messages.value.takeLast(6)
        for (msg in recentMessages) {
            builder.append("<|im_start|>").append(msg.role.value).append("\n")
                .append(msg.content).append("<|im_end|>\n")
        }

        builder.append("<|im_start|>assistant\n")
        return builder.toString()
    }

    private fun addMessage(message: ChatMessage) {
        sessionStore.addMessage(characterId, message)
    }

    private fun sendToEspForTts(text: String) {
        espServer.sendCommand("speak", mapOf("text" to text))
        
        // Also stream audio via WebSocket binary frames
        viewModelScope.launch(Dispatchers.IO) {
            ttsManager.speakStreaming(text) { audioChunk ->
                espServer.sendAudio(audioChunk)
            }
        }
    }

    private fun speakResponse(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ttsManager.speakStreaming(text) { audioChunk ->
                espServer.sendAudio(audioChunk)
            }
        }
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
        energyManager.rewardAdWatched()
    }

    fun upgradeToPro() {
        energyManager.purchasePro()
    }

    // Header actions (TODO: wire to config sheets)
    fun onModelConfigClick() {
        Log.d("ChatViewModel", "Model config clicked")
    }

    fun onSystemPromptClick() {
        Log.d("ChatViewModel", "System prompt clicked")
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
        espServer.setOnConnectionChangeListener(null)
        super.onCleared()
    }
}