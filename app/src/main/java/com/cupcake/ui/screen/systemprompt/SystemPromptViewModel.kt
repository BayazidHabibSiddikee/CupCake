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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val addImageUseCase: AddImageToPromptUseCase
) : ViewModel() {

    val allPrompts: StateFlow<List<SystemPrompt>> = getPromptsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _prompt = MutableStateFlow<SystemPrompt?>(null)
    val prompt: StateFlow<SystemPrompt?> = _prompt.asStateFlow()

    private var promptId: String? = null

    val showDeleteDialog = MutableStateFlow(false)
    val showImagePicker = MutableStateFlow(false)

    fun loadPrompt(id: String?) {
        promptId = id
        if (id != null) {
            viewModelScope.launch {
                _prompt.value = getPromptUseCase(id)
            }
        } else {
            _prompt.value = null
        }
    }

    fun save(name: String, text: String) {
        viewModelScope.launch {
            val newPrompt = SystemPrompt(
                name = name,
                text = text
            )
            val saved = savePromptUseCase(newPrompt)
            _prompt.value = saved
        }
    }

    fun update(name: String, text: String) {
        _prompt.value?.let { current ->
            viewModelScope.launch {
                val updated = current.copy(name = name, text = text)
                updatePromptUseCase(updated)
                _prompt.value = updated
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
                    _prompt.value = updated
                }
            }
        }
    }

    fun removeImage(imageId: String) {
        _prompt.value?.let { current ->
            val updated = current.copy(
                images = current.images.filter { it.id != imageId },
                version = current.version + 1
            )
            viewModelScope.launch {
                updatePromptUseCase(updated)
                _prompt.value = updated
            }
        }
    }
}