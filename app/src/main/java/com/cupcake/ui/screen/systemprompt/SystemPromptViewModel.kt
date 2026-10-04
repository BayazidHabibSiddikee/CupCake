package com.cupcake.ui.screen.systemprompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.SystemPrompt
import com.cupcake.domain.usecase.AddImageToPromptUseCase
import com.cupcake.domain.usecase.DeleteSystemPromptUseCase
import com.cupcake.domain.usecase.GetSystemPromptUseCase
import com.cupcake.domain.usecase.GetSystemPromptsUseCase
import com.cupcake.domain.usecase.SaveSystemPromptUseCase
import com.cupcake.domain.usecase.UpdateSystemPromptUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class SystemPromptViewModel @Inject constructor(
    private val getPromptUseCase: GetSystemPromptUseCase,
    private val getPromptsUseCase: GetSystemPromptsUseCase,
    private val savePromptUseCase: SaveSystemPromptUseCase,
    private val updatePromptUseCase: UpdateSystemPromptUseCase,
    private val deletePromptUseCase: DeleteSystemPromptUseCase,
    private val addImageUseCase: AddImageToPromptUseCase,
    private val promptId: String?
) : ViewModel() {

    val allPrompts = getPromptsUseCase().asStateFlow(initialValue = emptyList())

    val prompt = if (promptId != null) {
        getPromptUseCase(promptId!!).asStateFlow(initialValue = null)
    } else {
        MutableStateFlow<SystemPrompt?>(null).asStateFlow()
    }

    val showDeleteDialog = MutableStateFlow(false)
    val showImagePicker = MutableStateFlow(false)

    fun save(name: String, text: String) {
        viewModelScope.launch {
            val newPrompt = SystemPrompt(
                name = name,
                text = text
            )
            val saved = savePromptUseCase(newPrompt)
            prompt.value = saved
        }
    }

    fun update(name: String, text: String) {
        prompt.value?.let { current ->
            viewModelScope.launch {
                val updated = current.copy(name = name, text = text)
                updatePromptUseCase(updated)
                prompt.value = updated
            }
        }
    }

    fun deletePrompt() {
        promptId?.let { id ->
            viewModelScope.launch {
                deletePromptUseCase(id)
            }
        }
    }

    fun addImages(uris: List<String>) {
        promptId?.let { id ->
            viewModelScope.launch {
                uris.forEach { uri ->
                    val image = PromptImage(
                        uri = uri,
                        mimeType = "image/*", // TODO: detect actual mime type
                        description = "Image ${System.currentTimeMillis()}"
                    )
                    val updated = addImageUseCase(id, image)
                    prompt.value = updated
                }
            }
        }
    }

    fun removeImage(imageId: String) {
        prompt.value?.let { current ->
            val updated = current.copy(
                images = current.images.filter { it.id != imageId },
                version = current.version + 1
            )
            viewModelScope.launch {
                updatePromptUseCase(updated)
                prompt.value = updated
            }
        }
    }
}