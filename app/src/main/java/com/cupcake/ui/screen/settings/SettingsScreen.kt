package com.cupcake.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ModelConfig
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val showAddProviderDialog by viewModel.showAddProviderDialog.collectAsStateWithLifecycle()
    val selectedProviderType by viewModel.selectedProviderType.collectAsStateWithLifecycle()
    val newProviderName by viewModel.newProviderName.collectAsStateWithLifecycle()
    val newProviderUrl by viewModel.newProviderUrl.collectAsStateWithLifecycle()
    val newProviderApiKey by viewModel.newProviderApiKey.collectAsStateWithLifecycle()
    val newProviderModel by viewModel.newProviderModel.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            // Status message feedback
            statusMessage?.let { msg ->
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Providers list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Default Ollama model section
                ProviderSection(
                    title = "Ollama (Local Server)",
                    subtitle = "smollm / qwen2:0.5b via Ollama (http://localhost:11434)",
                    isEnabled = providers.none { it.isEnabled },
                    onClick = { viewModel.selectLocalModel() }
                )

                // Custom providers
                Text("Custom API Providers", fontWeight = FontWeight.Medium, fontSize = 18.sp)

                if (providers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text("No custom providers configured", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Add a custom API endpoint to use cloud models", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(providers) { provider ->
                        ProviderCard(
                            provider = provider,
                            onTest = { viewModel.testConnection(provider) },
                            onEdit = { viewModel.editProvider(provider) },
                            onDelete = { viewModel.deleteProvider(provider.id) },
                            onSelect = { viewModel.selectProvider(provider) }
                        )
                    }
                }

                // Add provider button
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.showAddProviderDialog.value = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null)
                    Text("Add Custom Provider")
                }
            }
        }
    }

    // Add provider dialog
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = {
                viewModel.showAddProviderDialog.value = false
                viewModel.editingProviderId.value = null
                viewModel.newProviderName.value = ""
                viewModel.newProviderUrl.value = ""
                viewModel.newProviderApiKey.value = ""
                viewModel.newProviderModel.value = ""
            },
            onConfirm = { name, type, url, apiKey, model ->
                viewModel.addProvider(name, type, url, apiKey, model)
                viewModel.showAddProviderDialog.value = false
            },
            providerType = selectedProviderType,
            onProviderTypeChange = { viewModel.selectedProviderType.value = it },
            name = newProviderName,
            onNameChange = { viewModel.newProviderName.value = it },
            url = newProviderUrl,
            onUrlChange = { viewModel.newProviderUrl.value = it },
            apiKey = newProviderApiKey,
            onApiKeyChange = { viewModel.newProviderApiKey.value = it },
            model = newProviderModel,
            onModelChange = { viewModel.newProviderModel.value = it }
        )
    }
}

@Composable
fun ProviderSection(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(subtitle, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ProviderCard(
    provider: ApiProvider,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(provider.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        ProviderTypeBadge(provider.providerType)
                        if (provider.isEnabled) {
                            androidx.compose.material3.Text(
                                text = "✓ Enabled",
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(provider.baseUrl, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${provider.models.size} models available", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTest) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Wifi, contentDescription = "Test connection")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onSelect) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.CheckCircle, contentDescription = "Select", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Delete", tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderTypeBadge(type: ModelConfig.ModelProvider) {
    val (text, color) = when (type) {
        ModelConfig.ModelProvider.CUSTOM_OPENAI -> "OpenAI" to androidx.compose.material3.MaterialTheme.colorScheme.primary
        ModelConfig.ModelProvider.CUSTOM_OLLAMA -> "Ollama" to Color(0xFF00A854)
        ModelConfig.ModelProvider.CUSTOM_VLLM -> "vLLM" to Color(0xFF6B46C1)
        ModelConfig.ModelProvider.CUSTOM_OTHER -> "Custom" to androidx.compose.material3.MaterialTheme.colorScheme.secondary
        else -> "Local" to androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    }

    androidx.compose.material3.Text(
        text = text,
        fontSize = 11.sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
    )
}

@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, ModelConfig.ModelProvider, String, String, String) -> Unit,
    providerType: ModelConfig.ModelProvider,
    onProviderTypeChange: (ModelConfig.ModelProvider) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name, providerType, url, apiKey, model) }, enabled = name.isNotBlank() && url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add / Edit Provider") },
        text = {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g., My Ollama, OpenAI") },
                    singleLine = true
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

                // Provider type dropdown
                Text("Provider Type", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                
                var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(providerType.value)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                            contentDescription = "Dropdown"
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        val types = listOf(
                            ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                            ModelConfig.ModelProvider.CUSTOM_OPENAI
                        )
                        types.forEach { type ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(type.value) },
                                onClick = {
                                    onProviderTypeChange(type)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

                TextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.1.X:11434") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

                TextField(
                    value = model,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model Name (optional)") },
                    placeholder = { Text("e.g., qwen2.5:0.5b or smollm:135m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

                TextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key (optional)") },
                    placeholder = { Text("sk-... or leave empty for local") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
    )
}