package com.cupcake.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.SystemPrompt
import kotlin.math.roundToInt

/**
 * Bottom sheet that lets the user pick the engine/model, tune sampling, and edit the
 * system prompt for the current conversation. Purely presentational: it reports the
 * chosen values through [onApply] and never talks to the repository itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigSheet(
    config: ModelConfig,
    systemPrompt: SystemPrompt?,
    providers: List<ApiProvider>,
    onApply: (ModelConfig, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedProviderId by remember(config.provider, config.customEndpoint) {
        mutableStateOf(
            providers.firstOrNull {
                it.providerType == config.provider && it.baseUrl == config.customEndpoint
            }?.id
        )
    }
    var modelName by remember(config.modelName) { mutableStateOf(config.modelName) }
    var temperature by remember(config.temperature) { mutableFloatStateOf(config.temperature) }
    var maxTokens by remember(config.maxTokens) { mutableIntStateOf(config.maxTokens) }
    var promptText by remember(systemPrompt?.id, systemPrompt?.version) {
        mutableStateOf(systemPrompt?.fullPrompt().orEmpty())
    }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val isCustom = selectedProviderId != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Chat settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            SectionLabel("Engine")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedProviderId == null,
                    onClick = { selectedProviderId = null },
                    label = { Text("On-device") }
                )
                providers.forEach { provider ->
                    FilterChip(
                        selected = selectedProviderId == provider.id,
                        onClick = { selectedProviderId = provider.id },
                        label = { Text(provider.name) }
                    )
                }
            }

            if (isCustom && selectedProvider != null) {
                Text(
                    text = "${selectedProvider.baseUrl} · ${selectedProvider.providerType.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true
            )

            SectionLabel("Temperature ${"%.2f".format(temperature)}")
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..1.5f
            )

            SectionLabel("Max reply length: $maxTokens tokens")
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { maxTokens = (it / 32f).roundToInt() * 32 },
                valueRange = 128f..1024f
            )

            SectionLabel("System prompt")
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("Leave blank to use the character's default prompt") },
                label = { Text("Personality / instructions") }
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.height(0.dp))
                Button(
                    onClick = {
                        val newConfig = if (selectedProvider == null) {
                            config.copy(
                                provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
                                customEndpoint = "",
                                apiKey = "",
                                modelName = modelName.ifBlank { "liltii-v0.2-v3-q4_0.gguf" },
                                temperature = temperature,
                                maxTokens = maxTokens
                            )
                        } else {
                            config.copy(
                                provider = selectedProvider.providerType,
                                customEndpoint = selectedProvider.baseUrl,
                                apiKey = selectedProvider.apiKey,
                                modelName = modelName.ifBlank {
                                    selectedProvider.models.firstOrNull() ?: config.modelName
                                },
                                temperature = temperature,
                                maxTokens = maxTokens
                            )
                        }
                        onApply(newConfig, promptText.takeIf { it.isNotBlank() })
                    }
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
