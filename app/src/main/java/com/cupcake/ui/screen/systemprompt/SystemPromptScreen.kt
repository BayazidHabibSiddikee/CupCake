package com.cupcake.ui.screen.systemprompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.data.model.SystemPrompt
import com.cupcake.ui.component.ImagePickerSheet
import com.cupcake.ui.theme.CupCakeTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptScreen(
    promptId: String? = null,
    onClose: () -> Unit,
    viewModel: SystemPromptViewModel = viewModel()
) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val prompts by viewModel.allPrompts.collectAsStateWithLifecycle()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()
    val showImagePicker by viewModel.showImagePicker.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(prompt?.name ?: "") }
    var text by remember { mutableStateOf(prompt?.text ?: "") }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            androidx.compose.material3.TopAppBar(
                title = { Text(if (promptId == null) "New System Prompt" else "Edit System Prompt") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(androidx.compose.material.icons.Icons.Filled.ArrowBack), contentDescription = "Back") } },
                actions = {
                    if (promptId != null) {
                        IconButton(onClick = { viewModel.showDeleteDialog.value = true }) {
                            Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Delete), contentDescription = "Delete", tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = { viewModel.save(name, text) }, enabled = name.isNotBlank()) {
                        Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Save), contentDescription = "Save")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            // Form
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt Name") },
                    placeholder = { Text("e.g., Coding Assistant, Creative Writer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // Text area
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(bottom = 16.dp),
                    label = { Text("System Prompt Text") },
                    placeholder = { Text("Enter the system prompt instructions...") },
                    minLines = 8,
                    maxLines = 20,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                // Images section
                ImagesSection(
                    images = prompt?.images ?: emptyList(),
                    onAddImage = { viewModel.showImagePicker.value = true },
                    onRemoveImage = { imageId -> viewModel.removeImage(imageId) }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteDialog.value = false },
            confirmButton = {
                Button(onClick = { viewModel.deletePrompt(); viewModel.showDeleteDialog.value = false }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.showDeleteDialog.value = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete Prompt") },
            text = { Text("Are you sure you want to delete this system prompt? This cannot be undone.") }
        )
    }

    // Image picker
    ImagePickerSheet(
        visible = showImagePicker,
        onDismiss = { viewModel.showImagePicker.value = false },
        onImagesSelected = { uris -> viewModel.addImages(uris) }
    )
}

@Composable
fun ImagesSection(
    images: List<PromptImage>,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Attached Images (${images.size})", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            androidx.compose.material3.IconButton(onClick = onAddImage) {
                Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Add), contentDescription = "Add image")
            }
        }

        if (images.isEmpty()) {
            Text("No images attached. Add images to provide visual context.", 
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { image ->
                    ImageCard(image = image, onRemove = { onRemoveImage(image.id) })
                }
            }
        }
    }
}

@Composable
fun ImageCard(
    image: PromptImage,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            ) {
                androidx.compose.ui.res.painterResource(id = androidx.compose.material.icons.Icons.Filled.Image).also { painter ->
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = "Image thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Info
            Column(Modifier.weight(1f)) {
                Text(
                    text = image.description.ifEmpty { "Image ${images.indexOf(image) + 1}" },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (image.extractedText.isNotEmpty()) {
                    Text(
                        text = "Extracted: ${image.extractedText.take(100)}...",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Remove button
            androidx.compose.material3.IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(androidx.compose.material.icons.Icons.Filled.Delete),
                    contentDescription = "Remove",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        }
    }
}