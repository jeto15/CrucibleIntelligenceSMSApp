package com.example.crucibleintelligencesmsapp.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crucibleintelligencesmsapp.data.AttachmentType
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.MediaAttachment
import com.example.crucibleintelligencesmsapp.data.repository.SmsMessage
import com.example.crucibleintelligencesmsapp.data.repository.SmsStatus
import com.example.crucibleintelligencesmsapp.ui.theme.CrucibleIntelligenceSMSAppTheme
import com.example.crucibleintelligencesmsapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var messageText by remember { mutableStateOf("") }

    ConversationContent(
        displayTitle = displayTitle,
        address = viewModel.address,
        messages = messages,
        messageText = messageText,
        onMessageTextChange = { messageText = it },
        onBackClick = onBackClick,
        onDossierClick = {
            showDossierSheet = true
            profile?.let { p -> onDossierClick?.invoke(p.id) }
        },
        onSendMessage = { text, attachments ->
            viewModel.sendMessage(text, attachments)
        }
    )

    if (showDossierSheet) {
        DossierIntelligenceBottomSheet(
            displayTitle = displayTitle,
            address = viewModel.address,
            profile = profile,
            messages = messages,
            onDismiss = { showDossierSheet = false },
            onSaveNotes = { notes ->
                viewModel.saveProfileNotes(notes)
            },
            onInjectScript = { scriptText ->
                messageText = scriptText
                showDossierSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationContent(
    displayTitle: String,
    address: String,
    messages: List<SmsMessage>,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onDossierClick: () -> Unit,
    onSendMessage: (String, List<MediaAttachment>) -> Unit
) {
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

            GeminiMessageComposer(
                text = messageText,
                onTextChange = onMessageTextChange,
                onSendMessage = onSendMessage
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (message.attachments.isNotEmpty()) {
                        message.attachments.forEach { attachment ->
                            MessageAttachmentItem(attachment = attachment)
                        }
                    }
                    if (message.body.isNotBlank()) {
                        Text(
                            text = message.body,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
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
fun MessageAttachmentItem(attachment: MediaAttachment) {
    when (attachment.type) {
        AttachmentType.IMAGE -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AttachmentThumbnail(
                    attachment = attachment,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        AttachmentType.VIDEO, AttachmentType.FILE -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (attachment.type == AttachmentType.VIDEO) Icons.Rounded.Videocam else Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
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
fun GeminiMessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSendMessage: (String, List<MediaAttachment>) -> Unit
) {
    val context = LocalContext.current
    var attachments by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }

    // Launchers
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val photoName = "Photo_${SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())}.jpg"
            attachments = attachments + MediaAttachment(
                bitmap = bitmap,
                name = photoName,
                type = AttachmentType.IMAGE,
                mimeType = "image/jpeg"
            )
            Toast.makeText(context, "Captured photo attached", Toast.LENGTH_SHORT).show()
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val attachment = getAttachmentFromUri(context, uri, AttachmentType.IMAGE)
            attachments = attachments + attachment
            Toast.makeText(context, "Media attached", Toast.LENGTH_SHORT).show()
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val attachment = getAttachmentFromUri(context, uri, AttachmentType.FILE)
            attachments = attachments + attachment
            Toast.makeText(context, "File attached from SAF/Drive", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Attachment Preview Area
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        AttachmentPreviewChip(
                            attachment = attachment,
                            onRemove = {
                                attachments = attachments.filter { a -> a.id != attachment.id }
                            }
                        )
                    }
                }
            }

            // Gemini Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Camera Button
                IconButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "In-Thread Camera",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Media Picker Button
                IconButton(
                    onClick = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "Local Media Picker",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // SAF Cloud / Drive Access Button
                IconButton(
                    onClick = { safLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = "SAF Cloud Drive Access",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Text Box
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { Text("Ask Gemini or type message...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                // Send Button
                FilledIconButton(
                    onClick = {
                        if (text.isNotBlank() || attachments.isNotEmpty()) {
                            onSendMessage(text, attachments)
                            onTextChange("")
                            attachments = emptyList()
                        }
                    },
                    enabled = text.isNotBlank() || attachments.isNotEmpty(),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewChip(
    attachment: MediaAttachment,
    onRemove: () -> Unit
) {
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier.size(width = 84.dp, height = 72.dp)
        ) {
            if (attachment.type == AttachmentType.IMAGE) {
                AttachmentThumbnail(
                    attachment = attachment,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (attachment.type == AttachmentType.VIDEO) Icons.Rounded.Videocam else Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Close Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(22.dp)
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cancel,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AttachmentThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadedBitmap by remember(attachment) { mutableStateOf<ImageBitmap?>(attachment.bitmap?.asImageBitmap()) }

    LaunchedEffect(attachment) {
        if (loadedBitmap == null && attachment.uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(attachment.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        val bmp = BitmapFactory.decodeStream(stream, null, options)
                        loadedBitmap = bmp?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap!!,
            contentDescription = attachment.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (attachment.type) {
                    AttachmentType.IMAGE -> Icons.Rounded.Image
                    AttachmentType.VIDEO -> Icons.Rounded.Videocam
                    AttachmentType.FILE -> Icons.AutoMirrored.Rounded.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getAttachmentFromUri(context: Context, uri: Uri, defaultType: AttachmentType): MediaAttachment {
    var fileName = "Attachment"
    val mimeType: String? = context.contentResolver.getType(uri)
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val foundName = cursor.getString(nameIndex)
                if (!foundName.isNullOrBlank()) {
                    fileName = foundName
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val type = when {
        mimeType?.startsWith("image/") == true -> AttachmentType.IMAGE
        mimeType?.startsWith("video/") == true -> AttachmentType.VIDEO
        fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true) -> AttachmentType.IMAGE
        fileName.endsWith(".mp4", ignoreCase = true) || fileName.endsWith(".mkv", ignoreCase = true) -> AttachmentType.VIDEO
        else -> defaultType
    }

    return MediaAttachment(
        uri = uri,
        name = fileName,
        type = type,
        mimeType = mimeType
    )
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
            messageText = "",
            onMessageTextChange = {},
            onBackClick = {},
            onDossierClick = {},
            onSendMessage = { _, _ -> }
        )
    }
}
