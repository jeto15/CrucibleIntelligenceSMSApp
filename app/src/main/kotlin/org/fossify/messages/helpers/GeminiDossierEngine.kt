package org.fossify.messages.helpers

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fossify.messages.models.Message
import java.util.Locale

@Serializable
data class DossierReportResult(
    val score: Int,
    val scoreJustification: String,
    val behavioralSymptomAlignment: String,
    val personalityProfile: String,
    val compatibilityScore: Double,
    val attachmentStyleAssessment: String,
    val strategicSuggestedReply: String,
    val alternativeReplies: List<String> = emptyList(),
    val promptTokens: Int = 0,
    val candidateTokens: Int = 0
) {
    val estimatedCost: Double
        get() = (promptTokens * 4.0 / 1_000_000.0) + (candidateTokens * 20.0 / 1_000_000.0)

    val formattedTelemetry: String
        get() = "TOKENS: $promptTokens IN / $candidateTokens OUT | EST. API COST: $${String.format(Locale.US, "%.4f", estimatedCost)}"
}

object GeminiDossierEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun generateDossierReport(
        apiKey: String,
        contactName: String,
        genderEnergy: String,
        relationshipType: String,
        relationshipGoal: String,
        messages: List<Message>
    ): Result<DossierReportResult> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Gemini API key is not configured. Please enter your API Key via the Key icon in the Dossier drawer."))
            }

            // Up to last 40 messages in chronological order
            val recentMessages = messages.takeLast(40)
            val totalMessagesCount = messages.size
            val incomingCount = recentMessages.count { it.isReceivedMessage() }
            val outgoingCount = recentMessages.count { !it.isReceivedMessage() }
            val incomingRatioPct = if (recentMessages.isNotEmpty()) (incomingCount * 100 / recentMessages.size) else 50

            val conversationText = buildString {
                if (recentMessages.isEmpty()) {
                    append("[No messages recorded in thread]\n")
                } else {
                    recentMessages.forEach { msg ->
                        val sender = if (msg.isReceivedMessage()) contactName else "User (Me)"
                        append("[").append(msg.date).append("] ")
                        append(sender).append(": ").append(msg.body).append("\n")
                    }
                }
            }

            val prompt = """
                You are the CRUCIBLE INTELLIGENCE DOSSIER AI Engine.
                Analyze the provided conversation transcript and output a single strictly valid JSON object matching the requested schema.

                CONTEXT & LOCAL METRICS:
                - Target Subject: $contactName
                - Gender Energy Framework: $genderEnergy
                - Relationship Type: $relationshipType
                - Relationship Goal: $relationshipGoal
                - Local Exchange Metrics: $totalMessagesCount total thread messages ($incomingCount Incoming / $outgoingCount Outgoing, $incomingRatioPct% Incoming Ratio in recent exchange window).

                SCORING SYSTEM — ASSET / LIABILITY SCORE (0–99):
                - Score must be an integer between 0 and 99 ONLY. Score 100 is PROHIBITED.
                - 0 to 50 = LIABILITY (0 represents maximum assessed risk; 50 is the lowest-risk score in the liability category).
                - 51 to 99 = ASSET (51 is the baseline asset score; 99 represents maximum assessed asset value).
                - Provide exactly ONE sentence in `scoreJustification` explaining the score with direct reference to observable communication evidence.

                FOUR REQUIRED SECTIONS:

                SECTION 1 — DSM-5-TR Behavioral Symptom Alignment:
                Evaluate observable communication patterns that may resemble:
                - Provocation or baiting
                - Stonewalling or prolonged disengagement
                - Guilt-inducing communication
                - Triangulation indicators
                - Emotional escalation
                CRITICAL: Distinguish observations from interpretations. Explain uncertainty and alternative explanations. Do not diagnose personality disorders based on text messages alone.

                SECTION 2 — Personality Profile & Compatibility (0–10):
                Provide tentative estimates of possible MBTI preferences and Big Five trait tendencies when sufficient evidence exists.
                Generate a Compatibility Score from 0 to 10 with a brief explanation. Do not invent confident personality conclusions from insufficient evidence.

                SECTION 3 — Attachment Style Assessment & User Education:
                Evaluate possible attachment patterns (Secure, Anxious-Preoccupied, Dismissive-Avoidant, or Fearful-Avoidant).
                Explain the possible relationship between observed communication patterns and the selected interpretation, including uncertainty and alternative explanations.

                SECTION 4 — Strategic Suggested Reply:
                Generate a suggested reply or recommend not responding when appropriate, applying ALL FOUR GUARDRAILS:
                - Guardrail 1 (Biblical Wisdom): Emphasize truthfulness, integrity, discernment, respect, and appropriate boundaries.
                - Guardrail 2 (Lane A / Lane B): Separate emotional conflict (Lane A) from practical logistics (Lane B). Keep necessary logistical communication concise and factual.
                - Guardrail 3 (Gender Energy): Apply the selected $genderEnergy framework.
                  * Masculine: Direct communication, purpose, grounded leadership, decisive action, appropriate boundaries (Corey Wayne 3% Man).
                  * Feminine: Warmth, receptivity, patience, maintaining standards, avoiding chasing or controlling (Rori Raye & Adrienne Everheart).
                  * Dual-Energy: Analyze conversation context and provide BOTH a Grounded Leadership reply AND a Receptive Lean-Back reply.
                - Guardrail 4 (Marcus "Elevation" Taylor Mindset): Emphasize self-mastery, discipline, purposeful action, personal growth, and avoiding destructive communication cycles.

                CONVERSATION TRANSCRIPT (Last ${recentMessages.size} messages):
                $conversationText

                OUTPUT FORMAT:
                Return ONLY a JSON object with this exact structure:
                {
                  "score": <INTEGER 0-99>,
                  "scoreJustification": "<ONE SENTENCE JUSTIFICATION>",
                  "behavioralSymptomAlignment": "<DETAILED ANALYSIS FOR SECTION 1>",
                  "personalityProfile": "<DETAILED ANALYSIS FOR SECTION 2>",
                  "compatibilityScore": <NUMBER 0-10>,
                  "attachmentStyleAssessment": "<DETAILED ANALYSIS FOR SECTION 3>",
                  "strategicSuggestedReply": "<STRATEGIC SUGGESTED REPLY FOR SECTION 4>",
                  "alternativeReplies": ["<OPTIONAL ALTERNATIVE REPLY 1>", "<OPTIONAL ALTERNATIVE REPLY 2>"]
                }
            """.trimIndent()

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                }
            )

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: error("Empty response received from Gemini model.")

            val promptTokens = response.usageMetadata?.promptTokenCount ?: 0
            val candidateTokens = response.usageMetadata?.candidatesTokenCount ?: 0

            val parsedJson = extractJson(responseText)
            val decoded = json.decodeFromString<DossierReportResult>(parsedJson)

            val finalResult = decoded.copy(
                score = decoded.score.coerceIn(0, 99),
                promptTokens = promptTokens,
                candidateTokens = candidateTokens
            )

            Result.success(finalResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed

        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return trimmed.substring(startIndex, endIndex + 1)
        }
        return trimmed
    }
}
