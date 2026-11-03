package com.intraprenuers.noticepilot.capture

/** Frame geometry for ImageReader buffers. Pure Kotlin. */
object ScreenFrames {
    /** Wait this long after projection starts before taking a frame, so the permission dialog and
     *  NoticePilot's own window have animated away and the user's notice is what is on screen. */
    const val SETTLE_MS = 1500L

    /** Give up if no usable frame arrives within this time. */
    const val TIMEOUT_MS = 6000L

    /**
     * ImageReader rows can be padded: rowStride may exceed width * pixelStride. Returns the pixel width
     * of the buffer (including padding) to create the bitmap with, before cropping to [width].
     */
    fun bufferWidth(width: Int, rowStride: Int, pixelStride: Int): Int {
        if (pixelStride <= 0) return width
        val padding = rowStride - pixelStride * width
        return if (padding <= 0) width else width + padding / pixelStride
    }
}
