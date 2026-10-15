package org.fossify.messages.ui

import androidx.compose.runtime.Composable
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadConfiguration

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
    DossierBottomSheetContent(
        threadId = threadId,
        displayTitle = displayTitle,
        phoneNumber = phoneNumber,
        messages = messages,
        initialConfig = initialConfig,
        onSaveConfig = onSaveConfig,
        onDismiss = onDismiss,
        onInjectScript = { script ->
            onInjectScript?.invoke(script)
        }
    )
}
