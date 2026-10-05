package com.cupcake.domain

import app.cash.turbine.test
import com.cupcake.data.model.Conversation
import com.cupcake.domain.repository.ChatRepository
import com.cupcake.domain.usecase.CreateConversationUseCase
import com.cupcake.domain.usecase.GetConversationsUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UseCasesTest {

    private val chatRepository: ChatRepository = mockk()

    @Test
    fun `create conversation delegates to repository`() = runBlocking {
        val conversation = Conversation(title = "Hi")
        coEvery { chatRepository.createConversation("Hi") } returns conversation

        val result = CreateConversationUseCase(chatRepository)("Hi")

        assertThat(result.title).isEqualTo("Hi")
    }

    @Test
    fun `get conversations emits repository flow`() = runBlocking {
        val conversations = listOf(
            Conversation(title = "One"),
            Conversation(title = "Two")
        )
        every { chatRepository.getConversations() } returns flowOf(conversations)

        GetConversationsUseCase(chatRepository)().test {
            assertThat(awaitItem()).hasSize(2)
            awaitComplete()
        }
    }
}
