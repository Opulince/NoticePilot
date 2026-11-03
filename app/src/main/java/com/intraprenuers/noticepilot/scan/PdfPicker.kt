package com.intraprenuers.noticepilot.scan

import androidx.activity.result.contract.ActivityResultContracts

/**
 * System document picker (Storage Access Framework) limited to PDFs: device storage and cloud providers,
 * no storage permission needed. The returned content URI is read once, straight away (see OcrEngine.readPdf).
 */
object PdfPicker {
    val contract = ActivityResultContracts.OpenDocument()
    val mimeTypes = arrayOf("application/pdf")
}
