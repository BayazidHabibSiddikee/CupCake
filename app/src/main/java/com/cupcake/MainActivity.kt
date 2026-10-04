package com.cupcake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cupcake.ui.screen.chat.ChatScreen
import com.cupcake.ui.screen.device.DeviceScreen
import com.cupcake.ui.screen.home.HomeScreen
import com.cupcake.ui.screen.settings.SettingsScreen
import com.cupcake.ui.screen.systemprompt.SystemPromptScreen
import com.cupcake.ui.theme.CupCakeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CupCakeTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                
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
                            }
                        )
                    }
                    
                    composable(
                        route = "chat/{conversationId}",
                        arguments = listOf(androidx.navigation.navArgument("conversationId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val conversationId = backStackEntry.getString()!!
                        ChatScreen(conversationId = conversationId)
                    }
                    
                    composable(
                        route = "device/{deviceId}",
                        arguments = listOf(androidx.navigation.navArgument("deviceId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val deviceId = backStackEntry.getString()!!
                        DeviceScreen(deviceId = deviceId)
                    }
                    
                    composable("settings") {
                        SettingsScreen(onClose = { navController.popBackStack() })
                    }
                    
                    composable(
                        route = "systemprompt/{promptId?}",
                        arguments = listOf(androidx.navigation.navArgument("promptId") { type = androidx.navigation.NavType.StringType; defaultValue = "" })
                    ) { backStackEntry ->
                        val promptId = backStackEntry.getString()?.takeIf { it.isNotBlank() }
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