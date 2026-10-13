package com.example.crucibleintelligencesmsapp.ui.chat

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.repository.SmsMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DossierIntelligenceBottomSheet(
    displayTitle: String,
    address: String,
    profile: EntityProfile?,
    messages: List<SmsMessage>,
    onDismiss: () -> Unit,
    onSaveNotes: ((String) -> Unit)? = null,
    onInjectScript: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val totalMessages = messages.size
    val incomingCount = messages.count { it.isIncoming }
    val outgoingCount = messages.count { !it.isIncoming }
    val incomingRatio = if (totalMessages > 0) incomingCount.toFloat() / totalMessages else 0.5f

    val minTimestamp = messages.minOfOrNull { it.timestamp } ?: 0L
    val maxTimestamp = messages.maxOfOrNull { it.timestamp } ?: 0L

    val firstContactStr = if (minTimestamp > 0) formatDossierDate(minTimestamp) else "No messages"
    val latestContactStr = if (maxTimestamp > 0) formatDossierDate(maxTimestamp) else "No messages"

    val themes = remember(messages) { analyzeThemes(messages) }
    val sentiment = remember(messages) { analyzeSentiment(messages) }

    var generatedReportText by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text = "Crucible Dossier Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Communication Analysis & Intelligence POC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 1. Contact Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val firstChar = displayTitle.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (firstChar.isNotEmpty() && firstChar != "?") {
                            Text(
                                text = firstChar,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (address.isNotBlank() && address != displayTitle) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val badgeLabel = when {
                                totalMessages > 10 -> "High Activity Contact"
                                profile?.name != null -> "Verified Contact"
                                else -> "Monitored Target"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = badgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Communication Metrics
            Text(
                text = "COMMUNICATION METRICS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStatItem(label = "Total Messages", value = totalMessages.toString())
                        MetricStatItem(label = "Incoming", value = incomingCount.toString())
                        MetricStatItem(label = "Outgoing", value = outgoingCount.toString())
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Message Breakdown (${(incomingRatio * 100).toInt()}% IN / ${((1f - incomingRatio) * 100).toInt()}% OUT)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { incomingRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.tertiaryContainer
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "First Contact",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = firstContactStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Latest Contact",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = latestContactStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Voice Note / Audio Playback
            Text(
                text = "VOICE NOTE INTELLIGENCE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            DossierVoiceNotePlayer(context = context)

            // 4. AI / Keyword Analysis POC
            Text(
                text = "AI / KEYWORD ANALYSIS POC",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Key Themes Detected",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEach { theme ->
                            AssistChip(
                                onClick = {},
                                label = { Text(theme) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sentiment Gauge",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = sentiment,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Risk Level",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Low Risk",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Manual Context Feedback Box
            Text(
                text = "MANUAL DOSSIER INTELLIGENCE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            var manualNotes by remember(profile) { mutableStateOf(profile?.notes ?: "") }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = manualNotes,
                        onValueChange = { manualNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom Background Notes & Context") },
                        placeholder = { Text("Add manual intelligence background notes for this contact...") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            onSaveNotes?.invoke(manualNotes)
                            Toast.makeText(context, "Manual context note saved to profile", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Intelligence Note")
                    }
                }
            }

            // 6. "Execute Script" AI Response Injector Menu
            Text(
                text = "EXECUTE AI RESPONSE SCRIPT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Text(
                text = "Select a quick AI script to inject directly into the composer input field:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val scripts = listOf(
                Triple(
                    "Draft Formal Reply",
                    Icons.Rounded.Description,
                    "Dear $displayTitle,\n\nThank you for reaching out. I have reviewed the details and will proceed accordingly.\n\nBest regards,"
                ),
                Triple(
                    "Request Clarification",
                    Icons.Rounded.HelpOutline,
                    "Could you please provide additional details or clarification regarding your previous message? Standing by for your update."
                ),
                Triple(
                    "Acknowledge & Confirm",
                    Icons.Rounded.CheckCircleOutline,
                    "Acknowledged and confirmed. Proceeding with the requested action immediately."
                ),
                Triple(
                    "Escalate Priority",
                    Icons.Rounded.PriorityHigh,
                    "CRITICAL UPDATE: Priority level escalated for this thread. Immediate review in progress."
                )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scripts.forEach { (title, icon, text) ->
                    OutlinedCard(
                        onClick = {
                            onInjectScript?.invoke(text)
                            Toast.makeText(context, "Injected '$title' into composer", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (text.length > 60) text.take(60) + "..." else text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 7. Generate & Export/Share Dossier Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isGenerating = true
                        val reportId = (address.hashCode().absoluteValue % 900000 + 100000)
                        val reportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                        generatedReportText = """
                            ===================================================
                            CRUCIBLE DOSSIER INTELLIGENCE REPORT
                            ===================================================
                            REPORT ID  : #CDR-$reportId
                            DATE       : $reportDate
                            SUBJECT    : $displayTitle
                            ADDRESS    : $address
                            ---------------------------------------------------
                            INTERACTION SUMMARY:
                            - Total Exchanges : $totalMessages
                            - Incoming        : $incomingCount
                            - Outgoing        : $outgoingCount
                            - First Contact   : $firstContactStr
                            - Latest Contact  : $latestContactStr
                            
                            INTELLIGENCE ANALYSIS:
                            - Detected Themes : ${themes.joinToString(", ")}
                            - Sentiment Gauge : $sentiment
                            - Threat Risk     : Low Risk
                            
                            MANUAL NOTES:
                            ${if (manualNotes.isNotBlank()) manualNotes else "None recorded."}
                            
                            ASSESSMENT BRIEF:
                            Subject ($displayTitle) maintains an active communication link.
                            Exchanges demonstrate normal operational messaging patterns.
                            No threat indicators or security anomalies detected.
                            ===================================================
                        """.trimIndent()
                        isGenerating = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Dossier", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val reportId = (address.hashCode().absoluteValue % 900000 + 100000)
                        val reportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val reportToShare = generatedReportText ?: """
                            CRUCIBLE DOSSIER BRIEF: $displayTitle ($address)
                            Date: $reportDate (Ref #CDR-$reportId)
                            Exchanges: $totalMessages ($incomingCount IN / $outgoingCount OUT)
                            Themes: ${themes.joinToString(", ")}
                            Sentiment: $sentiment
                            Manual Notes: ${if (manualNotes.isNotBlank()) manualNotes else "N/A"}
                        """.trimIndent()

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Crucible Dossier Brief: $displayTitle")
                            putExtra(Intent.EXTRA_TEXT, reportToShare)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Dossier Brief"))
                    },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Export / Share Dossier"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            generatedReportText?.let { reportText ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Generated Dossier Brief",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(reportText))
                                    Toast.makeText(context, "Intelligence Dossier copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Dossier Brief"
                                )
                            }
                        }

                        Text(
                            text = reportText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(reportText))
                                    Toast.makeText(context, "Intelligence Dossier copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Brief")
                            }

                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Crucible Dossier Brief: $displayTitle")
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Dossier Brief"))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export / Share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DossierVoiceNotePlayer(context: Context) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    val totalDurationMs = 30000f

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(context) {
        val audioFile = getOrCreateSampleAudioFile(context)
        val player = try {
            MediaPlayer.create(context, Uri.fromFile(audioFile))?.apply {
                isLooping = true
            }
        } catch (e: Exception) {
            null
        }
        mediaPlayer = player

        onDispose {
            player?.stop()
            player?.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            mediaPlayer?.start()
            while (isActive && isPlaying) {
                delay(100)
                currentPositionMs = (currentPositionMs + 100f) % totalDurationMs
            }
        } else {
            mediaPlayer?.pause()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Dossier Voice Briefing #042",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Recorded Audio Intelligence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Audio Waveform Visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val barCount = 20
                for (i in 0 until barCount) {
                    val baseHeight = if (i % 3 == 0) 24f else if (i % 2 == 0) 16f else 10f
                    val animatedFactor = if (isPlaying) {
                        (sin((currentPositionMs / 200f) + i) * 8f + 12f).coerceIn(4f, 28f)
                    } else {
                        baseHeight
                    }
                    val heightDp by animateFloatAsState(
                        targetValue = animatedFactor,
                        animationSpec = tween(durationMillis = 100),
                        label = "waveform"
                    )

                    val isPlayed = (i.toFloat() / barCount) <= (currentPositionMs / totalDurationMs)
                    val barColor = if (isPlayed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(heightDp.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }

            // Controls & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                Slider(
                    value = currentPositionMs,
                    onValueChange = {
                        currentPositionMs = it
                        mediaPlayer?.seekTo(it.toInt())
                    },
                    valueRange = 0f..totalDurationMs,
                    modifier = Modifier.weight(1f)
                )

                val currentSec = (currentPositionMs / 1000).toInt()
                val totalSec = (totalDurationMs / 1000).toInt()
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getOrCreateSampleAudioFile(context: Context): File {
    val file = File(context.cacheDir, "sample_dossier_briefing.wav")
    if (file.exists() && file.length() > 0) return file

    try {
        val sampleRate = 22050
        val durationSeconds = 30
        val numSamples = sampleRate * durationSeconds
        val pcmData = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val angle = 2.0 * Math.PI * 440.0 * t
            val sample = (sin(angle) * 6000.0).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0x00FF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0x00FF).toByte()
        }

        FileOutputStream(file).use { out ->
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2

            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalDataLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16))
            out.write(shortToByteArray(1.toShort()))
            out.write(shortToByteArray(1.toShort()))
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray(2.toShort()))
            out.write(shortToByteArray(16.toShort()))
            out.write("data".toByteArray())
            out.write(intToByteArray(pcmData.size))
            out.write(pcmData)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return file
}

private fun intToByteArray(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )
}

private fun shortToByteArray(value: Short): ByteArray {
    return byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}

@Composable
private fun MetricStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDossierDate(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun analyzeThemes(messages: List<SmsMessage>): List<String> {
    if (messages.isEmpty()) {
        return listOf("Schedules", "Operational Comms", "General Inquiries")
    }
    val themes = mutableListOf<String>()
    val allText = messages.joinToString(" ") { it.body.lowercase() }

    if (allText.contains("meet") || allText.contains("schedule") || allText.contains("today") ||
        allText.contains("tomorrow") || allText.contains("time") || allText.contains("clock") ||
        allText.contains("call") || allText.contains("lunch") || allText.contains("appointment")) {
        themes.add("Schedules & Logistics")
    }
    if (allText.contains("money") || allText.contains("pay") || allText.contains("bank") ||
        allText.contains("card") || allText.contains("price") || allText.contains("cost") ||
        allText.contains("dollar") || allText.contains("payment") || allText.contains("invoice")) {
        themes.add("Financials")
    }
    if (allText.contains("urgent") || allText.contains("asap") || allText.contains("now") ||
        allText.contains("help") || allText.contains("alert") || allText.contains("critical") ||
        allText.contains("important") || allText.contains("emergency") || allText.contains("quick")) {
        themes.add("Urgent Requests")
    }
    if (allText.contains("project") || allText.contains("work") || allText.contains("doc") ||
        allText.contains("code") || allText.contains("report") || allText.contains("task") ||
        allText.contains("file")) {
        themes.add("Operations")
    }
    if (allText.contains("location") || allText.contains("address") || allText.contains("map") ||
        allText.contains("where") || allText.contains("place") || allText.contains("here")) {
        themes.add("Geospatial")
    }

    if (themes.isEmpty()) {
        themes.add("General Communication")
        themes.add("Operational Directives")
    }
    return themes
}

private fun analyzeSentiment(messages: List<SmsMessage>): String {
    if (messages.isEmpty()) return "Neutral"
    val allText = messages.joinToString(" ") { it.body.lowercase() }
    return when {
        allText.contains("urgent") || allText.contains("asap") || allText.contains("help") || allText.contains("alert") -> "Urgent / Action Required"
        allText.contains("thanks") || allText.contains("great") || allText.contains("good") || allText.contains("awesome") || allText.contains("perfect") -> "Positive"
        else -> "Neutral"
    }
}
