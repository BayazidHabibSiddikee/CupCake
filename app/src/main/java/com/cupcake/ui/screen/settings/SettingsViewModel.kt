package com.cupcake.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ModelConfig
import com.cupcake.domain.usecase.AddApiProviderUseCase
import com.cupcake.domain.usecase.DeleteApiProviderUseCase
import com.cupcake.domain.usecase.GetApiProvidersUseCase
import com.cupcake.domain.usecase.TestProviderConnectionUseCase
import com.cupcake.domain.usecase.UpdateApiProviderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getProvidersUseCase: GetApiProvidersUseCase,
    private val addProviderUseCase: AddApiProviderUseCase,
    private val updateProviderUseCase: UpdateApiProviderUseCase,
    private val deleteProviderUseCase: DeleteApiProviderUseCase,
    private val testConnectionUseCase: TestProviderConnectionUseCase
) : ViewModel() {

    val providers: StateFlow<List<ApiProvider>> = getProvidersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editingProviderId = MutableStateFlow<String?>(null)
    val showAddProviderDialog = MutableStateFlow(false)
    val selectedProviderType = MutableStateFlow(ModelConfig.ModelProvider.CUSTOM_OLLAMA)
    val newProviderName = MutableStateFlow("")
    val newProviderUrl = MutableStateFlow("")
    val newProviderApiKey = MutableStateFlow("")

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
            // Disable custom providers so local Ollama default is active
            providers.value.filter { it.isEnabled }.forEach { p ->
                updateProviderUseCase(p.copy(isEnabled = false))
            }
            statusMessage.value = "Selected default local Ollama (localhost:11434)"
            onLocalModelSelected?.invoke()
        }
    }

    fun addProvider(name: String, type: ModelConfig.ModelProvider, url: String, apiKey: String) {
        viewModelScope.launch {
            val idToEdit = editingProviderId.value
            if (idToEdit != null) {
                val provider = ApiProvider(
                    id = idToEdit,
                    name = name,
                    providerType = type,
                    baseUrl = url,
                    apiKey = apiKey
                )
                updateProviderUseCase(provider)
                statusMessage.value = "Updated $name"
            } else {
                val provider = ApiProvider(
                    name = name,
                    providerType = type,
                    baseUrl = url,
                    apiKey = apiKey
                )
                addProviderUseCase(provider)
                statusMessage.value = "Added $name"
            }
            // Clear form
            editingProviderId.value = null
            newProviderName.value = ""
            newProviderUrl.value = ""
            newProviderApiKey.value = ""
        }
    }

    fun editProvider(provider: ApiProvider) {
        editingProviderId.value = provider.id
        selectedProviderType.value = provider.providerType
        newProviderName.value = provider.name
        newProviderUrl.value = provider.baseUrl
        newProviderApiKey.value = provider.apiKey
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
            statusMessage.value = if (success) {
                "✓ Connected successfully to ${provider.name}"
            } else {
                "❌ Could not connect to ${provider.baseUrl}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}