package org.fossify.messages.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.fossify.messages.helpers.DossierReportResult
import org.fossify.messages.helpers.GeminiDossierEngine
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CrucibleDossierBottomSheet(
    threadId: Long,
    displayTitle: String,
    phoneNumber: String,
    messages: List<Message>,
    initialConfig: ThreadConfiguration?,
    onSaveConfig: (ThreadConfiguration) -> Unit,
    onDismiss: () -> Unit,
    onInjectScript: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Config state
    var genderEnergy by remember { mutableStateOf(initialConfig?.genderEnergy ?: "Masculine") }
    var relationshipType by remember { mutableStateOf(initialConfig?.relationshipType ?: "Dating/Romantic") }
    var relationshipGoal by remember { mutableStateOf(initialConfig?.relationshipGoal ?: "Build Attraction") }
    var currentScore by remember { mutableStateOf(initialConfig?.score) }
    var scoreJustification by remember { mutableStateOf(initialConfig?.scoreJustification) }

    // Dialog flags
    var showGenderModal by remember { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }

    // API Key State (stored in SharedPreferences)
    val sharedPrefs = remember { context.getSharedPreferences("crucible_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }

    // Gemini Execution State
    var isGenerating by remember { mutableStateOf(false) }
    var generatedReportResult by remember { mutableStateOf<DossierReportResult?>(null) }
    var generatedReportText by remember { mutableStateOf<String?>(initialConfig?.dossierReportJson) }

    // Metrics
    val totalMessages = messages.size
    val incomingCount = messages.count { it.isReceivedMessage() }
    val outgoingCount = messages.count { !it.isReceivedMessage() }
    val incomingRatio = if (totalMessages > 0) incomingCount.toFloat() / totalMessages else 0.5f

    val minTimestamp = messages.minOfOrNull { it.date * 1000L } ?: 0L
    val maxTimestamp = messages.maxOfOrNull { it.date * 1000L } ?: 0L

    val firstContactStr = if (minTimestamp > 0) SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(minTimestamp)) else "No messages"
    val latestContactStr = if (maxTimestamp > 0) SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(maxTimestamp)) else "No messages"

    fun persistConfig(newResult: DossierReportResult? = null) {
        val updated = ThreadConfiguration(
            threadId = threadId,
            genderEnergy = genderEnergy,
            relationshipType = relationshipType,
            relationshipGoal = relationshipGoal,
            score = newResult?.score ?: currentScore,
            scoreJustification = newResult?.scoreJustification ?: scoreJustification,
            dossierReportJson = generatedReportText,
            promptTokenCount = newResult?.promptTokens ?: (initialConfig?.promptTokenCount ?: 0),
            candidatesTokenCount = newResult?.candidateTokens ?: (initialConfig?.candidatesTokenCount ?: 0),
            updatedTimestamp = System.currentTimeMillis()
        )
        onSaveConfig(updated)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag handle at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
            // Header Bar
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Crucible Intelligence Dossier",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "AI Communication Analysis & Intelligence System",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showApiKeyModal = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = "Configure API Key",
                        tint = if (apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 1. Contact Overview Card with Score Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstChar,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (phoneNumber.isNotBlank() && phoneNumber != displayTitle) {
                            Text(
                                text = phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Score Badge Composable
                    AssetLiabilityBadge(score = currentScore)
                }
            }

            // 2. Per-Thread Controls Section
            Text(
                text = "THREAD INTELLIGENCE CONFIGURATION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // A. Gender Energy Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Gender Energy Framework",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Gender Energy Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showGenderModal = true }
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("Masculine", "Feminine", "Dual-Energy")
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = genderEnergy == option,
                                onClick = {
                                    genderEnergy = option
                                    persistConfig()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) {
                                Text(text = option, fontSize = 12.sp)
                            }
                        }
                    }

                    // B. Relationship Type Dropdown
                    var relTypeExpanded by remember { mutableStateOf(false) }
                    val relTypeOptions = listOf("Dating/Romantic", "Co-Parenting/Ex", "Business", "Family", "Friend")

                    ExposedDropdownMenuBox(
                        expanded = relTypeExpanded,
                        onExpandedChange = { relTypeExpanded = !relTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = relationshipType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relTypeExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = relTypeExpanded,
                            onDismissRequest = { relTypeExpanded = false }
                        ) {
                            relTypeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        relationshipType = option
                                        relTypeExpanded = false
                                        persistConfig()
                                    }
                                )
                            }
                        }
                    }

                    // C. Relationship Goal Dropdown
                    var relGoalExpanded by remember { mutableStateOf(false) }
                    val relGoalOptions = listOf(
                        "Build Attraction",
                        "Maintain Strict Boundaries / Parallel Parenting",
                        "Close a Deal",
                        "Evaluate Character"
                    )

                    ExposedDropdownMenuBox(
                        expanded = relGoalExpanded,
                        onExpandedChange = { relGoalExpanded = !relGoalExpanded }
                    ) {
                        OutlinedTextField(
                            value = relationshipGoal,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship Goal") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relGoalExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = relGoalExpanded,
                            onDismissRequest = { relGoalExpanded = false }
                        ) {
                            relGoalOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        relationshipGoal = option
                                        relGoalExpanded = false
                                        persistConfig()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Local Communication Metrics
            Text(
                text = "COMMUNICATION METRICS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = totalMessages.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Total Messages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = incomingCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Incoming", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = outgoingCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Outgoing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        text = "Message Ratio (${(incomingRatio * 100).toInt()}% IN / ${((1f - incomingRatio) * 100).toInt()}% OUT)",
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "First: $firstContactStr", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Latest: $latestContactStr", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 4. Generate Dossier Action Button
            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        showApiKeyModal = true
                        return@Button
                    }
                    isGenerating = true
                    coroutineScope.launch {
                        val result = GeminiDossierEngine.generateDossierReport(
                            apiKey = apiKey,
                            contactName = displayTitle,
                            genderEnergy = genderEnergy,
                            relationshipType = relationshipType,
                            relationshipGoal = relationshipGoal,
                            messages = messages
                        )
                        isGenerating = false
                        result.onSuccess { report ->
                            generatedReportResult = report
                            currentScore = report.score
                            scoreJustification = report.scoreJustification

                            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            generatedReportText = """
                                ===================================================
                                CRUCIBLE INTELLIGENCE DOSSIER REPORT
                                ===================================================
                                DATE       : $dateStr
                                SUBJECT    : $displayTitle ($phoneNumber)
                                SCORE      : ${report.score} / 99 (${if (report.score <= 50) "LIABILITY" else "ASSET"})
                                REASON     : ${report.scoreJustification}
                                ---------------------------------------------------
                                SECTION 1 — DSM-5-TR BEHAVIORAL SYMPTOM ALIGNMENT
                                ${report.behavioralSymptomAlignment}

                                SECTION 2 — PERSONALITY PROFILE & COMPATIBILITY
                                Score: ${report.compatibilityScore}/10
                                ${report.personalityProfile}

                                SECTION 3 — ATTACHMENT STYLE ASSESSMENT
                                ${report.attachmentStyleAssessment}

                                SECTION 4 — STRATEGIC SUGGESTED REPLY
                                ${report.strategicSuggestedReply}
                                ===================================================
                            """.trimIndent()

                            persistConfig(report)
                        }.onFailure { err ->
                            Toast.makeText(context, "Dossier Error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Generate Intelligence Dossier", fontWeight = FontWeight.Bold)
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

            // 5. Rendered Generated Report Card & Highlighted Intelligence Sections
            generatedReportText?.let { reportText ->
                val section1 = generatedReportResult?.behavioralSymptomAlignment
                    ?: parseReportSection(reportText, "SECTION 1 — DSM-5-TR BEHAVIORAL SYMPTOM ALIGNMENT", "SECTION 2 — PERSONALITY PROFILE & COMPATIBILITY")
                val section2 = generatedReportResult?.personalityProfile
                    ?: parseReportSection(reportText, "SECTION 2 — PERSONALITY PROFILE & COMPATIBILITY", "SECTION 3 — ATTACHMENT STYLE ASSESSMENT")
                val section3 = generatedReportResult?.attachmentStyleAssessment
                    ?: parseReportSection(reportText, "SECTION 3 — ATTACHMENT STYLE ASSESSMENT", "SECTION 4 — STRATEGIC SUGGESTED REPLY")
                val section4 = generatedReportResult?.strategicSuggestedReply
                    ?: parseReportSection(reportText, "SECTION 4 — STRATEGIC SUGGESTED REPLY", "===================================================")

                Text(
                    text = "DOSSIER INTELLIGENCE REPORT",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                // A. Justification & Score Callout
                scoreJustification?.let { justification ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = justification,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // B. Section 4 Highlight: Strategic Suggested Reply & Composer Inject Button
                section4?.let { reply ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
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
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "STRATEGIC SUGGESTED REPLY",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = reply,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = {
                                    onInjectScript?.invoke(reply)
                                    Toast.makeText(context, "Injected reply into composer", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inject Suggested Reply into Composer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // C. Alternative Suggested Replies (if available)
                generatedReportResult?.alternativeReplies?.filter { it.isNotBlank() }?.let { alternatives ->
                    if (alternatives.isNotEmpty()) {
                        Text(
                            text = "ALTERNATIVE SUGGESTED REPLIES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        alternatives.forEachIndexed { idx, altReply ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Option ${idx + 1}: $altReply",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onInjectScript?.invoke(altReply)
                                            Toast.makeText(context, "Injected reply option into composer", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Inject Option ${idx + 1}")
                                    }
                                }
                            }
                        }
                    }
                }

                // D. Section 1 Highlight: DSM-5 Behavioral Symptom Alignment
                section1?.let { sec1 ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "DSM-5-TR Behavioral Symptom Alignment",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = sec1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // E. Section 2 Highlight: Personality Profile & Compatibility
                section2?.let { sec2 ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Personality Profile & Compatibility",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = sec2,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // F. Section 3 Highlight: Attachment Style Assessment
                section3?.let { sec3 ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Attachment Style Assessment",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = sec3,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // G. Cost & Token Telemetry (if available)
                generatedReportResult?.formattedTelemetry?.let { telemetry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = telemetry,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // H. Raw Brief & Copy Option
                var showRawBrief by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Icon(imageVector = Icons.Rounded.Description, contentDescription = null)
                                Text(
                                    text = "Raw Dossier Text",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                TextButton(onClick = { showRawBrief = !showRawBrief }) {
                                    Text(if (showRawBrief) "Hide Raw" else "View Raw")
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(reportText))
                                    Toast.makeText(context, "Dossier copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }

                        if (showRawBrief) {
                            Text(
                                text = reportText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Gender Energy Info Modal
    if (showGenderModal) {
        AlertDialog(
            onDismissRequest = { showGenderModal = false },
            title = { Text("Gender Energy Frameworks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Masculine Energy:", fontWeight = FontWeight.Bold)
                    Text(text = "Direct communication, purpose, grounded leadership, decisive action, and appropriate boundaries. Reference framework: Coach Corey Wayne's 3% Man.")

                    Text(text = "Feminine Energy:", fontWeight = FontWeight.Bold)
                    Text(text = "Warmth, receptivity, patience, maintaining standards, and avoiding chasing or controlling. Reference frameworks: Rori Raye and Adrienne Everheart.")

                    Text(text = "Dual-Energy:", fontWeight = FontWeight.Bold)
                    Text(text = "Analyzes available context and provides both a Grounded Leadership reply and a Receptive Lean-Back reply.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showGenderModal = false }) { Text("Close") }
            }
        )
    }

    // API Key Configuration Modal
    if (showApiKeyModal) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyModal = false },
            title = { Text("Configure Gemini API Key") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    apiKey = tempKey.trim()
                    sharedPrefs.edit().putString("gemini_api_key", apiKey).apply()
                    showApiKeyModal = false
                    Toast.makeText(context, "API Key saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyModal = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AssetLiabilityBadge(score: Int?) {
    if (score == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Not Assessed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val sanitizedScore = score.coerceIn(0, 99)
    val isAsset = sanitizedScore >= 51
    val label = if (isAsset) "ASSET $sanitizedScore/99" else "LIABILITY $sanitizedScore/99"
    val bgColor = if (isAsset) Color(0xFF1E3A2B) else Color(0xFF3E1E1E)
    val textColor = if (isAsset) Color(0xFF81C784) else Color(0xFFE57373)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isAsset) Icons.Rounded.Verified else Icons.Rounded.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun parseReportSection(reportText: String, sectionHeader: String, nextHeader: String?): String? {
    val startIdx = reportText.indexOf(sectionHeader)
    if (startIdx == -1) return null
    val contentStart = startIdx + sectionHeader.length
    val endIdx = if (nextHeader != null) {
        val nIdx = reportText.indexOf(nextHeader, contentStart)
        if (nIdx != -1) nIdx else reportText.length
    } else {
        val dividerIdx = reportText.indexOf("===================================================", contentStart)
        if (dividerIdx != -1) dividerIdx else reportText.length
    }
    return reportText.substring(contentStart, endIdx).trim().ifBlank { null }
}
