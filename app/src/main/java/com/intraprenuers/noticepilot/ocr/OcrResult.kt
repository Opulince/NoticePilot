package com.intraprenuers.noticepilot.ocr

import android.graphics.Rect

/** One recognised text line. [id] is "L1", "L2"... numbered continuously across all pages. */
data class OcrLine(val id: String, val text: String, val box: Rect?, val page: Int)

data class OcrResult(val lines: List<OcrLine>)
