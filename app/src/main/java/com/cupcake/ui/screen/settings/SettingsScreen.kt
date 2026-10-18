package com.cupcake.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ModelConfig
import com.cupcake.service.SoundReactionService
import com.cupcake.ui.theme.CupCakeTheme

private val SectionSpacing = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onNavigateToWifiSetup: (() -> Unit)? = null,
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

    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userAge by viewModel.userAge.collectAsStateWithLifecycle()
    val userGender by viewModel.userGender.collectAsStateWithLifecycle()
    val userRelationship by viewModel.userRelationship.collectAsStateWithLifecycle()
    val userLikes by viewModel.userLikes.collectAsStateWithLifecycle()
    val userDislikes by viewModel.userDislikes.collectAsStateWithLifecycle()
    val userNotes by viewModel.userNotes.collectAsStateWithLifecycle()

    val activeLocalModel by viewModel.modelDownloadManager.activeModel.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.modelDownloadManager.downloadProgress.collectAsStateWithLifecycle()
    val isLocalActive = providers.none { it.isEnabled }
    val activeProvider = providers.firstOrNull { it.isEnabled }

    CupCakeTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            statusMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearStatusMessage() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
            ) {
                // ---- Active engine summary -------------------------------------
                item {
                    ActiveEngineCard(
                        isLocal = isLocalActive,
                        modelName = activeLocalModel,
                        provider = activeProvider
                    )
                }

                // ---- About you -------------------------------------------------
                item {
                    SectionHeader("🙋", "About You", "Your companion uses these details to address you and tailor replies.")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LabeledField("Your name", userName) { viewModel.userName.value = it }
                            LabeledField("Age", userAge) { viewModel.userAge.value = it }
                            LabeledField("Gender / pronouns", userGender) { viewModel.userGender.value = it }
                            LabeledField("Who are you to them? (friend, owner…)", userRelationship) { viewModel.userRelationship.value = it }
                            LabeledField("Likes", userLikes, singleLine = false) { viewModel.userLikes.value = it }
                            LabeledField("Dislikes", userDislikes, singleLine = false) { viewModel.userDislikes.value = it }
                            LabeledField("Anything else to remember", userNotes, singleLine = false) { viewModel.userNotes.value = it }
                            Button(
                                onClick = { viewModel.saveUserProfile() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }

                // ---- Companion device -----------------------------------------
                if (onNavigateToWifiSetup != null) {
                    item {
                        SectionHeader("📡", "Companion Device", "Connect to the ESP32 over Bluetooth and set up its WiFi.")
                    }
                    item {
                        OutlinedButton(
                            onClick = { onNavigateToWifiSetup.invoke() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Connect a device")
                        }
                    }
                }

                // ---- Sound awareness (CupCake's ear) --------------------------
                item {
                    SectionHeader(
                        "👂", "Sound Awareness",
                        "Let CupCake hear what's playing around you — music, games, videos — and react to it."
                    )
                }
                item { SoundAwarenessCard() }

                // ---- On-device models -----------------------------------------
                item {
                    SectionHeader("🧠", "On-Device Models", "Offline AI running on your phone's CPU. Switch anytime without losing chat history.")
                }
                items(com.cupcake.ai.ModelDownloadManager.CATALOG, key = { it.fileName }) { model ->
                    OnDeviceModelCard(
                        model = model,
                        isInstalled = viewModel.modelDownloadManager.isModelInstalled(model.fileName),
                        isActive = isLocalActive && activeLocalModel == model.fileName,
                        downloadProgress = downloadProgressMap[model.fileName],
                        onSelect = { viewModel.selectOnDeviceModel(model.fileName) },
                        onDownload = { viewModel.downloadModel(model) },
                        onCancelDownload = { viewModel.cancelModelDownload(model.fileName) },
                        onDelete = { viewModel.deleteDownloadedModel(model.fileName) }
                    )
                }

                // ---- External providers ---------------------------------------
                item {
                    SectionHeader("🔌", "External API Providers", "Connect to a local server or any OpenAI-compatible endpoint.")
                }
                if (providers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "No custom providers added",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onTest = { viewModel.testConnection(provider) },
                        onEdit = { viewModel.editProvider(provider) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onSelect = { viewModel.selectProvider(provider) }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.showAddProviderDialog.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Add Custom Provider")
                    }
                }

                if (com.cupcake.BuildConfig.DEBUG) {
                    item {
                        TextButton(
                            onClick = { onNavigateToLilTiiEval?.invoke() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "LilTii Eval (debug)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

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
private fun SectionHeader(emoji: String, title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$emoji $title", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Toggle for CupCake's "ear" — captures the mic, classifies sounds on-device
 * with YAMNet and drives companion reactions. Requests RECORD_AUDIO on enable.
 */
@Composable
private fun SoundAwarenessCard() {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(SoundReactionService.isListening) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSoundService(context)
            listening = true
        } else {
            listening = false
        }
    }

    fun enable() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startSoundService(context)
            listening = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Listen to my surroundings", fontWeight = FontWeight.SemiBold)
                Text(
                    if (listening) "CupCake is listening 👂" else "Off — tap to let CupCake hear music, games & more",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = listening,
                onCheckedChange = { on ->
                    if (on) enable() else {
                        stopSoundService(context)
                        listening = false
                    }
                }
            )
        }
    }
}

private fun startSoundService(context: android.content.Context) {
    val intent = Intent(context, SoundReactionService::class.java)
        .setAction(SoundReactionService.ACTION_START)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopSoundService(context: android.content.Context) {
    context.startService(
        Intent(context, SoundReactionService::class.java)
            .setAction(SoundReactionService.ACTION_STOP)
    )
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActiveEngineCard(
    isLocal: Boolean,
    modelName: String?,
    provider: ApiProvider?
) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (isLocal) "📱" else "☁️", fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Active engine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (isLocal) (modelName ?: "Bundled on-device model") else (provider?.name ?: "External provider"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isLocal) "Running offline on this device" else (provider?.baseUrl ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isActive)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = model.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ModelTag(model.tag, model.isBundled)
                if (!model.isBundled && isInstalled && !isActive) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete model",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = model.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (downloadProgress?.isDownloading == true) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Downloading… ${downloadProgress.progressPct}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val mbDownloaded = downloadProgress.downloadedBytes / (1024 * 1024)
                        val mbTotal = downloadProgress.totalBytes / (1024 * 1024)
                        Text(
                            text = "$mbDownloaded / $mbTotal MB",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { downloadProgress.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (downloadProgress?.error != null) {
                Text(
                    text = "Error: ${downloadProgress.error}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (downloadProgress?.isDownloading != true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeMb = model.sizeBytes / (1024 * 1024)
                    Text(
                        text = if (model.isBundled) "Bundled · ~$sizeMb MB" else "~$sizeMb MB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when {
                        isActive -> Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        isInstalled -> Button(
                            onClick = onSelect,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text("Use", fontSize = 12.sp)
                        }
                        else -> FilledTonalButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Download", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelTag(text: String, bundled: Boolean) {
    Surface(
        color = if (bundled) Color(0xFF2E7D32) else Color(0xFF1565C0),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (provider.isEnabled)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = provider.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ProviderTypeBadge(provider.providerType)
                if (provider.isEnabled) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Enabled",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = provider.baseUrl,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${provider.models.size} model(s) available",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTest) { Text("Test", fontSize = 13.sp) }
                TextButton(onClick = onEdit) { Text("Edit", fontSize = 13.sp) }
                TextButton(
                    onClick = onSelect,
                    enabled = !provider.isEnabled
                ) { Text("Use", fontSize = 13.sp) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderTypeBadge(type: ModelConfig.ModelProvider) {
    val (text, color) = when (type) {
        ModelConfig.ModelProvider.CUSTOM_OPENAI -> "OpenAI" to MaterialTheme.colorScheme.primary
        ModelConfig.ModelProvider.CUSTOM_OLLAMA -> "Ollama" to Color(0xFF00A854)
        ModelConfig.ModelProvider.CUSTOM_VLLM -> "vLLM" to Color(0xFF6B46C1)
        ModelConfig.ModelProvider.CUSTOM_OTHER -> "Custom" to MaterialTheme.colorScheme.secondary
        else -> "Local" to MaterialTheme.colorScheme.tertiary
    }
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
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
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(name, providerType, url, apiKey, model) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Add / Edit Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider name") },
                    placeholder = { Text("e.g. My Ollama") },
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Provider type", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(providerType.value)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            listOf(
                                ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                                ModelConfig.ModelProvider.CUSTOM_OPENAI
                            ).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.value) },
                                    onClick = {
                                        onProviderTypeChange(type)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.1.10:11434") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model name (optional)") },
                    placeholder = { Text("e.g. qwen2.5:0.5b") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (optional)") },
                    placeholder = { Text("sk-… or leave empty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
    )
}
