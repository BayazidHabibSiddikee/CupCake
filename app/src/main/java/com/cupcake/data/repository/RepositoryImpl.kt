package com.cupcake.data.repository

import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.GenerationRequest
import com.cupcake.data.model.GenerationResponse
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.StreamChunk
import com.cupcake.data.model.SystemPrompt
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.data.source.local.ChatDao
import com.cupcake.data.source.remote.ApiProviderDao
import com.cupcake.data.source.remote.ApiServices
import com.cupcake.domain.repository.ChatRepository
import com.cupcake.domain.repository.ModelRepository
import com.cupcake.domain.repository.SystemPromptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val apiClient: ApiClient
) : ChatRepository {

    override suspend fun createConversation(title: String = "New Chat"): Conversation {
        val conversation = Conversation(title = title)
        database.conversationDao().insert(conversation)
        return conversation
    }

    override fun getConversations(): Flow<List<Conversation>> =
        database.conversationDao().getAll()

    override suspend fun getConversation(id: String): Conversation? =
        database.conversationDao().getById(id)

    override fun observeConversation(id: String): Flow<Conversation?> =
        database.conversationDao().observeById(id)

    override suspend fun updateConversationTitle(id: String, title: String) {
        database.conversationDao().updateTitle(id, title, java.time.Instant.now())
    }

    override suspend fun deleteConversation(id: String) {
        database.chatMessageDao().deleteByConversation(id)
        database.conversationDao().delete(id)
        database.modelConfigDao().deleteByConversation(id)
    }

    override suspend fun sendMessage(
        conversationId: String,
        message: ChatMessage,
        config: ModelConfig
    ): Flow<StreamChunk> {
        // Save user message
        database.chatMessageDao().insert(message)
        database.conversationDao().incrementMessageCount(conversationId, java.time.Instant.now())

        // Get system prompt if configured
        val systemPrompt = config.systemPromptId?.let { promptId ->
            database.systemPromptDao().getById(promptId)
        }

        // Build full message list
        val messages = buildMessageList(conversationId, message, systemPrompt)

        return if (config.isLocal()) {
            // Use local Qwen via JNI
            apiClient.generateLocal(messages, config)
        } else {
            // Use custom API
            apiClient.generateRemote(messages, config)
        }
    }

    override suspend fun saveAssistantMessage(
        conversationId: String,
        content: String,
        modelUsed: String,
        tokenCount: Int,
        latencyMs: Long
    ) {
        val message = ChatMessage.assistant(content, conversationId, modelUsed).copy(
            tokenCount = tokenCount,
            latencyMs = latencyMs
        )
        database.chatMessageDao().insert(message)
    }

    override fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        database.chatMessageDao().getMessages(conversationId)

    override suspend fun getMessagesPaged(
        conversationId: String,
        limit: Int,
        offset: Int
    ): List<ChatMessage> =
        database.chatMessageDao().getMessagesPaged(conversationId, limit, offset)

    private fun buildMessageList(
        conversationId: String,
        newMessage: ChatMessage,
        systemPrompt: SystemPrompt?
    ): List<ChatMessage> {
        val existingMessages = database.chatMessageDao().getMessages(conversationId)
            .first() // This is not ideal for suspend, but for illustration
            .toMutableList()

        // Add system prompt if exists
        systemPrompt?.let { prompt ->
            existingMessages.add(0, ChatMessage.system(prompt.fullPrompt(), conversationId))
        }

        // Add new user message
        existingMessages.add(newMessage)

        return existingMessages
    }
}

class SystemPromptRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : SystemPromptRepository {

    override fun getAllPrompts(): Flow<List<SystemPrompt>> =
        database.systemPromptDao().getAll()

    override suspend fun getPrompt(id: String): SystemPrompt? =
        database.systemPromptDao().getById(id)

    override suspend fun savePrompt(prompt: SystemPrompt): SystemPrompt {
        val id = database.systemPromptDao().insert(prompt)
        return prompt.copy(id = id.toString())
    }

    override suspend fun updatePrompt(prompt: SystemPrompt) {
        database.systemPromptDao().update(prompt)
    }

    override suspend fun deletePrompt(id: String) {
        database.systemPromptDao().delete(id)
    }

    override suspend fun addImageToPrompt(promptId: String, image: PromptImage): SystemPrompt {
        val prompt = database.systemPromptDao().getById(promptId)
            ?: throw IllegalArgumentException("Prompt not found: $promptId")
        val updated = prompt.withImage(image)
        database.systemPromptDao().update(updated)
        return updated
    }
}

class ModelRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val apiClient: ApiClient
) : ModelRepository {

    override fun getProviders(): Flow<List<ApiProvider>> =
        database.apiProviderDao().getAll()

    override fun getEnabledProviders(): Flow<List<ApiProvider>> =
        database.apiProviderDao().getEnabled()

    override suspend fun addProvider(provider: ApiProvider): ApiProvider {
        database.apiProviderDao().insert(provider)
        return provider
    }

    override suspend fun updateProvider(provider: ApiProvider) {
        database.apiProviderDao().update(provider)
    }

    override suspend fun deleteProvider(id: String) {
        database.apiProviderDao().delete(id)
    }

    override suspend fun testConnection(provider: ApiProvider): Boolean {
        return apiClient.testConnection(provider)
    }

    override suspend fun getModels(provider: ApiProvider): List<String> {
        return apiClient.fetchModels(provider)
    }

    override fun getConfig(conversationId: String): Flow<ModelConfig> =
        database.modelConfigDao().getByConversation(conversationId)
            .map { it?.toModelConfig() ?: ModelConfig.default() }

    override suspend fun updateConfig(conversationId: String, config: ModelConfig) {
        val entity = ChatDao.ModelConfigWithConversation.from(conversationId, config)
        database.modelConfigDao().upsert(entity)
    }
}