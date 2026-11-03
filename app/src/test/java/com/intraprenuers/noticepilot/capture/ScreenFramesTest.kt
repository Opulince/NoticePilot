package com.intraprenuers.noticepilot.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFramesTest {
    @Test fun noPadding() = assertEquals(1080, ScreenFrames.bufferWidth(1080, 1080 * 4, 4))

    @Test fun paddedRows() = assertEquals(1088, ScreenFrames.bufferWidth(1080, 1088 * 4, 4))

    @Test fun oddPaddingRoundsDown() = assertEquals(1081, ScreenFrames.bufferWidth(1080, 1080 * 4 + 6, 4))

    @Test fun bogusStridesFallBackToWidth() {
        assertEquals(720, ScreenFrames.bufferWidth(720, 0, 4))
        assertEquals(720, ScreenFrames.bufferWidth(720, 2880, 0))
    }

    @Test fun settleIsShorterThanTimeout() = assertTrue(ScreenFrames.SETTLE_MS < ScreenFrames.TIMEOUT_MS)
}
