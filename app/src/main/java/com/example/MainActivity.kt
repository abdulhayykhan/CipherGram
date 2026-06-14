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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val threads by viewModel.threads.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = if (viewModel.isAuthenticated) "threads" else "session"
                        ) {
                            // 1. Login Screen
                            composable("session") {
                                SessionScreen(
                                    viewModel = viewModel,
                                    onNavigateToChats = {
                                        navController.navigate("threads") {
                                            popUpTo("session") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 2. Thread list
                            composable("threads") {
                                ThreadListScreen(
                                    viewModel = viewModel,
                                    onNavigateToChat = { id ->
                                        navController.navigate("chat/$id")
                                    },
                                    onNavigateToSession = {
                                        navController.navigate("session") {
                                            popUpTo("threads") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 3. Chat screen
                            composable(
                                route = "chat/{threadId}",
                                arguments = listOf(
                                    navArgument("threadId") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""

                                // Re-select thread by ID when navigating back via deep link
                                LaunchedEffect(threadId) {
                                    val thread = threads.find { it.id == threadId }
                                    if (thread != null) viewModel.selectThread(thread)
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

    /**
     * Delegates Facebook Login activity results to the ViewModel's CallbackManager.
     * Required for the Facebook Login SDK to process OAuth responses.
     */
    @Deprecated("Required for Facebook SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Handles text shared to CipherGram via the system share sheet.
     * Supports sharing Instagram URLs directly into the chat input.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null && "text/plain" == type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotEmpty()) {
                viewModel.handleIncomingShareUrl(sharedText)
                Toast.makeText(this, "Content loaded into chat!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
