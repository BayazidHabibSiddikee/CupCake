package com.cupcake.chat

import com.cupcake.data.model.ChatMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatSessionStoreTest {

    private fun userMessage(text: String) = ChatMessage.user(text, "ignored")

    @Test
    fun `new character starts with empty session`() {
        val store = ChatSessionStore()
        assertThat(store.messages("cute_companion").value).isEmpty()
    }

    @Test
    fun `messages are stamped with the character session id`() {
        val store = ChatSessionStore()
        store.addMessage("grumpy_bot", userMessage("hi"))

        val messages = store.messages("grumpy_bot").value
        assertThat(messages).hasSize(1)
        assertThat(messages[0].conversationId).isEqualTo("chat_grumpy_bot")
        assertThat(messages[0].content).isEqualTo("hi")
    }

    @Test
    fun `sessions are isolated per character`() {
        val store = ChatSessionStore()
        store.addMessage("cute_companion", userMessage("hello"))
        store.addMessage("cute_companion", userMessage("are you there?"))

        assertThat(store.messages("cute_companion").value).hasSize(2)
        assertThat(store.messages("rage_gamer").value).isEmpty()
    }

    @Test
    fun `message order is preserved`() {
        val store = ChatSessionStore()
        store.addMessage("tutor_bot", userMessage("first"))
        store.addMessage("tutor_bot", userMessage("second"))
        store.addMessage("tutor_bot", userMessage("third"))

        assertThat(store.messages("tutor_bot").value.map { it.content })
            .containsExactly("first", "second", "third")
            .inOrder()
    }

    @Test
    fun `clear removes one session only`() {
        val store = ChatSessionStore()
        store.addMessage("a", userMessage("x"))
        store.addMessage("b", userMessage("y"))

        store.clear("a")

        assertThat(store.messages("a").value).isEmpty()
        assertThat(store.messages("b").value).hasSize(1)
    }

    @Test
    fun `clearAll empties every session`() {
        val store = ChatSessionStore()
        store.addMessage("a", userMessage("x"))
        store.addMessage("b", userMessage("y"))

        store.clearAll()

        assertThat(store.messages("a").value).isEmpty()
        assertThat(store.messages("b").value).isEmpty()
    }

    @Test
    fun `conversation ids are namespaced per character`() {
        val store = ChatSessionStore()
        assertThat(store.conversationIdFor("cute_companion"))
            .isEqualTo("chat_cute_companion")
        assertThat(store.conversationIdFor("cute_companion"))
            .isNotEqualTo(store.conversationIdFor("grumpy_bot"))
    }
}
