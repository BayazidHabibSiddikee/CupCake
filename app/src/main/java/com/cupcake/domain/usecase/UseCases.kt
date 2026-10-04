package com.cupcake.domain.usecase

import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.StreamChunk
import com.cupcake.data.model.SystemPrompt
import com.cupcake.domain.repository.ChatRepository
import com.cupcake.domain.repository.ModelRepository
import com.cupcake.domain.repository.SystemPromptRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(title: String = "New Chat"): Conversation {
        return chatRepository.createConversation(title)
    }
}

class GetConversationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Conversation>> = chatRepository.getConversations()
}

class GetConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(id: String): Conversation? = chatRepository.getConversation(id)
}

class ObserveConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(id: String): Flow<Conversation?> = chatRepository.observeConversation(id)
}

class UpdateConversationTitleUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(id: String, title: String) = chatRepository.updateConversationTitle(id, title)
}

class DeleteConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(id: String) = chatRepository.deleteConversation(id)
}

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository
) {
    operator fun invoke(
        conversationId: String,
        message: ChatMessage,
        config: ModelConfig
    ): Flow<StreamChunk> {
        return chatRepository.sendMessage(conversationId, message, config)
    }
}

class SaveAssistantMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(
        conversationId: String,
        content: String,
        modelUsed: String,
        tokenCount: Int,
        latencyMs: Long
    ) = chatRepository.saveAssistantMessage(conversationId, content, modelUsed, tokenCount, latencyMs)
}

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<List<ChatMessage>> =
        chatRepository.getMessages(conversationId)
}

class GetSystemPromptsUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(): Flow<List<SystemPrompt>> = promptRepository.getAllPrompts()
}

class GetSystemPromptUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(id: String): SystemPrompt? = promptRepository.getPrompt(id)
}

class SaveSystemPromptUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(prompt: SystemPrompt): SystemPrompt = promptRepository.savePrompt(prompt)
}

class UpdateSystemPromptUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(prompt: SystemPrompt) = promptRepository.updatePrompt(prompt)
}

class DeleteSystemPromptUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(id: String) = promptRepository.deletePrompt(id)
}

class AddImageToPromptUseCase @Inject constructor(
    private val promptRepository: SystemPromptRepository
) {
    operator fun invoke(promptId: String, image: SystemPromptRepository.PromptImage): SystemPrompt =
        promptRepository.addImageToPrompt(promptId, image)
}

class GetApiProvidersUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(): Flow<List<ApiProvider>> = modelRepository.getProviders()
}

class GetEnabledProvidersUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(): Flow<List<ApiProvider>> = modelRepository.getEnabledProviders()
}

class AddApiProviderUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(provider: ApiProvider): ApiProvider = modelRepository.addProvider(provider)
}

class UpdateApiProviderUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(provider: ApiProvider) = modelRepository.updateProvider(provider)
}

class DeleteApiProviderUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(id: String) = modelRepository.deleteProvider(id)
}

class TestProviderConnectionUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(provider: ApiProvider): Boolean = modelRepository.testConnection(provider)
}

class GetProviderModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(provider: ApiProvider): List<String> = modelRepository.getModels(provider)
}

class GetModelConfigUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(conversationId: String): Flow<ModelConfig> = modelRepository.getConfig(conversationId)
}

class UpdateModelConfigUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(conversationId: String, config: ModelConfig) = modelRepository.updateConfig(conversationId, config)
}