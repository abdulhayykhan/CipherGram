package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.network.InstagramScraper
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SessionScreen
import com.example.ui.screens.ThreadListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize coordinates viewmodel
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        // Handle initial external link sharing if opened via share sheet
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "session"
                        ) {
                            // 1. Session Login Config Route
                            composable("session") {
                                SessionScreen(
                                    viewModel = viewModel,
                                    onNavigateToChats = {
                                        navController.navigate("threads") {
                                            popUpTo("session") { inclusive = false }
                                        }
                                    }
                                )
                            }

                            // 2. Chat Contacts Threads Picker Route
                            composable("threads") {
                                ThreadListScreen(
                                    viewModel = viewModel,
                                    onNavigateToChat = { id ->
                                        navController.navigate("chat/$id")
                                    },
                                    onNavigateToSession = {
                                        navController.navigate("session")
                                    }
                                )
                            }

                            // 3. Main Chat thread Stream Route
                            composable(
                                route = "chat/{threadId}",
                                arguments = listOf(
                                    navArgument("threadId") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                                
                                // Re-sync active selection (fallback safe)
                                LaunchedEffect(threadId) {
                                    viewModel.selectThread(threadId)
                                }

                                ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = {
                                        viewModel.selectThread(null)
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Intercepts and parses text payload shared via standard Android Share Sheets (ACTION_SEND).
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                if (sharedText.isNotEmpty()) {
                    viewModel.stagedMessageText = sharedText
                    Toast.makeText(
                        this, 
                        "Shared Content Loaded into Chat Box!", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
