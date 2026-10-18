package com.cupcake.chat

import com.cupcake.data.model.ChatMessage
import com.cupcake.data.source.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent chat session per character backed by Room database.
 *
 * Each character owns exactly one persistent conversation history ("chat_$characterId").
 * Messages are loaded from SQLite on startup and immediately saved to Room on every addMessage.
 */
@Singleton
class ChatSessionStore @Inject constructor(
    private val database: AppDatabase?
) {
    // Secondary constructor for unit tests
    constructor() : this(null)

    private val sessions = ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        instance = this
    }

    companion object {
        @Volatile
        private var instance: ChatSessionStore? = null

        fun get(): ChatSessionStore? = instance
    }

    fun conversationIdFor(characterId: String): String = "chat_$characterId"

    fun messages(characterId: String): StateFlow<List<ChatMessage>> =
        session(characterId).asStateFlow()

    fun addMessage(characterId: String, message: ChatMessage) {
        val flow = session(characterId)
        // id==0 keeps the message id/0 for the UI (Room autoGenerate assigns the real
        // row id on insert). Fabricating a parallel counter here collided with Room's
        // own ids (both minted id=N), so insert(REPLACE) silently deleted whole rows.
        val stamped = message.copy(conversationId = conversationIdFor(characterId))
        flow.value = flow.value + stamped

        database?.let { db ->
            scope.launch {
                try {
                    db.chatMessageDao().insert(stamped)
                } catch (e: Exception) {
                    android.util.Log.e("ChatSessionStore", "Failed to persist message", e)
                }
            }
        }
    }

    fun clear(characterId: String) {
        session(characterId).value = emptyList()
        database?.let { db ->
            scope.launch {
                try {
                    db.chatMessageDao().deleteByConversation(conversationIdFor(characterId))
                } catch (e: Exception) {
                    android.util.Log.e("ChatSessionStore", "Failed to delete messages", e)
                }
            }
        }
    }

    fun clearAll() {
        sessions.values.forEach { it.value = emptyList() }
        database?.let { db ->
            scope.launch {
                try {
                    sessions.keys.forEach { charId ->
                        db.chatMessageDao().deleteByConversation(conversationIdFor(charId))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatSessionStore", "Failed to clear all messages", e)
                }
            }
        }
    }

    fun sessionCount(): Int = sessions.size

    private fun session(characterId: String): MutableStateFlow<List<ChatMessage>> =
        sessions.getOrPut(characterId) {
            val flow = MutableStateFlow<List<ChatMessage>>(emptyList())
            database?.let { db ->
                scope.launch {
                    try {
                        val convId = conversationIdFor(characterId)
                        val stored = db.chatMessageDao().getMessagesPaged(convId, 300, 0)
                        if (stored.isNotEmpty()) {
                            // Merge, never replace: a message sent while the load was
                            // in flight must not be orphaned (it was dropped this way).
                            val inFlight = flow.value.filterNot { migrated ->
                                stored.any { s -> s.id == migrated.id }
                            }
                            flow.value = (stored + inFlight)
                                .sortedBy { it.timestamp.toEpochMilli() }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatSessionStore", "Failed to load chat history for $characterId", e)
                    }
                }
            }
            flow
        }
}
