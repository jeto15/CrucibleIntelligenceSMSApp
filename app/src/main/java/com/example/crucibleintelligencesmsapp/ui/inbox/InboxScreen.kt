package com.example.crucibleintelligencesmsapp.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crucibleintelligencesmsapp.data.repository.SmsConversation
import com.example.crucibleintelligencesmsapp.ui.theme.CrucibleIntelligenceSMSAppTheme
import com.example.crucibleintelligencesmsapp.viewmodel.InboxViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    isDefaultSmsApp: Boolean = true,
    onRequestDefaultSmsApp: () -> Unit = {},
    onConversationClick: (threadId: Long, address: String, title: String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    InboxContent(
        conversations = conversations,
        isDefaultSmsApp = isDefaultSmsApp,
        onRequestDefaultSmsApp = onRequestDefaultSmsApp,
        onConversationClick = onConversationClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxContent(
    conversations: List<SmsConversation>,
    isDefaultSmsApp: Boolean = true,
    onRequestDefaultSmsApp: () -> Unit = {},
    onConversationClick: (threadId: Long, address: String, title: String) -> Unit
) {
    var showNewChatDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (!isDefaultSmsApp) {
                        TextButton(onClick = onRequestDefaultSmsApp) {
                            Text("Set Default", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "New Conversation"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isDefaultSmsApp) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default SMS App",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Set Crucible as your default SMS app to receive and reply to messages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestDefaultSmsApp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Set Default")
                        }
                    }
                }
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Conversations Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your incoming and outgoing SMS messages will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(conversations, key = { it.threadId.takeIf { id -> id > 0 } ?: it.address }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            onClick = {
                                onConversationClick(conv.threadId, conv.address, conv.displayTitle)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (showNewChatDialog) {
            NewConversationDialog(
                onDismiss = { showNewChatDialog = false },
                onStartChat = { phoneNumber ->
                    showNewChatDialog = false
                    onConversationClick(-1L, phoneNumber, phoneNumber)
                }
            )
        }
    }
}

@Composable
fun NewConversationDialog(
    onDismiss: () -> Unit,
    onStartChat: (phoneNumber: String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Conversation",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enter recipient phone number:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        if (isError) {
                            isError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1 555 012 3456") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = phoneNumber.trim()
                            if (validatePhoneNumber(trimmed)) {
                                onStartChat(trimmed)
                            } else {
                                isError = true
                                errorMessage = "Please enter a valid phone number"
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = phoneNumber.trim()
                    if (validatePhoneNumber(trimmed)) {
                        onStartChat(trimmed)
                    } else {
                        isError = true
                        errorMessage = "Please enter a valid phone number"
                    }
                }
            ) {
                Text("Start Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun validatePhoneNumber(number: String): Boolean {
    val digitsOnly = number.filter { it.isDigit() }
    return digitsOnly.length >= 3
}

@Composable
fun ConversationItem(
    conversation: SmsConversation,
    onClick: () -> Unit
) {
    val isUnread = conversation.unreadCount > 0
    val firstChar = conversation.displayTitle.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = conversation.displayTitle,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = conversation.lastMessage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (firstChar.isNotEmpty() && firstChar != "?") {
                    Text(
                        text = firstChar,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formatTimestamp(conversation.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                )
                if (isUnread) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(text = conversation.unreadCount.toString())
                    }
                }
            }
        }
    )
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday"
        }
        now.timeInMillis - msgTime.timeInMillis < 7 * 24 * 60 * 60 * 1000L -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun InboxScreenPreview() {
    CrucibleIntelligenceSMSAppTheme {
        InboxContent(
            conversations = listOf(
                SmsConversation(1L, "+1234567890", "James Bond", "James Bond", "Are we still on for the mission?", System.currentTimeMillis()),
                SmsConversation(2L, "+1987654321", "Ethan Hunt", "Ethan Hunt", "This message will self-destruct.", System.currentTimeMillis() - 3600000, unreadCount = 2),
                SmsConversation(3L, "+1555000111", "Jason Bourne", "Jason Bourne", "I remember everything.", System.currentTimeMillis() - 86400000)
            ),
            isDefaultSmsApp = false,
            onRequestDefaultSmsApp = {},
            onConversationClick = { _, _, _ -> }
        )
    }
}
