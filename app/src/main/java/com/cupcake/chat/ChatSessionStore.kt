package com.cupcake.chat

import com.cupcake.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single in-memory session per character.
 *
 * There is exactly one message list per character id, shared across
 * navigation so leaving and reopening a character's chat resumes the
 * same session. Nothing is duplicated: no "New Chat" copies.
 */
@Singleton
class ChatSessionStore @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()

    fun conversationIdFor(characterId: String): String = "chat_$characterId"

    fun messages(characterId: String): StateFlow<List<ChatMessage>> =
        session(characterId).asStateFlow()

    fun addMessage(characterId: String, message: ChatMessage) {
        val flow = session(characterId)
        val stamped = message.copy(conversationId = conversationIdFor(characterId))
        flow.value = flow.value + stamped
    }

    fun clear(characterId: String) {
        session(characterId).value = emptyList()
    }

    fun clearAll() {
        sessions.values.forEach { it.value = emptyList() }
    }

    fun sessionCount(): Int = sessions.size

    private fun session(characterId: String): MutableStateFlow<List<ChatMessage>> =
        sessions.getOrPut(characterId) { MutableStateFlow(emptyList()) }
}
