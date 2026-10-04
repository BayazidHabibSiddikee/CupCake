package com.cupcake.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onImagesSelected: (List<String>) -> Unit
) {
    if (!visible) return

    var selectedUris by remember { mutableStateOf<MutableList<String>>(mutableListOf()) }

    androidx.compose.material3.ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(initialValue = androidx.compose.material3.BottomSheetValue.Collapsed),
        sheetContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Gray)
                        .padding(top = 12.dp)
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Images", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Close), contentDescription = "Close")
                    }
                }

                // Image grid (placeholder)
                Text("Image picker implementation would go here", 
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                // Selected count
                if (selectedUris.isNotEmpty()) {
                    Text("${selectedUris.size} images selected",
                        fontSize = 14.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }

                // Confirm button
                androidx.compose.material3.Button(
                    onClick = {
                        onImagesSelected(selectedUris.toList())
                        onDismiss()
                    },
                    enabled = selectedUris.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Add Images")
                }
            }
        },
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    )
}

// Placeholder for ModalBottomSheetState
@Composable
fun rememberModalBottomSheetState(initialValue: androidx.compose.material3.BottomSheetValue): androidx.compose.material3.ModalBottomSheetState {
    return androidx.compose.material3.MaterialTheme { androidx.compose.material3.ModalBottomSheetDefaults.modalBottomSheetState(initialValue) }
}