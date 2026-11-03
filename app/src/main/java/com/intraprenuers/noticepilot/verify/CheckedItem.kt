package com.intraprenuers.noticepilot.verify

import com.intraprenuers.noticepilot.extract.ExtractedItem

/** Ordered best -> worst. The final status is the worst one found. */
enum class Status { VERIFIED, APPROXIMATE, NEEDS_CHECK }

data class CheckedItem(val item: ExtractedItem, val status: Status, val reasons: List<String>)

/** A source line as the verifier sees it: no android types. */
data class SourceLine(val id: String, val text: String)
