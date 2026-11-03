package com.intraprenuers.noticepilot.storage

import com.intraprenuers.noticepilot.extract.ExtractedItem
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.ui.ResultItem
import com.intraprenuers.noticepilot.verify.CheckedItem
import com.intraprenuers.noticepilot.verify.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeJsonTest {

    private val item = ExtractedItem(
        type = "event", title = "Sports Day", date = "2026-10-26", endDate = null, time = "09:00",
        conditions = listOf("Wear house T-shirt"), lineIds = listOf("L2"),
        quote = "held on 26th October 2026", unclearReason = null
    )

    private val notice = SavedNotice(
        id = 1_700_000_000_000L,
        savedAt = 1_700_000_000_500L,
        lines = listOf(OcrLine("L1", "Annual Sports Day", null, 1), OcrLine("L2", "held on 26th October 2026", null, 2)),
        items = listOf(
            ResultItem(0, CheckedItem(item, Status.VERIFIED, emptyList())),
            ResultItem(
                1,
                CheckedItem(item.copy(title = "Fee", date = null, unclearReason = "year not stated"), Status.NEEDS_CHECK, listOf("year not stated")),
                edited = item.copy(title = "Fee payment", date = "2026-11-02"),
                confirmed = true
            )
        )
    )

    @Test fun roundTripKeepsEverything() {
        val back = NoticeJson.decode(NoticeJson.encode(listOf(notice)))
        assertEquals(listOf(notice), back)
    }

    @Test fun nullFieldsStayNull() {
        val back = NoticeJson.decode(NoticeJson.encode(listOf(notice))).single()
        val flagged = back.items[1].checked.item
        assertNull(flagged.date)
        assertNull(flagged.endDate)
        assertEquals("year not stated", flagged.unclearReason)
        assertNull(back.items[0].edited)
    }

    @Test fun garbageDecodesToEmpty() {
        assertTrue(NoticeJson.decode("").isEmpty())
        assertTrue(NoticeJson.decode("not json").isEmpty())
        assertTrue(NoticeJson.decode("{}").isEmpty())
    }

    @Test fun corruptEntryIsSkippedOthersKept() {
        val good = NoticeJson.encode(listOf(notice))
        val mixed = good.replaceFirst("\"notices\":[", "\"notices\":[{\"id\":5},")
        val back = NoticeJson.decode(mixed)
        assertEquals(listOf(notice), back)
    }

    @Test fun unknownStatusBecomesNeedsCheck() {
        val json = NoticeJson.encode(listOf(notice)).replace("\"VERIFIED\"", "\"SOMETHING_NEW\"")
        assertEquals(Status.NEEDS_CHECK, NoticeJson.decode(json).single().items[0].checked.status)
    }

    @Test fun nameUsesFirstItemTitleThenFirstLine() {
        assertEquals("Sports Day", notice.name)
        assertEquals("Annual Sports Day", notice.copy(items = emptyList()).name)
        assertEquals("Scanned notice", notice.copy(items = emptyList(), lines = emptyList()).name)
    }
}
