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
import kotlinx.coroutines.flow.asStateFlow
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

    val providers = getProvidersUseCase().asStateFlow(initialValue = emptyList())

    val showAddProviderDialog = MutableStateFlow(false)
    val selectedProviderType = MutableStateFlow(ModelConfig.ModelProvider.CUSTOM_OPENAI)
    val newProviderName = MutableStateFlow("")
    val newProviderUrl = MutableStateFlow("")
    val newProviderApiKey = MutableStateFlow("")

    var onProviderSelected: ((ApiProvider) -> Unit)? = null
    var onLocalModelSelected: (() -> Unit)? = null

    fun selectProvider(provider: ApiProvider) {
        onProviderSelected?.invoke(provider)
    }

    fun selectLocalModel() {
        onLocalModelSelected?.invoke()
    }

    fun addProvider(name: String, type: ModelConfig.ModelProvider, url: String, apiKey: String) {
        viewModelScope.launch {
            val provider = ApiProvider(
                name = name,
                providerType = type,
                baseUrl = url,
                apiKey = apiKey
            )
            addProviderUseCase(provider)
            // Clear form
            newProviderName.value = ""
            newProviderUrl.value = ""
            newProviderApiKey.value = ""
        }
    }

    fun editProvider(provider: ApiProvider) {
        // TODO: Navigate to edit screen
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            deleteProviderUseCase(id)
        }
    }

    fun testConnection(provider: ApiProvider) {
        viewModelScope.launch {
            val success = testConnectionUseCase(provider)
            // Show result via snackbar or toast
        }
    }
}