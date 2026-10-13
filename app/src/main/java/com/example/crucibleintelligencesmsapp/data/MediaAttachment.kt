package com.example.crucibleintelligencesmsapp.data

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID

enum class AttachmentType {
    IMAGE,
    VIDEO,
    FILE
}

data class MediaAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
    val name: String = "Attachment",
    val type: AttachmentType = AttachmentType.FILE,
    val mimeType: String? = null
)
