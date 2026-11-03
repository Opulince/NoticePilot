package com.intraprenuers.noticepilot.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.intraprenuers.noticepilot.io.CacheFiles
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

    /** What a PDF read produced: the lines plus an honest account of which pages were (not) read. */
    data class PdfRead(val result: OcrResult, val totalPages: Int, val pagesRead: Int, val failedPages: List<Int>)

    /**
     * Renders each PDF page (up to [PdfLimits.MAX_PAGES]) to a bitmap and OCRs it, one page at a time.
     * Works the same for scanned and text PDFs, because every page is rendered and read as an image.
     * A page that fails to render is recorded in [PdfRead.failedPages] and skipped; if no page at all
     * could be read, [PdfException] is thrown.
     * [onOpened] gets the total page count; [onPage] is called before each page with (pageNumber, pagesToRead).
     */
    suspend fun readPdf(uri: Uri, onOpened: (totalPages: Int) -> Unit, onPage: (page: Int, toRead: Int) -> Unit): PdfRead {
        val copy = withContext(Dispatchers.IO) { copyPdfToCache(uri) }
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
                    val failed = mutableListOf<Int>()
                    val toRead = PdfLimits.pagesToRead(total)
                    for (i in 0 until toRead) {
                        currentCoroutineContext().ensureActive()
                        onPage(i + 1, toRead)
                        val bitmap = try {
                            withContext(Dispatchers.IO) { renderPage(renderer, i) }
                        } catch (e: PdfException) {
                            failed += i + 1
                            continue
                        }
                        try {
                            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                            addLines(text, page = i + 1, into = lines)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    if (failed.size == toRead) {
                        throw PdfException("None of this PDF's pages could be rendered.", "failed pages: $failed")
                    }
                    return PdfRead(OcrResult(lines), totalPages = total, pagesRead = toRead - failed.size, failedPages = failed)
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
     * Copies the picked/shared PDF into app cache (unique name). Picker and share URIs may be cloud-backed,
     * non-seekable or only readable while the permission lasts; PdfRenderer needs a seekable local file.
     */
    private fun copyPdfToCache(uri: Uri): File =
        try {
            CacheFiles.copy(context, uri, prefix = "pdf", extension = "pdf", maxBytes = PdfLimits.MAX_BYTES)
        } catch (e: CacheFiles.TooLargeException) {
            throw PdfException("This PDF is too large (over ${PdfLimits.MAX_BYTES / (1024 * 1024)} MB).", "size > ${PdfLimits.MAX_BYTES}", e)
        } catch (e: CacheFiles.EmptyException) {
            throw PdfException("This PDF file is empty.", "0 bytes", e)
        } catch (e: SecurityException) {
            throw PdfException(CANT_OPEN, "SecurityException: ${e.message}", e)
        } catch (e: IOException) {
            throw PdfException(CANT_OPEN, "${e.javaClass.simpleName}: ${e.message}", e)
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
