package com.kuikly.stockchat.domain.attachment

enum class AttachmentKind { IMAGE, DOCUMENT }

enum class AttachmentSource { CAMERA, PHOTO_LIBRARY, FILE }

enum class AttachmentStatus { READY, PROCESSING, FAILED, EXPIRED }

data class Attachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val localPath: String,
    val thumbnailPath: String? = null,
    val source: AttachmentSource,
    val kind: AttachmentKind,
    val status: AttachmentStatus = AttachmentStatus.READY,
    val errorMessage: String = "",
)
