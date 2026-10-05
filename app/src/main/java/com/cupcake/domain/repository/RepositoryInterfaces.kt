package com.cupcake.domain.repository

import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.StreamChunk
import com.cupcake.data.model.SystemPrompt
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun createConversation(title: String): Conversation
    fun getConversations(): Flow<List<Conversation>>
    suspend fun getConversation(id: String): Conversation?
    fun observeConversation(id: String): Flow<Conversation?>
    suspend fun updateConversationTitle(id: String, title: String)
    suspend fun deleteConversation(id: String)

    suspend fun sendMessage(
        conversationId: String,
        message: ChatMessage,
        config: ModelConfig
    ): Flow<StreamChunk>

    suspend fun saveAssistantMessage(
        conversationId: String,
        content: String,
        modelUsed: String,
        tokenCount: Int,
        latencyMs: Long
    )

    fun getMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun getMessagesPaged(conversationId: String, limit: Int, offset: Int): List<ChatMessage>
}

interface SystemPromptRepository {
    fun getAllPrompts(): Flow<List<SystemPrompt>>
    suspend fun getPrompt(id: String): SystemPrompt?
    suspend fun savePrompt(prompt: SystemPrompt): SystemPrompt
    suspend fun updatePrompt(prompt: SystemPrompt)
    suspend fun deletePrompt(id: String)
    suspend fun addImageToPrompt(promptId: String, image: PromptImage): SystemPrompt
}

interface ModelRepository {
    fun getProviders(): Flow<List<ApiProvider>>
    fun getEnabledProviders(): Flow<List<ApiProvider>>
    suspend fun addProvider(provider: ApiProvider): ApiProvider
    suspend fun updateProvider(provider: ApiProvider)
    suspend fun deleteProvider(id: String)
    suspend fun testConnection(provider: ApiProvider): Boolean
    suspend fun getModels(provider: ApiProvider): List<String>
    fun getConfig(conversationId: String): Flow<ModelConfig>
    suspend fun updateConfig(conversationId: String, config: ModelConfig)
}