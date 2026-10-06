#!/bin/bash
cat << 'PATCH' > home.patch
--- app/src/main/java/com/cupcake/ui/screen/home/HomeScreen.kt
+++ app/src/main/java/com/cupcake/ui/screen/home/HomeScreen.kt
@@ -10,13 +10,16 @@
 import androidx.compose.foundation.layout.PaddingValues
 import androidx.compose.foundation.lazy.LazyColumn
 import androidx.compose.foundation.lazy.items
 import androidx.compose.material3.Card
 import androidx.compose.material3.ExperimentalMaterial3Api
+import androidx.compose.material3.FloatingActionButton
 import androidx.compose.material3.Icon
+import androidx.compose.material3.Scaffold
 import androidx.compose.material3.Text
 import androidx.compose.material3.TopAppBar
 import androidx.compose.runtime.Composable
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
+import androidx.compose.ui.text.style.TextAlign
 import androidx.compose.ui.text.font.FontWeight
 import androidx.compose.ui.unit.dp
 import androidx.compose.ui.unit.sp
@@ -24,6 +27,7 @@
 import com.cupcake.data.model.Character
 import com.cupcake.ui.theme.CupCakeTheme
 import androidx.compose.material.icons.Icons
+import androidx.compose.material.icons.filled.Add
 import androidx.compose.material.icons.filled.Psychology
 import androidx.compose.material.icons.filled.Settings
 import androidx.compose.material.icons.filled.VideogameAsset
@@ -40,8 +44,8 @@
     val characters = viewModel.listCharacters()
 
     CupCakeTheme {
-        Column(Modifier.fillMaxSize()) {
-            TopAppBar(
+        Scaffold(
+            topBar = { TopAppBar(
                 title = { Text("CupCake") },
                 actions = {
                     androidx.compose.material3.IconButton(onClick = onNavigateToGames) {
@@ -58,9 +62,17 @@
                 colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                     containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                 )
-            )
-
-            Column(
+            ) },
+            floatingActionButton = {
+                FloatingActionButton(onClick = onNavigateToCharacters) {
+                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Character")
+                }
+            }
+        ) { innerPadding ->
+            Column(
-                modifier = Modifier.fillMaxSize(),
+                modifier = Modifier.fillMaxSize().padding(innerPadding),
                 verticalArrangement = Arrangement.spacedBy(8.dp)
             ) {
                 Text(
@@ -69,14 +81,25 @@
                     color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                 )
 
-                // One persistent chat session per character.
-                LazyColumn(
-                    modifier = Modifier.fillMaxSize(),
-                    contentPadding = PaddingValues(16.dp),
-                    verticalArrangement = Arrangement.spacedBy(12.dp)
-                ) {
-                    items(characters) { character ->
-                        CharacterCard(
-                            character = character,
-                            onClick = { onNavigateToChat(character.id) }
-                        )
-                    }
-                }
+                if (characters.isEmpty()) {
+                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
+                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
+                            Text("🫥", fontSize = 64.sp)
+                            Text("No characters found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
+                            Text("Tap the + button to create your\nfirst companion!", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
+                        }
+                    }
+                } else {
+                    LazyColumn(
+                        modifier = Modifier.fillMaxSize(),
+                        contentPadding = PaddingValues(16.dp),
+                        verticalArrangement = Arrangement.spacedBy(12.dp)
+                    ) {
+                        items(characters) { character ->
+                            CharacterCard(
+                                character = character,
+                                onClick = { onNavigateToChat(character.id) }
+                            )
+                        }
+                    }
+                }
             }
         }
     }
PATCH
patch -p0 < home.patch
