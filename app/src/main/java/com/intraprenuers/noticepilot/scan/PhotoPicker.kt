package com.intraprenuers.noticepilot.scan

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/** Fallback when the Document Scanner isn't available: the system photo picker (no permissions). */
object PhotoPicker {
    val contract = ActivityResultContracts.PickVisualMedia()
    fun imageRequest(): PickVisualMediaRequest =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
}
