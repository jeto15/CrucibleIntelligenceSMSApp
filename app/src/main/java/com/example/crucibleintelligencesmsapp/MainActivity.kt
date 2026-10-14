package com.example.crucibleintelligencesmsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.crucibleintelligencesmsapp.data.AppDatabase
import com.example.crucibleintelligencesmsapp.data.repository.SmsRepository
import com.example.crucibleintelligencesmsapp.ui.chat.ConversationScreen
import com.example.crucibleintelligencesmsapp.ui.inbox.InboxScreen
import com.example.crucibleintelligencesmsapp.ui.permissions.PermissionWrapper
import com.example.crucibleintelligencesmsapp.ui.theme.CrucibleIntelligenceSMSAppTheme
import com.example.crucibleintelligencesmsapp.util.DefaultSmsHelper
import com.example.crucibleintelligencesmsapp.viewmodel.ChatViewModel
import com.example.crucibleintelligencesmsapp.viewmodel.InboxViewModel
import kotlinx.serialization.Serializable

@Serializable
data object InboxRoute

@Serializable
data class ConversationRoute(
    val threadId: Long,
    val address: String,
    val title: String
)

class MainActivity : ComponentActivity() {

    private var isDefaultSmsApp by mutableStateOf(false)

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkDefaultSmsApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            checkDefaultSmsApp()
            if (!isDefaultSmsApp) {
                requestDefaultSmsApp()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            CrucibleIntelligenceSMSAppTheme {
                PermissionWrapper {
                    val backStack = remember { mutableStateListOf<Any>(InboxRoute) }
                    val database = remember { AppDatabase.getDatabase(applicationContext) }
                    val smsRepo = remember { SmsRepository(applicationContext, database) }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = { key ->
                            when (key) {
                                is InboxRoute -> NavEntry(key) {
                                    val inboxViewModel: InboxViewModel = viewModel(
                                        key = "inbox_view_model",
                                        factory = InboxViewModel.Factory(smsRepo)
                                    )
                                    InboxScreen(
                                        viewModel = inboxViewModel,
                                        isDefaultSmsApp = isDefaultSmsApp,
                                        onRequestDefaultSmsApp = { requestDefaultSmsApp() },
                                        onConversationClick = { threadId, address, title ->
                                            backStack.add(ConversationRoute(threadId, address, title))
                                        }
                                    )
                                }
                                is ConversationRoute -> NavEntry(key) {
                                    val factory = ChatViewModel.Factory(
                                        key.threadId,
                                        key.address,
                                        key.title,
                                        database,
                                        smsRepo
                                    )
                                    val viewModel: ChatViewModel = viewModel(
                                        key = "chat_${key.threadId}_${key.address}",
                                        factory = factory
                                    )

                                    ConversationScreen(
                                        viewModel = viewModel,
                                        onBackClick = { backStack.removeLastOrNull() },
                                        onDossierClick = { _ ->
                                            // Handle dossier click if needed
                                        }
                                    )
                                }
                                else -> error("Unknown route: $key")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
    }

    private fun checkDefaultSmsApp() {
        try {
            isDefaultSmsApp = DefaultSmsHelper.isDefaultSmsApp(this)
        } catch (e: Exception) {
            isDefaultSmsApp = false
        }
    }

    fun requestDefaultSmsApp() {
        try {
            val intent = DefaultSmsHelper.createRequestDefaultSmsIntent(this)
            if (intent != null) {
                requestRoleLauncher.launch(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
