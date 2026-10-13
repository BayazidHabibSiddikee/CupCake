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

import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.BorderStroke

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onNavigateToLilTiiEval: (() -> Unit)? = null,
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

    val activeLocalModel by viewModel.modelDownloadManager.activeModel.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.modelDownloadManager.downloadProgress.collectAsStateWithLifecycle()
    val isLocalActive = providers.none { it.isEnabled }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings & Models") },
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
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                // Section: On-Device Models
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("On-Device GGUF Models", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Offline AI running directly on your phone CPU. Switch models anytime without losing chat history.",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(com.cupcake.ai.ModelDownloadManager.CATALOG) { model ->
                    val isInstalled = viewModel.modelDownloadManager.isModelInstalled(model.fileName)
                    val isCurrentActive = isLocalActive && activeLocalModel == model.fileName
                    val progress = downloadProgressMap[model.fileName]

                    OnDeviceModelCard(
                        model = model,
                        isInstalled = isInstalled,
                        isActive = isCurrentActive,
                        downloadProgress = progress,
                        onSelect = { viewModel.selectOnDeviceModel(model.fileName) },
                        onDownload = { viewModel.downloadModel(model) },
                        onCancelDownload = { viewModel.cancelModelDownload(model.fileName) },
                        onDelete = { viewModel.deleteDownloadedModel(model.fileName) }
                    )
                }

                // Section: External / Custom API Providers
                item {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("External API Providers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Connect to local Ollama on Termux/PC or OpenAI-compatible servers.",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (providers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "No custom providers added",
                                    fontSize = 14.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        onTest = { viewModel.testConnection(provider) },
                        onEdit = { viewModel.editProvider(provider) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onSelect = { viewModel.selectProvider(provider) }
                    )
                }

                // Add provider button
                item {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.showAddProviderDialog.value = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
                    ) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null)
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        Text("Add Custom Provider")
                    }
                }

                // Debug-only: LilTii eval entry point
                if (com.cupcake.BuildConfig.DEBUG) {
                    item {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                        androidx.compose.material3.TextButton(
                            onClick = { onNavigateToLilTiiEval?.invoke() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "LilTii Eval (debug)",
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
fun OnDeviceModelCard(
    model: com.cupcake.ai.ModelDownloadManager.GgufModelInfo,
    isInstalled: Boolean,
    isActive: Boolean,
    downloadProgress: com.cupcake.ai.ModelDownloadManager.DownloadProgress?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isActive)
                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isActive)
            androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        androidx.compose.material3.Surface(
                            color = if (model.isBundled) Color(0xFF2E7D32) else Color(0xFF1565C0),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = model.tag,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = model.description,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!model.isBundled && isInstalled && !isActive) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Download progress if active
            if (downloadProgress?.isDownloading == true) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Downloading... ${downloadProgress.progressPct}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                        val mbDownloaded = downloadProgress.downloadedBytes / (1024 * 1024)
                        val mbTotal = downloadProgress.totalBytes / (1024 * 1024)
                        Text(
                            text = "$mbDownloaded MB / $mbTotal MB",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { downloadProgress.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(
                            onClick = onCancelDownload,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (downloadProgress?.error != null) {
                Text(
                    text = "Error: ${downloadProgress.error}",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }

            // Actions row
            if (downloadProgress?.isDownloading != true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeMb = model.sizeBytes / (1024 * 1024)
                    Text(
                        text = if (model.isBundled) "Bundled (~$sizeMb MB)" else "Size: ~$sizeMb MB",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isActive) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    } else if (isInstalled) {
                        Button(
                            onClick = onSelect,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text("Use Model", fontSize = 12.sp)
                        }
                    } else {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = onDownload,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                            Text("Download", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
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