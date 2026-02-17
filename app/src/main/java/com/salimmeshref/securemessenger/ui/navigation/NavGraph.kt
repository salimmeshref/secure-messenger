package com.salimmeshref.securemessenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.salimmeshref.securemessenger.ui.auth.AuthScreen
import com.salimmeshref.securemessenger.ui.chat.ChatScreen
import com.salimmeshref.securemessenger.ui.conversations.ConversationsScreen
import com.salimmeshref.securemessenger.ui.settings.SettingsScreen
import com.salimmeshref.securemessenger.ui.users.UsersScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = "auth"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("conversations") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("conversations") {
            ConversationsScreen(
                onConversationClick = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onNewConversation = {
                    navController.navigate("users")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onSignOut = {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("users") {
            UsersScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToChat = { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        popUpTo("users") { inclusive = true }
                    }
                }
            )
        }
    }
}
