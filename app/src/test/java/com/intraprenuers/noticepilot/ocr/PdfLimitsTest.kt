package com.intraprenuers.noticepilot.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLimitsTest {
    @Test fun a4RendersAtTargetLongSide() {
        val (w, h) = PdfLimits.renderSize(595, 842) // A4 in points
        assertEquals(PdfLimits.TARGET_LONG_SIDE_PX, h)
        assertEquals(1696, w) // 595 * 2400/842, rounded
    }

    @Test fun landscapeUsesWidthAsLongSide() {
        val (w, h) = PdfLimits.renderSize(842, 595)
        assertEquals(PdfLimits.TARGET_LONG_SIDE_PX, w)
        assertTrue(h < w)
    }

    @Test fun tinyPageUpscaleIsCapped() = assertEquals(400 to 400, PdfLimits.renderSize(100, 100))

    @Test fun hugePageFitsPixelBudget() {
        val (w, h) = PdfLimits.renderSize(5000, 5000)
        assertTrue(w.toLong() * h <= PdfLimits.TARGET_LONG_SIDE_PX.toLong() * PdfLimits.TARGET_LONG_SIDE_PX)
    }

    @Test fun longStripStaysWithinPixelBudget() {
        val (w, h) = PdfLimits.renderSize(600, 20000)
        assertTrue(w >= 1 && h >= 1)
        assertTrue(w.toLong() * h <= PdfLimits.TARGET_LONG_SIDE_PX.toLong() * PdfLimits.TARGET_LONG_SIDE_PX)
    }

    @Test fun zeroSizedPageDoesNotCrash() {
        val (w, h) = PdfLimits.renderSize(0, 0)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test fun pageLimit() {
        assertEquals(0, PdfLimits.pagesToRead(0))
        assertEquals(3, PdfLimits.pagesToRead(3))
        assertEquals(PdfLimits.MAX_PAGES, PdfLimits.pagesToRead(40))
    }

    @Test fun skippedNoteOnlyWhenOverLimit() {
        assertNull(PdfLimits.skippedNote(PdfLimits.MAX_PAGES))
        assertTrue(PdfLimits.skippedNote(23)!!.contains("first ${PdfLimits.MAX_PAGES} of 23"))
    }

    @Test fun readNoteCombinesSkippedAndFailedPages() {
        assertNull(PdfLimits.readNote(3, emptyList()))
        assertEquals("Page 2 couldn't be rendered and was not read.", PdfLimits.readNote(3, listOf(2)))
        assertEquals("Pages 2, 5 couldn't be rendered and were not read.", PdfLimits.readNote(6, listOf(2, 5)))
        val both = PdfLimits.readNote(12, listOf(4))!!
        assertTrue(both.startsWith("Only the first ${PdfLimits.MAX_PAGES} of 12 pages were read."))
        assertTrue(both.endsWith("Page 4 couldn't be rendered and was not read."))
    }
}
