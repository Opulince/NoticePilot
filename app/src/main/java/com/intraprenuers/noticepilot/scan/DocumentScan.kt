package com.intraprenuers.noticepilot.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** ML Kit Document Scanner: JPEG pages, gallery import allowed, up to [PAGE_LIMIT] pages. */
object DocumentScan {
    const val PAGE_LIMIT = 5

    fun client(): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        return GmsDocumentScanning.getClient(options)
    }

    /** Page image URIs in page order, or null if the user cancelled the scan. */
    fun pageUris(resultCode: Int, data: Intent?): List<Uri>? {
        if (resultCode != Activity.RESULT_OK) return null
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return emptyList()
        return result.pages?.map { it.imageUri }.orEmpty()
    }
}
