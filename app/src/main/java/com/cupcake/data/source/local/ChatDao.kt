package com.cupcake.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>)

    @Update
    suspend fun update(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(conversationId: String, limit: Int, offset: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND role = 'system' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getSystemMessage(conversationId: String): ChatMessage?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT MAX(timestamp) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getLastMessageTime(conversationId: String): Instant?
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<Conversation?>

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Instant)

    @Query("UPDATE conversations SET messageCount = messageCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementMessageCount(id: String, updatedAt: Instant)
}

@Dao
interface SystemPromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: SystemPrompt): Long

    @Update
    suspend fun update(prompt: SystemPrompt)

    @Query("DELETE FROM system_prompts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM system_prompts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SystemPrompt>>

    @Query("SELECT * FROM system_prompts WHERE id = :id")
    suspend fun getById(id: String): SystemPrompt?
}

@Dao
interface ApiProviderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ApiProvider)

    @Update
    suspend fun update(provider: ApiProvider)

    @Query("DELETE FROM api_providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM api_providers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ApiProvider>>

    @Query("SELECT * FROM api_providers WHERE isEnabled = 1")
    fun getEnabled(): Flow<List<ApiProvider>>

    @Query("SELECT * FROM api_providers WHERE id = :id")
    suspend fun getById(id: String): ApiProvider?
}

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): ModelConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ModelConfigWithConversation)

    @Query("DELETE FROM model_configs WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

data class ModelConfigWithConversation(
    val conversationId: String,
    val provider: ModelConfig.ModelProvider,
    val modelName: String,
    val customEndpoint: String,
    val apiKey: String,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
    val systemPromptId: String?,
    val useStreaming: Boolean,
    val timeoutSeconds: Int
) {
    fun toModelConfig(): ModelConfig = ModelConfig(
        provider = provider,
        modelName = modelName,
        customEndpoint = customEndpoint,
        apiKey = apiKey,
        temperature = temperature,
        topP = topP,
        topK = topK,
        maxTokens = maxTokens,
        systemPromptId = systemPromptId,
        useStreaming = useStreaming,
        timeoutSeconds = timeoutSeconds
    )

    companion object {
        fun from(conversationId: String, config: ModelConfig): ModelConfigWithConversation {
            return ModelConfigWithConversation(
                conversationId = conversationId,
                provider = config.provider,
                modelName = config.modelName,
                customEndpoint = config.customEndpoint,
                apiKey = config.apiKey,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                maxTokens = config.maxTokens,
                systemPromptId = config.systemPromptId,
                useStreaming = config.useStreaming,
                timeoutSeconds = config.timeoutSeconds
            )
        }
    }
}