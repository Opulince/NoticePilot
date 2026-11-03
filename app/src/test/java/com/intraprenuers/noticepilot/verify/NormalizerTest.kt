package com.intraprenuers.noticepilot.verify

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test fun lowercases() = assertEquals("annual sports day", Normalizer.normalize("Annual SPORTS Day"))

    @Test fun collapsesWhitespace() =
        assertEquals("oct 12 sharp", Normalizer.normalize("  Oct \t\n 12  sharp  "))

    @Test fun curlyQuotes() =
        assertEquals("it's \"fine\"", Normalizer.normalize("It’s “Fine”"))

    @Test fun dashes() =
        assertEquals("26-28 oct - nov-1", Normalizer.normalize("26–28 Oct — Nov−1"))

    @Test fun keepsPunctuationAndDigits() = assertEquals("rs. 1,500/-", Normalizer.normalize("Rs. 1,500/-"))

    @Test fun emptyAndBlank() {
        assertEquals("", Normalizer.normalize(""))
        assertEquals("", Normalizer.normalize("  \n "))
    }
}
