package com.cupcake.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ModelConfig
import com.cupcake.domain.usecase.AddApiProviderUseCase
import com.cupcake.domain.usecase.DeleteApiProviderUseCase
import com.cupcake.domain.usecase.GetApiProvidersUseCase
import com.cupcake.domain.usecase.GetProviderModelsUseCase
import com.cupcake.domain.usecase.TestProviderConnectionUseCase
import com.cupcake.domain.usecase.UpdateApiProviderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.cupcake.ai.ModelDownloadManager
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getProvidersUseCase: GetApiProvidersUseCase,
    private val addProviderUseCase: AddApiProviderUseCase,
    private val updateProviderUseCase: UpdateApiProviderUseCase,
    private val deleteProviderUseCase: DeleteApiProviderUseCase,
    private val testConnectionUseCase: TestProviderConnectionUseCase,
    private val getProviderModelsUseCase: GetProviderModelsUseCase,
    val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    val providers: StateFlow<List<ApiProvider>> = getProvidersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editingProviderId = MutableStateFlow<String?>(null)
    val showAddProviderDialog = MutableStateFlow(false)
    val selectedProviderType = MutableStateFlow(ModelConfig.ModelProvider.CUSTOM_OLLAMA)
    val newProviderName = MutableStateFlow("")
    val newProviderUrl = MutableStateFlow("")
    val newProviderApiKey = MutableStateFlow("")
    val newProviderModel = MutableStateFlow("")

    val statusMessage = MutableStateFlow<String?>(null)

    var onProviderSelected: ((ApiProvider) -> Unit)? = null
    var onLocalModelSelected: (() -> Unit)? = null

    fun selectProvider(provider: ApiProvider) {
        viewModelScope.launch {
            // Enable this provider and disable others
            val currentProviders = providers.value
            currentProviders.forEach { p ->
                if (p.id == provider.id && !p.isEnabled) {
                    updateProviderUseCase(p.copy(isEnabled = true))
                } else if (p.id != provider.id && p.isEnabled) {
                    updateProviderUseCase(p.copy(isEnabled = false))
                }
            }
            statusMessage.value = "Selected ${provider.name} (${provider.baseUrl})"
            onProviderSelected?.invoke(provider)
        }
    }

    fun selectLocalModel() {
        viewModelScope.launch {
            // Disable custom providers so on-device model is active
            providers.value.filter { it.isEnabled }.forEach { p ->
                updateProviderUseCase(p.copy(isEnabled = false))
            }
            val active = modelDownloadManager.activeModel.value
            statusMessage.value = "Selected on-device model: $active"
            onLocalModelSelected?.invoke()
        }
    }

    fun selectOnDeviceModel(fileName: String) {
        viewModelScope.launch {
            providers.value.filter { it.isEnabled }.forEach { p ->
                updateProviderUseCase(p.copy(isEnabled = false))
            }
            modelDownloadManager.setActiveModel(fileName)
            statusMessage.value = "Active on-device model: $fileName"
            onLocalModelSelected?.invoke()
        }
    }

    fun downloadModel(model: ModelDownloadManager.GgufModelInfo) {
        statusMessage.value = "Starting download for ${model.displayName}..."
        modelDownloadManager.startDownload(model)
    }

    fun cancelModelDownload(fileName: String) {
        modelDownloadManager.cancelDownload(fileName)
        statusMessage.value = "Cancelled download for $fileName"
    }

    fun deleteDownloadedModel(fileName: String) {
        if (modelDownloadManager.deleteModel(fileName)) {
            statusMessage.value = "Deleted model $fileName"
        } else {
            statusMessage.value = "Cannot delete $fileName"
        }
    }

    fun addProvider(name: String, type: ModelConfig.ModelProvider, url: String, apiKey: String, model: String = "") {
        viewModelScope.launch {
            val idToEdit = editingProviderId.value
            val initialModels = if (model.isNotBlank()) listOf(model.trim()) else emptyList()
            val provider = if (idToEdit != null) {
                ApiProvider(
                    id = idToEdit,
                    name = name,
                    providerType = type,
                    baseUrl = url,
                    apiKey = apiKey,
                    models = initialModels
                ).also { updateProviderUseCase(it) }
            } else {
                ApiProvider(
                    name = name,
                    providerType = type,
                    baseUrl = url,
                    apiKey = apiKey,
                    models = initialModels
                ).let { addProviderUseCase(it) }
            }
            statusMessage.value = if (idToEdit != null) "Updated $name" else "Added $name"

            // Clear form
            editingProviderId.value = null
            newProviderName.value = ""
            newProviderUrl.value = ""
            newProviderApiKey.value = ""
            newProviderModel.value = ""

            // Asynchronously fetch server models if none provided or to update list
            try {
                val fetched = getProviderModelsUseCase(provider)
                if (fetched.isNotEmpty()) {
                    val finalModels = if (model.isNotBlank() && fetched.contains(model.trim())) {
                        listOf(model.trim()) + fetched.filter { it != model.trim() }
                    } else if (model.isNotBlank()) {
                        listOf(model.trim()) + fetched
                    } else {
                        fetched
                    }
                    updateProviderUseCase(provider.copy(models = finalModels))
                }
            } catch (_: Exception) {}
        }
    }

    fun editProvider(provider: ApiProvider) {
        editingProviderId.value = provider.id
        selectedProviderType.value = provider.providerType
        newProviderName.value = provider.name
        newProviderUrl.value = provider.baseUrl
        newProviderApiKey.value = provider.apiKey
        newProviderModel.value = provider.models.firstOrNull() ?: ""
        showAddProviderDialog.value = true
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            deleteProviderUseCase(id)
            statusMessage.value = "Deleted provider"
        }
    }

    fun testConnection(provider: ApiProvider) {
        viewModelScope.launch {
            statusMessage.value = "Testing connection to ${provider.baseUrl}..."
            val success = testConnectionUseCase(provider)
            if (success) {
                val fetchedModels = getProviderModelsUseCase(provider)
                if (fetchedModels.isNotEmpty()) {
                    updateProviderUseCase(provider.copy(models = fetchedModels))
                    statusMessage.value = "✓ Connected! Found ${fetchedModels.size} models: ${fetchedModels.take(3).joinToString(", ")}"
                } else {
                    statusMessage.value = "✓ Connected to ${provider.name} (no models returned by server)"
                }
            } else {
                statusMessage.value = "❌ Could not connect to ${provider.baseUrl}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}