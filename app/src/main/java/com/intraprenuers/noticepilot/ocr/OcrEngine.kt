package com.intraprenuers.noticepilot.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device OCR (ML Kit Latin, bundled model). Lines keep ML Kit's block/line reading order and are
 * numbered L1, L2... continuously across pages. Throws IOException if an image can't be opened.
 */
class OcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(uris: List<Uri>): OcrResult {
        val lines = mutableListOf<OcrLine>()
        uris.forEachIndexed { index, uri ->
            val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
            val text = recognizer.process(image).await()
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val clean = line.text.trim()
                    if (clean.isNotEmpty()) {
                        lines += OcrLine(id = "L${lines.size + 1}", text = clean, box = line.boundingBox, page = index + 1)
                    }
                }
            }
        }
        return OcrResult(lines)
    }

    fun close() = recognizer.close()
}
