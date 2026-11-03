package com.intraprenuers.noticepilot.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps scanned notices in one JSON file in app storage, so they survive closing the app. */
class NoticeStore(private val file: File) {
    private val mutex = Mutex()

    suspend fun load(): List<SavedNotice> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (file.exists()) NoticeJson.decode(file.readText()) else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Writes the list [latest] returns at the moment the write happens, so overlapping saves can never
     * leave an older snapshot on disk. Writes a temp file first, then renames it over the old one.
     */
    suspend fun save(latest: () -> List<SavedNotice>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(NoticeJson.encode(latest()))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            } catch (e: Exception) {
                // Saving is best effort: never crash the app over it.
            }
        }
    }

    companion object {
        const val MAX_NOTICES = 30
    }
}
