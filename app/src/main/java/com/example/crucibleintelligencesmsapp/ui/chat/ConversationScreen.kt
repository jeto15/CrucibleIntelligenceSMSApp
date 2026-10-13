package com.example.crucibleintelligencesmsapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.repository.SmsMessage
import com.example.crucibleintelligencesmsapp.data.repository.SmsStatus
import com.example.crucibleintelligencesmsapp.ui.theme.CrucibleIntelligenceSMSAppTheme
import com.example.crucibleintelligencesmsapp.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ConversationScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onDossierClick: ((Long) -> Unit)? = null
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.messages.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            viewModel.markAsRead()
        }
    }

    val displayTitle = profile?.name?.takeIf { it.isNotBlank() }
        ?: viewModel.initialTitle.takeIf { it.isNotBlank() }
        ?: viewModel.address

    var showDossierSheet by remember { mutableStateOf(false) }

    ConversationContent(
        displayTitle = displayTitle,
        address = viewModel.address,
        messages = messages,
        onBackClick = onBackClick,
        onDossierClick = {
            showDossierSheet = true
            profile?.let { p -> onDossierClick?.invoke(p.id) }
        },
        onSendMessage = { text ->
            viewModel.sendMessage(text)
        }
    )

    if (showDossierSheet) {
        DossierIntelligenceBottomSheet(
            displayTitle = displayTitle,
            address = viewModel.address,
            profile = profile,
            messages = messages,
            onDismiss = { showDossierSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationContent(
    displayTitle: String,
    address: String,
    messages: List<SmsMessage>,
    onBackClick: () -> Unit,
    onDossierClick: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (displayTitle != address && address.isNotBlank()) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDossierClick) {
                        Icon(
                            imageVector = Icons.Rounded.Analytics,
                            contentDescription = "Crucible Dossier Intelligence"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages in this conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imeNestedScroll()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            MessageComposer(
                text = messageText,
                onTextChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: SmsMessage) {
    val isIncoming = message.isIncoming
    val alignment = if (isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    val backgroundColor = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val textColor = if (isIncoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    val shape = if (isIncoming) {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isIncoming) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.body,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = formatMessageTimestamp(message.timestamp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (!isIncoming) {
                    MessageStatusIndicator(status = message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIndicator(status: SmsStatus) {
    val (icon, text, color) = when (status) {
        SmsStatus.SENDING -> Triple(
            Icons.Rounded.Schedule,
            "Sending...",
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        SmsStatus.SENT -> Triple(
            Icons.Rounded.Check,
            "Sent",
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        SmsStatus.DELIVERED -> Triple(
            Icons.Rounded.DoneAll,
            "Delivered",
            MaterialTheme.colorScheme.primary
        )
        SmsStatus.FAILED -> Triple(
            Icons.Rounded.ErrorOutline,
            "Failed",
            MaterialTheme.colorScheme.error
        )
        SmsStatus.NONE -> return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            fontSize = 10.sp,
            color = color,
            fontWeight = if (status == SmsStatus.FAILED) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            placeholder = { Text("Text message...") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        FilledIconButton(
            onClick = onSendClick,
            enabled = text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send message"
            )
        }
    }
}

fun formatMessageTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    ) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun ConversationContentPreview() {
    CrucibleIntelligenceSMSAppTheme {
        ConversationContent(
            displayTitle = "James Bond",
            address = "+1234567890",
            messages = listOf(
                SmsMessage(1, 1L, "+1234567890", "Hello, this is an incoming message.", System.currentTimeMillis() - 200000, 1, true, SmsStatus.NONE),
                SmsMessage(2, 1L, "+1234567890", "Sending message...", System.currentTimeMillis() - 150000, 4, false, SmsStatus.SENDING),
                SmsMessage(3, 1L, "+1234567890", "Sent message", System.currentTimeMillis() - 100000, 2, false, SmsStatus.SENT),
                SmsMessage(4, 1L, "+1234567890", "Delivered message!", System.currentTimeMillis() - 50000, 2, false, SmsStatus.DELIVERED),
                SmsMessage(5, 1L, "+1234567890", "Failed message", System.currentTimeMillis() - 10000, 5, false, SmsStatus.FAILED)
            ),
            onBackClick = {},
            onDossierClick = {},
            onSendMessage = {}
        )
    }
}
