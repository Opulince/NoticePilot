package com.intraprenuers.noticepilot.ocr

import kotlin.math.max
import kotlin.math.roundToInt

/** Limits and render sizing for PDF import. Pure Kotlin so it can be unit tested. */
object PdfLimits {
    /** Pages beyond this are skipped (and the user is told), to keep time and the Gemini prompt reasonable. */
    const val MAX_PAGES = 10

    /** Files larger than this are refused before copying. */
    const val MAX_BYTES = 50L * 1024 * 1024

    /** Longest rendered side in pixels: ~200-300 dpi for A4/Letter, plenty for ML Kit, ~16 MB per bitmap. */
    const val TARGET_LONG_SIDE_PX = 2400

    /** Never upscale tiny pages more than this, never shrink below 1:1 with PDF points. */
    private const val MAX_SCALE = 4.0f
    private const val MIN_SCALE = 1.0f

    fun pagesToRead(pageCount: Int): Int = pageCount.coerceIn(0, MAX_PAGES)

    /** Bitmap size for a page measured in PDF points (1/72 inch). Always at least 1x1. */
    fun renderSize(widthPt: Int, heightPt: Int): Pair<Int, Int> {
        val w = max(widthPt, 1)
        val h = max(heightPt, 1)
        val scale = (TARGET_LONG_SIDE_PX.toFloat() / max(w, h)).coerceIn(MIN_SCALE, MAX_SCALE)
        // Also cap the area so a freak page size (very long strip) can't blow up memory.
        val maxPixels = TARGET_LONG_SIDE_PX * TARGET_LONG_SIDE_PX
        var bw = (w * scale).roundToInt().coerceAtLeast(1)
        var bh = (h * scale).roundToInt().coerceAtLeast(1)
        if (bw.toLong() * bh > maxPixels) {
            val shrink = kotlin.math.sqrt(maxPixels.toDouble() / (bw.toLong() * bh))
            bw = (bw * shrink).toInt().coerceAtLeast(1)
            bh = (bh * shrink).toInt().coerceAtLeast(1)
        }
        return bw to bh
    }

    /** Shown on the results when pages were skipped, null otherwise. */
    fun skippedNote(pageCount: Int): String? =
        if (pageCount > MAX_PAGES) "Only the first $MAX_PAGES of $pageCount pages were read. Split long PDFs to read the rest." else null

    /** Everything the user should know about pages that were not read (over the limit, or failed). Null if none. */
    fun readNote(pageCount: Int, failedPages: List<Int>): String? {
        val parts = mutableListOf<String>()
        skippedNote(pageCount)?.let { parts += it }
        if (failedPages.isNotEmpty()) {
            val which = failedPages.joinToString(", ")
            parts += if (failedPages.size == 1) "Page $which couldn't be rendered and was not read." else "Pages $which couldn't be rendered and were not read."
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }
}

/** A PDF problem with a message that can be shown to the user as is. */
class PdfException(val userMessage: String, detail: String, cause: Throwable? = null) : Exception(detail, cause)
