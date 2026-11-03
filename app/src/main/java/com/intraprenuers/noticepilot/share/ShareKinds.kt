package com.intraprenuers.noticepilot.share

import java.util.Locale

/** What a shared/received file is, from its MIME type with a file-name fallback. Pure Kotlin. */
object ShareKinds {
    enum class Kind { PDF, IMAGE, UNSUPPORTED }

    /** Same cap as PDF pages: keeps OCR time and the Gemini prompt reasonable. */
    const val MAX_IMAGES = 10
    const val MAX_IMAGE_BYTES = 30L * 1024 * 1024

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

    fun kindOf(mime: String?, name: String?): Kind {
        val m = mime?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()
        if (m == "application/pdf") return Kind.PDF
        if (m != null && m.startsWith("image/")) return Kind.IMAGE
        // Some apps send */* or application/octet-stream: fall back to the extension.
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return when {
            ext == "pdf" -> Kind.PDF
            ext in IMAGE_EXT -> Kind.IMAGE
            else -> Kind.UNSUPPORTED
        }
    }

    fun tooManyImagesNote(count: Int): String? =
        if (count > MAX_IMAGES) "Only the first $MAX_IMAGES of $count shared images were read." else null
}
