package com.cupcake.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.ModelConfigWithConversation
import com.cupcake.data.model.SystemPrompt
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
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): ModelConfigWithConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ModelConfigWithConversation)

    @Query("DELETE FROM model_configs WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}