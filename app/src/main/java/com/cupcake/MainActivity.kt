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
import com.cupcake.ui.screen.game.GameScreen
import com.cupcake.ui.screen.home.HomeScreen
import com.cupcake.ui.screen.home.HomeViewModel
import com.cupcake.ui.screen.settings.SettingsScreen
import com.cupcake.ui.screen.systemprompt.SystemPromptScreen
import com.cupcake.ui.theme.CupCakeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CupCakeTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = viewModel()
                
                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToChat = { conversationId ->
                                navController.navigate("chat/$conversationId")
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
                            onNavigateToGames = {
                                navController.navigate("games")
                            }
                        )
                    }
                    
                    composable(
                        route = "chat/{conversationId}",
                        arguments = listOf(androidx.navigation.navArgument("conversationId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId")!!
                        ChatScreen(conversationId = conversationId)
                    }
                    
                    composable(
                        route = "device/{deviceId}",
                        arguments = listOf(androidx.navigation.navArgument("deviceId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId")!!
                        DeviceScreen(deviceId = deviceId)
                    }
                    
                    composable("settings") {
                        SettingsScreen(onClose = { navController.popBackStack() })
                    }
                    
                    composable("characters") {
                        CharacterScreen(onClose = { navController.popBackStack() })
                    }
                    
                    composable("games") {
                        GameScreen(onClose = { navController.popBackStack() })
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
                }
            }
        }
    }
}