package com.cupcake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cupcake.ui.screen.chat.ChatScreen
import com.cupcake.ui.screen.character.CharacterScreen
import com.cupcake.ui.screen.device.DeviceScreen
import com.cupcake.ui.screen.device.WifiSetupScreen
import com.cupcake.ui.screen.game.GameScreen
import com.cupcake.ui.screen.home.HomeScreen
import com.cupcake.ui.screen.home.HomeViewModel
import com.cupcake.ui.screen.points.PointsScreen
import com.cupcake.ui.screen.settings.SettingsScreen
import com.cupcake.ui.screen.systemprompt.SystemPromptScreen
import com.cupcake.ui.theme.CupCakeTheme
import dagger.hilt.android.AndroidEntryPoint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.cupcake.ui.screen.debug.LilTiiEvalScreen
import com.cupcake.BuildConfig

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notificationCharId = intent?.getStringExtra("characterId")
        val startDestination = if (!notificationCharId.isNullOrBlank()) "chat/$notificationCharId" else "home"

        setContent {
            CupCakeTheme {
                val context = androidx.compose.ui.platform.LocalContext.current

                // Runtime permission for Android 13+ Notifications
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    android.util.Log.i("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Background ESP service battery optimization exemption
                    try {
                        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                            val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(batteryIntent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Battery optimization intent skipped: ${e.message}")
                    }
                }

                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                
                NavHost(navController, startDestination = startDestination) {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToChat = { characterId ->
                                navController.navigate("chat/$characterId")
                            },
                            onNavigateToDevice = { deviceId ->
                                navController.navigate("device/$deviceId")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            },
                            onNavigateToCharacters = {
                                navController.navigate("characters")
                            },
                            onNavigateToGame = { gameType ->
                                val route = if (gameType != null) "games?gameType=$gameType" else "games"
                                navController.navigate(route)
                            },
                            onNavigateToPoints = {
                                navController.navigate("points")
                            }
                        )
                    }
                    
                    composable(
                        route = "chat/{characterId}",
                        arguments = listOf(androidx.navigation.navArgument("characterId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val characterId = backStackEntry.arguments?.getString("characterId") ?: "marin_kitagawa"
                        ChatScreen(
                            characterId = characterId,
                            onSwitchCharacter = { id ->
                                navController.navigate("chat/$id") {
                                    popUpTo("chat/{characterId}") { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable(
                        route = "device/{deviceId}",
                        arguments = listOf(androidx.navigation.navArgument("deviceId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId")!!
                        DeviceScreen(deviceId = deviceId)
                    }
                    
                    composable("settings") {
                        SettingsScreen(
                            onClose = { navController.popBackStack() },
                            onNavigateToWifiSetup = { navController.navigate("wifi-setup") },
                            onNavigateToLilTiiEval = {
                                if (BuildConfig.DEBUG) {
                                    navController.navigate("liltii-eval")
                                }
                            }
                        )
                    }

                    composable("wifi-setup") {
                        WifiSetupScreen(
                            onClose = { navController.popBackStack() }
                        )
                    }
                    
                    composable("characters") {
                        CharacterScreen(onClose = { navController.popBackStack() })
                    }
                    
                    composable(
                        route = "games?gameType={gameType}",
                        arguments = listOf(
                            androidx.navigation.navArgument("gameType") {
                                type = androidx.navigation.NavType.StringType
                                defaultValue = ""
                                nullable = true
                            }
                        )
                    ) { backStackEntry ->
                        val gameType = backStackEntry.arguments?.getString("gameType")?.takeIf { it.isNotBlank() }
                        GameScreen(
                            startGameType = gameType,
                            onClose = { navController.popBackStack() }
                        )
                    }
                    
                    composable("points") {
                        PointsScreen(onClose = { navController.popBackStack() })
                    }
                    
                    composable(
                        route = "systemprompt/{promptId?}",
                        arguments = listOf(androidx.navigation.navArgument("promptId") { type = androidx.navigation.NavType.StringType; defaultValue = "" })
                    ) { backStackEntry ->
                        val promptId = backStackEntry.arguments?.getString("promptId")?.takeIf { it.isNotBlank() }
                        SystemPromptScreen(
                            promptId = promptId,
                            onClose = { navController.popBackStack() }
                        )
                    }

                    if (BuildConfig.DEBUG) {
                        composable("liltii-eval") {
                            LilTiiEvalScreen(onClose = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}