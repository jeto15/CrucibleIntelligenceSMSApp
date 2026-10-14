package org.fossify.messages.dialogs

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadConfiguration
import org.fossify.messages.ui.CrucibleDossierBottomSheet

class CrucibleDossierDialog(
    private val activity: Activity,
    private val threadId: Long,
    private val displayTitle: String,
    private val phoneNumber: String,
    private val messages: List<Message>,
    private val initialConfig: ThreadConfiguration?,
    private val onSaveConfig: (ThreadConfiguration) -> Unit,
    private val onInjectScript: ((String) -> Unit)? = null
) {
    private var dialog: BottomSheetDialog? = null

    fun show() {
        dialog = BottomSheetDialog(activity).apply {
            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    MaterialTheme {
                        CrucibleDossierBottomSheet(
                            threadId = threadId,
                            displayTitle = displayTitle,
                            phoneNumber = phoneNumber,
                            messages = messages,
                            initialConfig = initialConfig,
                            onSaveConfig = { config ->
                                onSaveConfig(config)
                            },
                            onDismiss = {
                                dismiss()
                            },
                            onInjectScript = { script ->
                                onInjectScript?.invoke(script)
                            }
                        )
                    }
                }
            }
            setContentView(composeView)
            setOnShowListener {
                val bottomSheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
        dialog?.show()
    }
}
