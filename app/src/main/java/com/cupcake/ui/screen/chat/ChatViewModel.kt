package com.cupcake.ui.screen.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.LlamaEngine
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
    private val llamaEngine: LlamaEngine,
    private val characterManager: CharacterManager,
    private val gameManager: GameManager,
    private val espServer: EspWebSocketServer,
    private val ttsManager: TtsManager,
    private val energyManager: EnergyManager
) : ViewModel() {

    sealed interface ModelLoadState {
        data object Idle : ModelLoadState
        data object Loading : ModelLoadState
        data object Ready : ModelLoadState
        data class Error(val message: String) : ModelLoadState
    }

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val modelLoadState = _modelLoadState.asStateFlow()

    // UI State
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

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

    private var currentConversationId = "default"
    private var systemPromptCache = ""

    init {
        setupObservers()
        setupGameReactions()
        setupEspCallbacks()
        loadCurrentCharacter()
        ensureModelLoaded()
    }

    /**
     * Copies the bundled GGUF from assets on first run (large file, runs
     * on IO) and initializes the native engine. Safe to call repeatedly.
     */
    fun ensureModelLoaded() {
        if (llamaEngine.isReady() || _modelLoadState.value is ModelLoadState.Loading) return
        _modelLoadState.value = ModelLoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = llamaEngine.loadModel(appContext)
            _modelLoadState.value = result.fold(
                onSuccess = { ModelLoadState.Ready },
                onFailure = { ModelLoadState.Error(it.message ?: "Model load failed") }
            )
        }
    }

    private fun setupObservers() {
        viewModelScope.launch {
            energyManager.energy.collect { energy ->
                _energy.value = energy
            }
            energyManager.isPro.collect { pro ->
                _isPro.value = pro
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
        val character = characterManager.getCurrentCharacter()
        _currentCharacter.value = character
        if (character != null) {
            systemPromptCache = characterManager.getSystemPromptForCharacter(character.id)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        // Check energy
        if (!energyManager.hasEnergy()) {
            addMessage(
                ChatMessage.system(
                    "⚡ Energy depleted! Watch an ad or upgrade to Pro to continue.",
                    currentConversationId
                )
            )
            return
        }

        val userMessage = ChatMessage.user(text, currentConversationId)
        addMessage(userMessage)

        if (!llamaEngine.isReady()) {
            ensureModelLoaded()
            addMessage(
                ChatMessage.system(
                    "⏳ Model is still loading, please wait a moment and try again.",
                    currentConversationId
                )
            )
            return
        }

        _isGenerating.value = true
        _currentResponse.value = ""

        viewModelScope.launch {
            var fullResponse = ""
            val character = characterManager.getCurrentCharacter()
            val prompt = buildPrompt(text, character)

            try {
                llamaEngine.generateStream(prompt).consumeEach { token ->
                    fullResponse += token
                    _currentResponse.value = fullResponse
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Generation failed", e)
                fullResponse = ""
                addMessage(
                    ChatMessage.system(
                        "❌ Generation failed: ${e.message}",
                        currentConversationId
                    )
                )
            }.also {
                _isGenerating.value = false
                _currentResponse.value = ""
                
                if (fullResponse.isNotBlank()) {
                    // Consume energy
                    energyManager.consumeEnergy()
                    
                    // Add assistant message
                    val assistantMessage = ChatMessage.assistant(
                        fullResponse.trim(),
                        currentConversationId,
                        modelUsed = _modelConfig.value.modelName
                    )
                    addMessage(assistantMessage)
                    
                    // Send to ESP32 for TTS
                    sendToEspForTts(fullResponse.trim())
                }
            }
        }
    }

    private fun buildPrompt(userInput: String, character: Character?): String {
        val systemPrompt = characterManager.getSystemPromptForCharacter(
            character?.id ?: "cute_companion"
        )
        
        // Get recent messages for context
        val recentMessages = _messages.value.takeLast(6).joinToString("\n") { msg ->
            "${msg.role.name}: ${msg.content}"
        }

        return """$systemPrompt

Recent conversation:
$recentMessages

User: $userInput
Assistant:""".trimIndent()
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
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
        if (!energyManager.hasEnergy()) return
        energyManager.consumeEnergy(com.cupcake.ai.EnergyManager.ENERGY_PER_GAME)
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
    fun selectCharacter(characterId: String) {
        if (characterManager.selectCharacter(characterId)) {
            loadCurrentCharacter()
        }
    }

    fun getAvailableCharacters(): List<Character> = characterManager.getAllCharacters()

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

    fun onAttachImage() {
        Log.d("ChatViewModel", "Attach image clicked")
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
        espServer.stop()
        super.onCleared()
    }
}