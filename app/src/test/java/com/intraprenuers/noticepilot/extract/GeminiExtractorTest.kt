package com.intraprenuers.noticepilot.extract

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GeminiExtractorTest {
    @Test fun blankKeyThrowsMissingKeyBeforeNetwork() {
        try {
            runBlocking { GeminiExtractor("  ").extract(emptyList()) }
            fail("expected exception")
        } catch (e: ExtractionException) {
            assertEquals(ExtractionException.Kind.MISSING_KEY, e.kind)
        }
    }

    @Test fun userMessages() {
        assertEquals(
            "No internet connection. Connect and try again.",
            ExtractionException(ExtractionException.Kind.NO_INTERNET, "x").userMessage
        )
        assertEquals(
            "Couldn't read this notice. Please try again.",
            ExtractionException(ExtractionException.Kind.BAD_JSON, "x").userMessage
        )
    }
}
