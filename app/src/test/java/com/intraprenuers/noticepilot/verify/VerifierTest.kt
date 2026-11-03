package com.intraprenuers.noticepilot.verify

import com.intraprenuers.noticepilot.extract.ExtractedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {
    private val lines = listOf(
        SourceLine("L1", "Annual Sports Day"),
        SourceLine("L2", "The event will be held on 26th October 2026 at 9:00 am."),
        SourceLine("L3", "Students must submit consent forms by 20/10/2026."),
        SourceLine("L4", "Sports kit is compulsory.")
    )

    private fun item(
        quote: String,
        lineIds: List<String> = listOf("L2"),
        date: String? = "2026-10-26",
        endDate: String? = null,
        unclear: String? = null
    ) = ExtractedItem("event", "Sports Day", date, endDate, null, emptyList(), lineIds, quote, unclear)

    private fun run(i: ExtractedItem, l: List<SourceLine> = lines) = Verifier.verify(i, l)

    @Test fun exactMatch() {
        val r = run(item("held on 26th October 2026"))
        assertEquals(Status.VERIFIED, r.status)
        assertTrue(r.reasons.isEmpty())
    }

    @Test fun whitespaceAndCaseIgnored() {
        val l = listOf(SourceLine("L1", "Event on Oct  12  2026"))
        val r = run(item("EVENT ON oct 12 2026", listOf("L1"), "2026-10-12"), l)
        assertEquals(Status.VERIFIED, r.status)
    }

    @Test fun curlyQuotesMatch() {
        val l = listOf(SourceLine("L1", "Bring the “blue” folder on 5 Nov 2026"))
        val r = run(item("Bring the \"blue\" folder on 5 Nov 2026", listOf("L1"), "2026-11-05"), l)
        assertEquals(Status.VERIFIED, r.status)
    }

    @Test fun multiLineCitationJoinedInOriginalOrder() {
        val r = run(item("annual sports day the event will be held", listOf("L2", "L1"), null))
        assertEquals(Status.VERIFIED, r.status)
    }

    @Test fun ocrTypoIsApproximate() {
        val r = run(item("The event wi11 be held on 26th October 2026"))
        assertEquals(Status.APPROXIMATE, r.status)
        assertEquals(listOf("quote only approximately matches the scanned text"), r.reasons)
    }

    @Test fun quoteAbsent() {
        val r = run(item("Parents must pay a fee of Rs. 500", date = null))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("quote not found in scanned text"), r.reasons)
    }

    @Test fun wrongDate() {
        val r = run(item("held on 26th October 2026", date = "2026-10-27"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("date doesn't match source"), r.reasons)
    }

    @Test fun yearMissingInQuote() {
        val l = listOf(SourceLine("L1", "Sports Day on 26th October at school"))
        val r = run(item("Sports Day on 26th October", listOf("L1")), l)
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("year missing in source"), r.reasons)
    }

    @Test fun numericIsDayFirst() {
        val ok = run(item("by 20/10/2026", listOf("L3"), "2026-10-20"))
        assertEquals(Status.VERIFIED, ok.status)
        val monthFirst = run(item("by 20/10/2026", listOf("L3"), "2026-20-10"))
        assertEquals(Status.NEEDS_CHECK, monthFirst.status)
        val l = listOf(SourceLine("L1", "Due 03/04/2026"))
        assertEquals(Status.VERIFIED, run(item("Due 03/04/2026", listOf("L1"), "2026-04-03"), l).status)
        assertEquals(Status.NEEDS_CHECK, run(item("Due 03/04/2026", listOf("L1"), "2026-03-04"), l).status)
    }

    @Test fun dateRangeBothVerified() {
        val l = listOf(SourceLine("L1", "Exams run from 26-28 October 2026 inclusive"))
        val r = run(item("26-28 October 2026", listOf("L1"), "2026-10-26", "2026-10-28"), l)
        assertEquals(Status.VERIFIED, r.status)
        assertTrue(r.reasons.isEmpty())
    }

    @Test fun badEndDate() {
        val l = listOf(SourceLine("L1", "Exams run from 26-28 October 2026 inclusive"))
        val r = run(item("26-28 October 2026", listOf("L1"), "2026-10-26", "2026-10-29"), l)
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("end date doesn't match source"), r.reasons)
    }

    @Test fun endDateNotInQuote() {
        val r = run(item("The event will be held", date = null, endDate = "2026-10-28"))
        assertEquals(listOf("end date not found in quote"), r.reasons)
    }

    @Test fun wrongLineCitedButQuoteExists() {
        val r = run(item("consent forms by 20/10/2026", listOf("L1"), "2026-10-20"))
        assertEquals(Status.APPROXIMATE, r.status)
        assertEquals(listOf("cited the wrong line"), r.reasons)
    }

    @Test fun unknownLineIdFallsBackToWholeText() {
        val r = run(item("held on 26th October 2026", listOf("L99")))
        assertEquals(Status.APPROXIMATE, r.status)
        assertEquals(listOf("cited the wrong line"), r.reasons)
    }

    @Test fun lineIdsAreMatchedTolerantly() {
        assertEquals(Status.VERIFIED, run(item("held on 26th October 2026", listOf(" l2 "))).status)
        assertEquals(Status.VERIFIED, run(item("held on 26th October 2026", listOf("2"))).status)
    }

    @Test fun noLineIdsAtAll() {
        val r = run(item("held on 26th October 2026", emptyList()))
        assertEquals(listOf("cited the wrong line"), r.reasons)
    }

    @Test fun unclearReasonForcesNeedsCheck() {
        val r = run(item("held on 26th October 2026", unclear = "Not sure which event this date belongs to"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("Not sure which event this date belongs to"), r.reasons)
    }

    @Test fun blankUnclearReasonIgnored() {
        assertEquals(Status.VERIFIED, run(item("held on 26th October 2026", unclear = "  ")).status)
    }

    @Test fun emptyQuote() {
        val r = run(item("", date = null))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("quote is empty"), r.reasons)
        assertEquals(Status.NEEDS_CHECK, run(item("   ", date = null)).status)
    }

    @Test fun emptyLinesList() {
        val r = run(item("held on 26th October 2026"), emptyList())
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertTrue("quote not found in scanned text" in r.reasons)
    }

    @Test fun dateNotInQuote() {
        val r = run(item("Sports kit is compulsory.", listOf("L4"), "2026-10-26"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("date not found in quote"), r.reasons)
    }

    @Test fun invalidIsoDate() {
        val r = run(item("held on 26th October 2026", date = "26/10/2026"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(listOf("date is not a valid date"), r.reasons)
        assertEquals(listOf("end date is not a valid date"),
            run(item("held on 26th October 2026", endDate = "soon")).reasons)
        assertEquals(listOf("date is not a valid date"),
            run(item("held on 26th October 2026", date = "2026-02-30")).reasons)
    }

    @Test fun blankDateTreatedAsAbsent() {
        assertEquals(Status.VERIFIED, run(item("Annual Sports Day", listOf("L1"), date = " ")).status)
    }

    @Test fun multipleReasonsKeptInOrder() {
        val r = run(item("held on 26th October 2027", date = "2026-10-27", unclear = "Year unclear"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(
            listOf("quote only approximately matches the scanned text", "date doesn't match source", "Year unclear"),
            r.reasons
        )
    }

    @Test fun worstStatusWins() {
        val r = run(item("The event wi11 be held on 26th October 2026", date = "2026-10-27"))
        assertEquals(Status.NEEDS_CHECK, r.status)
        assertEquals(2, r.reasons.size)
    }

    @Test fun nonDateItemWithoutDateVerifies() {
        val r = run(item("Sports kit is compulsory.", listOf("L4"), date = null))
        assertEquals(Status.VERIFIED, r.status)
    }

    @Test fun verifyAllKeepsOrderAndSize() {
        val out = Verifier.verifyAll(listOf(item("held on 26th October 2026"), item("nonsense text here", date = null)), lines)
        assertEquals(listOf(Status.VERIFIED, Status.NEEDS_CHECK), out.map { it.status })
        assertTrue(Verifier.verifyAll(emptyList(), lines).isEmpty())
    }

    @Test fun hugeInputDoesNotThrow() {
        val big = List(2000) { SourceLine("L$it", "Some filler text about the school term number $it") }
        val r = run(item("completely absent quote about uniforms", listOf("L5")), big)
        assertEquals(Status.NEEDS_CHECK, r.status)
    }

    @Test fun garbageNeverThrows() {
        val r = run(ExtractedItem("", "", "", "", "", emptyList(), listOf("", "  "), "\u0000￿", ""), listOf(SourceLine("", "")))
        assertEquals(Status.NEEDS_CHECK, r.status)
    }
}
