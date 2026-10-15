package org.fossify.messages.dialogs

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w("CrucibleDossierDialog", "Activity is finishing or destroyed, skipping show()")
            return
        }

        try {
            dialog = BottomSheetDialog(activity).apply {
                val composeView = ComposeView(activity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setContent {
                        MaterialTheme {
                            CrucibleDossierBottomSheet(
                                threadId = threadId,
                                displayTitle = displayTitle,
                                phoneNumber = phoneNumber,
                                messages = messages,
                                initialConfig = initialConfig,
                                onSaveConfig = { config ->
                                    try {
                                        onSaveConfig(config)
                                    } catch (e: Exception) {
                                        Log.e("CrucibleDossierDialog", "Error saving config", e)
                                    }
                                },
                                onDismiss = {
                                    try {
                                        dismiss()
                                    } catch (e: Exception) {
                                        Log.e("CrucibleDossierDialog", "Error dismissing dialog", e)
                                    }
                                },
                                onInjectScript = { script ->
                                    try {
                                        onInjectScript?.invoke(script)
                                    } catch (e: Exception) {
                                        Log.e("CrucibleDossierDialog", "Error injecting script", e)
                                    }
                                }
                            )
                        }
                    }
                }
                setContentView(composeView)
                setOnShowListener {
                    try {
                        val bottomSheet = findViewById<FrameLayout>(R.id.design_bottom_sheet)
                        bottomSheet?.let { sheet ->
                            val behavior = BottomSheetBehavior.from(sheet)
                            val displayMetrics = activity.resources.displayMetrics
                            val maxHeight = (displayMetrics.heightPixels * 0.88).toInt()

                            val layoutParams = sheet.layoutParams
                            layoutParams.height = maxHeight
                            sheet.layoutParams = layoutParams

                            behavior.peekHeight = maxHeight
                            behavior.state = BottomSheetBehavior.STATE_EXPANDED
                            behavior.skipCollapsed = true
                        }
                    } catch (e: Exception) {
                        Log.e("CrucibleDossierDialog", "Error setting up bottom sheet behavior on show", e)
                    }
                }
            }
            dialog?.show()
        } catch (e: Exception) {
            Log.e("CrucibleDossierDialog", "Error showing CrucibleDossierDialog", e)
        }
    }
}
