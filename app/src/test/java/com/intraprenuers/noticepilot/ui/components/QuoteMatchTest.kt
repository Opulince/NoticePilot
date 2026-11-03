package com.intraprenuers.noticepilot.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteMatchTest {
    private fun hit(line: String, quote: String): String? = QuoteMatch.rangeIn(line, quote)?.let { line.substring(it.first, it.last + 1) }

    @Test fun quoteInsideLine() =
        assertEquals("held on 26th October 2026", hit("The event will be held on 26th October 2026 at 9 am.", "held on 26th October 2026"))

    @Test fun caseInsensitive() = assertEquals("Held On 26", hit("Held On 26 Oct", "held on 26"))

    @Test fun lineInsideMultiLineQuote() = assertEquals("by 20 October 2026", hit("by 20 October 2026", "submit forms by 20 October 2026"))

    @Test fun lineEndsWithQuoteStart() =
        assertEquals("must submit consent", hit("Students must submit consent", "must submit consent forms by 20/10/2026"))

    @Test fun lineStartsWithQuoteEnd() =
        assertEquals("forms by 20/10/2026", hit("forms by 20/10/2026. Late forms", "must submit consent forms by 20/10/2026"))

    @Test fun unrelatedLineIsNull() = assertNull(QuoteMatch.rangeIn("Principal's message", "Sports Day on 26 October"))

    @Test fun tinyOverlapIgnored() = assertNull(QuoteMatch.rangeIn("on the", "the event"))

    @Test fun emptyInputsAreNull() {
        assertNull(QuoteMatch.rangeIn("", "x"))
        assertNull(QuoteMatch.rangeIn("text", "   "))
    }
}
