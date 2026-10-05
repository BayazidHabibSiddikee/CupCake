package com.cupcake.ui.screen.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.LlamaEngine
import com.cupcake.data.model.Character
import com.cupcake.game.GameManager
import com.cupcake.network.EspWebSocketServer
import com.cupcake.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val characterManager: CharacterManager,
    private val gameManager: GameManager,
    private val espServer: EspWebSocketServer,
    private val ttsManager: TtsManager,
    private val energyManager: EnergyManager
) : ViewModel() {

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

    private val _gameState = MutableStateFlow<com.cupcake.game.GameManager.Result?>(null)
    val gameState = _gameState.asStateFlow()

    private var currentConversationId = "default"
    private var systemPromptCache = ""

    init {
        setupObservers()
        setupGameReactions()
        setupEspCallbacks()
        loadCurrentCharacter()
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
            addMessage(ChatMessage(
                role = ChatMessage.MessageRole.ASSISTANT,
                content = "⚡ Energy depleted! Watch an ad or upgrade to Pro to continue.",
                isSystem = true
            ))
            return
        }

        val userMessage = ChatMessage(
            role = ChatMessage.MessageRole.USER,
            content = text
        )
        addMessage(userMessage)

        _isGenerating.value = true
        _currentResponse.value = ""

        viewModelScope.launch {
            var fullResponse = ""
            val character = characterManager.getCurrentCharacter()
            val prompt = buildPrompt(text, character)

            llamaEngine.generateStream(prompt).collect { token ->
                fullResponse += token
                _currentResponse.value = fullResponse
            }.also {
                _isGenerating.value = false
                _currentResponse.value = ""
                
                if (fullResponse.isNotBlank()) {
                    // Consume energy
                    energyManager.consumeEnergy()
                    
                    // Add assistant message
                    val assistantMessage = ChatMessage(
                        role = ChatMessage.MessageRole.ASSISTANT,
                        content = fullResponse.trim()
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

    data class ChatMessage(
        val role: MessageRole,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isSystem: Boolean = false
    ) {
        enum class MessageRole { USER, ASSISTANT, SYSTEM }
    }
}