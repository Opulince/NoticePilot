package com.intraprenuers.noticepilot.ui

import com.intraprenuers.noticepilot.extract.ExtractedItem
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.verify.CheckedItem
import com.intraprenuers.noticepilot.verify.Status

/** Pipeline stages shown while working. Only the stage that is actually running is shown. */
enum class Stage(val label: String) {
    OPENING_PDF("Opening PDF..."),
    READING_TEXT("Reading text..."),
    EXTRACTING("Extracting information..."),
    VERIFYING("Verifying evidence..."),
    PREPARING("Preparing results...")
}

/**
 * One result card. [checked] is the verifier's verdict on what Gemini produced and never changes.
 * [edited] holds the user's correction; once saved, [confirmed] is true. A confirmed item is
 * "confirmed by you", not "verified": the UI must keep those two apart.
 */
data class ResultItem(
    val id: Int,
    val checked: CheckedItem,
    val edited: ExtractedItem? = null,
    val confirmed: Boolean = false
) {
    /** What to display and what goes to the calendar: the user's edit if any, else Gemini's item. */
    val shown: ExtractedItem get() = edited ?: checked.item
    val isVerified: Boolean get() = checked.status == Status.VERIFIED
    val canAddToCalendar: Boolean get() = (isVerified || confirmed) && shown.date != null
}

sealed interface UiState {
    /** ScanScreen. [notice] is a one-off message such as "No text found" or "Scan cancelled". */
    data class Idle(val notice: String? = null) : UiState

    /**
     * ScanScreen with a progress indicator. [detail] is live progress such as "Page 2 of 5";
     * [fromPdf] switches the first step from "Document captured" to "Opening PDF".
     */
    data class Working(val stage: Stage, val detail: String? = null, val fromPdf: Boolean = false) : UiState

    /**
     * ResultsScreen, or SourceScreen when [sourceItemId] is set.
     * [lines] are all OCR lines in order (the evidence). Gemini may return zero items.
     */
    data class Results(
        val lines: List<OcrLine>,
        val items: List<ResultItem>,
        val sourceItemId: Int? = null,
        /** Id of the saved notice these results belong to, so edits are saved too. */
        val noticeId: Long? = null,
        /** One-off note about how the notice was read, e.g. PDF pages that were skipped. */
        val note: String? = null
    ) : UiState

    /**
     * Something failed. [message] is user-facing; [detail] is technical (never contains the key).
     * [canRetry] = the OCR text is kept and extraction can be retried without rescanning.
     */
    data class Error(val message: String, val detail: String? = null, val canRetry: Boolean = false) : UiState
}
