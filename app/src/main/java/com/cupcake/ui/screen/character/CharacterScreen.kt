package com.cupcake.ui.screen.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.ai.CharacterManager
import com.cupcake.data.model.Character
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Image
import com.cupcake.ui.component.CharacterAvatar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(
    onClose: () -> Unit,
    viewModel: CharacterViewModel = hiltViewModel()
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val newCharName by viewModel.newCharName.collectAsStateWithLifecycle()
    val newCharDescription by viewModel.newCharDescription.collectAsStateWithLifecycle()
    val newCharPrompt by viewModel.newCharPrompt.collectAsStateWithLifecycle()
    val newCharPersonality by viewModel.newCharPersonality.collectAsStateWithLifecycle()
    val newCharAvatar by viewModel.newCharAvatar.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.newCharAvatar.value = it.toString() }
    }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.TopAppBar(
                title = { Text("Characters") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.showCreateDialog.value = true }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Add, contentDescription = "Create character")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(characters) { character ->
                    CharacterCard(
                        character = character,
                        isSelected = character.isSelected,
                        onSelect = { viewModel.selectCharacter(character.id) },
                        onEdit = { if (!character.isBuiltIn) viewModel.editCharacter(character) },
                        onDelete = { if (!character.isBuiltIn) viewModel.deleteCharacter(character.id) }
                    )
                }
            }
        }
    }

    // Create/Edit dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.showCreateDialog.value = false
                viewModel.clearForm()
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.saveCharacter()
                    viewModel.showCreateDialog.value = false
                }, enabled = newCharName.isNotBlank() && newCharPrompt.isNotBlank()) {
                    Text(viewModel.editingCharacterId.value?.let { "Save" } ?: "Create")
                }
            },
            dismissButton = {
                Button(onClick = { 
                    viewModel.showCreateDialog.value = false
                    viewModel.clearForm()
                }) {
                    Text("Cancel")
                }
            },
            title = { Text(viewModel.editingCharacterId.value?.let { "Edit Character" } ?: "Create Character") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar Picker Row
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CharacterAvatar(
                            avatar = newCharAvatar,
                            modifier = Modifier.size(56.dp),
                            emojiSize = 30.sp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                                Text("Choose Image / Art", fontSize = 12.sp)
                            }
                            TextField(
                                value = newCharAvatar,
                                onValueChange = { viewModel.newCharAvatar.value = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Emoji or image path") },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                            )
                        }
                    }

                    TextField(
                        value = newCharName,
                        onValueChange = { viewModel.newCharName.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        placeholder = { Text("e.g., Cyber Sam, Alice") },
                        singleLine = true
                    )

                    TextField(
                        value = newCharDescription,
                        onValueChange = { viewModel.newCharDescription.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Short Description") },
                        placeholder = { Text("e.g., Sassy companion who roasts code") },
                        singleLine = true
                    )

                    TextField(
                        value = newCharPrompt,
                        onValueChange = { viewModel.newCharPrompt.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("System Prompt (Persona)") },
                        placeholder = { Text("Define character identity and background...") },
                        minLines = 4,
                        maxLines = 8
                    )

                    TextField(
                        value = newCharPersonality,
                        onValueChange = { viewModel.newCharPersonality.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Personality ID") },
                        placeholder = { Text("friendly, grumpy, sarcastic, educational, rage_gamer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }
        )
    }
}

@Composable
fun CharacterCard(
    character: Character,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) 
                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer 
            else 
                androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = onSelect
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with custom image support
                    CharacterAvatar(
                        avatar = character.avatar,
                        modifier = Modifier.size(48.dp),
                        emojiSize = 26.sp
                    )

                    androidx.compose.foundation.layout.Column {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(character.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            if (character.isBuiltIn) {
                                androidx.compose.material3.Text(
                                    text = "Built-in",
                                    fontSize = 11.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .background(
                                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                            if (isSelected) {
                                androidx.compose.material3.Text(
                                    text = "✓ Active",
                                    fontSize = 11.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .background(
                                            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }
                        Text(character.description, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Personality: ${character.personality} • Languages: ${character.languages.joinToString(", ")}", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }

                // Actions
                if (!character.isBuiltIn) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Delete", tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Prompt preview
            Text(
                text = character.systemPrompt.take(150) + "...",
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}