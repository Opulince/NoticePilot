package com.intraprenuers.noticepilot.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * On-device OCR (ML Kit Latin, bundled model). Lines keep ML Kit's block/line reading order and are
 * numbered L1, L2... continuously across pages (images and PDF pages alike), each tagged with its page.
 * [read] throws IOException if an image can't be opened; [readPdf] throws [PdfException].
 */
class OcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(uris: List<Uri>): OcrResult {
        val lines = mutableListOf<OcrLine>()
        uris.forEachIndexed { index, uri ->
            val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
            val text = recognizer.process(image).await()
            addLines(text, page = index + 1, into = lines)
        }
        return OcrResult(lines)
    }

    /**
     * Renders each PDF page (up to [PdfLimits.MAX_PAGES]) to a bitmap and OCRs it, one page at a time.
     * Works the same for scanned and text PDFs, because every page is rendered and read as an image.
     * [onOpened] gets the total page count; [onPage] is called before each page with (pageNumber, pagesToRead).
     */
    suspend fun readPdf(uri: Uri, onOpened: (totalPages: Int) -> Unit, onPage: (page: Int, toRead: Int) -> Unit): OcrResult {
        val copy = withContext(Dispatchers.IO) { copyToCache(uri) }
        try {
            val fd = try {
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: IOException) {
                throw PdfException(CANT_OPEN, "open copy: ${e.message}", e)
            }
            try {
                val renderer = try {
                    withContext(Dispatchers.IO) { PdfRenderer(fd) }
                } catch (e: SecurityException) {
                    throw PdfException("This PDF is password-protected. Remove the password and try again.", "PdfRenderer: ${e.message}", e)
                } catch (e: IOException) {
                    throw PdfException("This PDF couldn't be opened. It may be damaged or not a real PDF.", "PdfRenderer: ${e.message}", e)
                }
                try {
                    val total = renderer.pageCount
                    if (total <= 0) throw PdfException("This PDF has no pages.", "pageCount=$total")
                    onOpened(total)
                    val lines = mutableListOf<OcrLine>()
                    val toRead = PdfLimits.pagesToRead(total)
                    for (i in 0 until toRead) {
                        currentCoroutineContext().ensureActive()
                        onPage(i + 1, toRead)
                        val bitmap = withContext(Dispatchers.IO) { renderPage(renderer, i) }
                        try {
                            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                            addLines(text, page = i + 1, into = lines)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    return OcrResult(lines)
                } finally {
                    renderer.close() // also closes fd
                }
            } finally {
                try { fd.close() } catch (e: IOException) { } // no-op if the renderer already closed it
            }
        } finally {
            copy.delete()
        }
    }

    /** One page -> white-backed bitmap (PDF pages are transparent; ML Kit needs dark text on light). */
    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        val page = try {
            renderer.openPage(index)
        } catch (e: Exception) {
            throw PdfException("Page ${index + 1} of this PDF couldn't be rendered.", "openPage($index): ${e.message}", e)
        }
        try {
            val (w, h) = PdfLimits.renderSize(page.width, page.height)
            val bitmap = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                throw PdfException("Not enough memory to read this PDF. Try a smaller file.", "createBitmap ${w}x$h: OOM")
            }
            bitmap.eraseColor(Color.WHITE)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } catch (e: Exception) {
                bitmap.recycle()
                throw PdfException("Page ${index + 1} of this PDF couldn't be rendered.", "render($index): ${e.message}", e)
            }
            return bitmap
        } finally {
            page.close()
        }
    }

    /**
     * Copies the picked document into app cache. Picker URIs may be cloud-backed or non-seekable and only
     * readable while the permission lasts; PdfRenderer needs a seekable file descriptor.
     */
    private fun copyToCache(uri: Uri): File {
        val out = File(context.cacheDir, "import.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw PdfException(CANT_OPEN, "openInputStream returned null")
            input.use { src ->
                out.outputStream().use { dst ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > PdfLimits.MAX_BYTES) {
                            throw PdfException("This PDF is too large (over ${PdfLimits.MAX_BYTES / (1024 * 1024)} MB).", "size > ${PdfLimits.MAX_BYTES}")
                        }
                        dst.write(buf, 0, n)
                    }
                    if (total == 0L) throw PdfException("This PDF file is empty.", "0 bytes")
                }
            }
            return out
        } catch (e: PdfException) {
            out.delete()
            throw e
        } catch (e: SecurityException) {
            out.delete()
            throw PdfException(CANT_OPEN, "SecurityException: ${e.message}", e)
        } catch (e: IOException) {
            out.delete()
            throw PdfException(CANT_OPEN, "${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun addLines(text: Text, page: Int, into: MutableList<OcrLine>) {
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val clean = line.text.trim()
                if (clean.isNotEmpty()) {
                    into += OcrLine(id = "L${into.size + 1}", text = clean, box = line.boundingBox, page = page)
                }
            }
        }
    }

    fun close() = recognizer.close()

    private companion object {
        const val CANT_OPEN = "NoticePilot couldn't open that file. Pick it again, or save it to your phone first."
    }
}
