package com.intraprenuers.noticepilot.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Copies picked/shared content into app cache under a unique name, while the URI read permission
 * is still valid. Everything downstream (OCR, PdfRenderer) then works on a plain, seekable local file.
 */
object CacheFiles {

    class TooLargeException(val limitBytes: Long) : IOException("larger than $limitBytes bytes")
    class EmptyException : IOException("0 bytes")

    /**
     * Blocking: call on Dispatchers.IO. Throws [TooLargeException], [EmptyException], IOException or
     * SecurityException (permission revoked). Never leaves a partial file behind.
     */
    fun copy(context: Context, uri: Uri, prefix: String, extension: String, maxBytes: Long): File {
        val out = newFile(context, prefix, extension)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("openInputStream returned null")
            input.use { src ->
                out.outputStream().use { dst ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw TooLargeException(maxBytes)
                        dst.write(buf, 0, n)
                    }
                    if (total == 0L) throw EmptyException()
                }
            }
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        }
    }

    /** A fresh, collision-free file in cacheDir/imports (repeated or overlapping imports never clash). */
    fun newFile(context: Context, prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        return File.createTempFile("${prefix}_", ".$extension", dir)
    }

    fun deleteQuietly(files: List<File>) {
        for (f in files) try { f.delete() } catch (e: Exception) { }
    }
}
