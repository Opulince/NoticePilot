package com.intraprenuers.noticepilot.storage

import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.ui.ResultItem

/** One scanned notice as kept on the phone: its OCR lines (the evidence) and its checked items. */
data class SavedNotice(
    val id: Long,
    val savedAt: Long,
    val lines: List<OcrLine>,
    val items: List<ResultItem>
) {
    /** A readable name: the first item title, else the first OCR line. */
    val name: String
        get() = (items.firstOrNull()?.shown?.title ?: lines.firstOrNull()?.text ?: "Scanned notice").take(80)
}
