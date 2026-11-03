package com.intraprenuers.noticepilot.verify

import com.intraprenuers.noticepilot.verify.DateParser.ParsedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateParserTest {
    private fun d(day: Int, month: Int, year: Int? = 2026) = ParsedDate(day, month, year)
    private fun dates(s: String) = DateParser.findDates(s)

    @Test fun fullMonthWithYear() = assertEquals(listOf(d(26, 10)), dates("26 October 2026"))
    @Test fun ordinalAbbrev() = assertEquals(listOf(d(26, 10)), dates("26th Oct 2026"))
    @Test fun commaAfterMonth() = assertEquals(listOf(d(26, 10)), dates("26 Oct, 2026"))
    @Test fun ordinals() {
        assertEquals(listOf(d(1, 11)), dates("1st November 2026"))
        assertEquals(listOf(d(2, 11)), dates("2nd November 2026"))
        assertEquals(listOf(d(3, 11)), dates("3rd November 2026"))
        assertEquals(listOf(d(4, 11)), dates("4th November 2026"))
    }
    @Test fun monthFirst() = assertEquals(listOf(d(26, 10)), dates("October 26, 2026"))
    @Test fun monthFirstNoComma() = assertEquals(listOf(d(26, 10)), dates("Oct 26 2026"))
    @Test fun ofForm() = assertEquals(listOf(d(26, 10)), dates("26th of October 2026"))
    @Test fun weekdayIgnored() = assertEquals(listOf(d(26, 10)), dates("Monday, 26 October 2026"))
    @Test fun sept() = assertEquals(listOf(d(5, 9)), dates("5 Sept 2026"))
    @Test fun trailingDot() = assertEquals(listOf(d(26, 10)), dates("26 Oct. 2026"))

    @Test fun numericSlash() = assertEquals(listOf(d(26, 10)), dates("26/10/2026"))
    @Test fun numericDash() = assertEquals(listOf(d(26, 10)), dates("26-10-2026"))
    @Test fun numericDot() = assertEquals(listOf(d(26, 10)), dates("26.10.2026"))
    @Test fun numericIsDayFirst() = assertEquals(listOf(d(3, 4)), dates("03/04/2026"))
    @Test fun twoDigitYear() = assertEquals(listOf(d(26, 10)), dates("26/10/26"))
    @Test fun isoDate() = assertEquals(listOf(d(26, 10)), dates("2026-10-26"))
    @Test fun numericDashNotARange() = assertEquals(listOf(d(26, 10)), dates("by 26-10-2026 please"))

    @Test fun noYearTextual() {
        assertEquals(listOf(d(26, 10, null)), dates("26 October"))
        assertEquals(listOf(d(26, 10, null)), dates("26th Oct"))
        assertEquals(listOf(d(26, 10, null)), dates("October 26"))
    }
    @Test fun noYearNumeric() {
        assertEquals(listOf(d(26, 10, null)), dates("26/10"))
        assertEquals(listOf(d(26, 10, null)), dates("26-10"))
        assertEquals(listOf(d(26, 10, null)), dates("26.10"))
    }

    @Test fun rangeSharesYear() = assertEquals(listOf(d(26, 10), d(28, 10)), dates("26-28 October 2026"))
    @Test fun rangeWithOrdinalsAndSpaces() =
        assertEquals(listOf(d(26, 10), d(28, 10)), dates("26th - 28th Oct 2026"))
    @Test fun rangeAcrossMonths() =
        assertEquals(listOf(d(26, 10), d(2, 11)), dates("26 October - 2 November 2026"))
    @Test fun rangeWithTo() = assertEquals(listOf(d(26, 10), d(28, 10)), dates("26 to 28 October 2026"))
    @Test fun rangeNoYear() = assertEquals(listOf(d(26, 10, null), d(28, 10, null)), dates("26-28 October"))
    @Test fun rangeIsNotMisreadAsNumeric() = assertEquals(listOf(d(1, 10), d(12, 10)), dates("1-12 October 2026"))

    @Test fun timeIsIgnored() {
        assertTrue(dates("at 10.30 am").isEmpty())
        assertTrue(dates("at 9:00 am").isEmpty())
    }
    @Test fun invalidDatesDropped() {
        assertTrue(dates("31 February 2026").isEmpty())
        assertTrue(dates("32/10/2026").isEmpty())
        assertTrue(dates("26/13/2026").isEmpty())
        assertTrue(dates("31/04").isEmpty())
    }
    @Test fun leapDayWithoutYearAllowed() = assertEquals(listOf(d(29, 2, null)), dates("29 February"))
    @Test fun phoneNumbersIgnored() {
        assertTrue(dates("Call 9876543210 or 12345").isEmpty())
        assertTrue(dates("Ph 022-2345-6789").isEmpty())
    }
    @Test fun marchNotMar() = assertEquals(listOf(d(5, 3)), dates("5 March 2026"))
    @Test fun wordsStartingWithMonthAbbrevIgnored() = assertTrue(dates("5 marketing days").isEmpty())
    @Test fun multipleDates() =
        assertEquals(listOf(d(20, 10), d(26, 10)), dates("Forms by 20/10/2026, event on 26 October 2026."))
    @Test fun emptyAndGarbage() {
        assertTrue(dates("").isEmpty())
        assertTrue(dates("////---...").isEmpty())
    }
}
