package com.intraprenuers.noticepilot.capture

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Intent actions shared by the tile, the capture service and MainActivity. */
object CaptureActions {
    const val ACTION_TILE_CAPTURE = "com.intraprenuers.noticepilot.ACTION_TILE_CAPTURE"
    const val ACTION_CAPTURE_DONE = "com.intraprenuers.noticepilot.ACTION_CAPTURE_DONE"
}

/**
 * Hands the captured frame from ScreenCaptureService to MainActivity (same process). The activity takes it
 * in onResume, so it is processed even when Android blocks the service from bringing the app forward
 * (background-activity-launch limits) and the user reopens NoticePilot themselves.
 */
object CaptureHandoff {
    sealed interface Outcome {
        data class Captured(val file: File) : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val pending = AtomicReference<Outcome?>(null)

    @Volatile
    var inProgress: Boolean = false

    fun deliver(outcome: Outcome) = pending.set(outcome)

    /** Returns the waiting outcome once, or null. */
    fun take(): Outcome? = pending.getAndSet(null)
}
