package com.intraprenuers.noticepilot.share

import com.intraprenuers.noticepilot.share.ShareKinds.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareKindsTest {
    @Test fun mimeWins() {
        assertEquals(Kind.PDF, ShareKinds.kindOf("application/pdf", "x.jpg"))
        assertEquals(Kind.IMAGE, ShareKinds.kindOf("image/jpeg", null))
        assertEquals(Kind.IMAGE, ShareKinds.kindOf("IMAGE/PNG", null))
        assertEquals(Kind.PDF, ShareKinds.kindOf("application/pdf; charset=binary", null))
    }

    @Test fun genericMimeFallsBackToExtension() {
        assertEquals(Kind.PDF, ShareKinds.kindOf("application/octet-stream", "Circular 12.PDF"))
        assertEquals(Kind.IMAGE, ShareKinds.kindOf("*/*", "IMG-2026.jpeg"))
        assertEquals(Kind.PDF, ShareKinds.kindOf(null, "notice.pdf"))
    }

    @Test fun unsupported() {
        assertEquals(Kind.UNSUPPORTED, ShareKinds.kindOf("text/plain", "notes.txt"))
        assertEquals(Kind.UNSUPPORTED, ShareKinds.kindOf(null, null))
        assertEquals(Kind.UNSUPPORTED, ShareKinds.kindOf("video/mp4", "clip"))
    }

    @Test fun imageCapNote() {
        assertNull(ShareKinds.tooManyImagesNote(ShareKinds.MAX_IMAGES))
        assertEquals("Only the first 10 of 14 shared images were read.", ShareKinds.tooManyImagesNote(14))
    }
}
