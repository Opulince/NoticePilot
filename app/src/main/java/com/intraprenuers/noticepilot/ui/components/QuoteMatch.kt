package com.intraprenuers.noticepilot.ui.components

/**
 * Where the AI's quote sits inside one source line, for highlighting. Pure Kotlin.
 * Handles quotes that span several lines: the line may contain the quote, be contained in it, or
 * share a start/end overlap with it. Case-insensitive. Returns null when nothing meaningful overlaps.
 */
object QuoteMatch {
    private const val MIN_OVERLAP = 6

    fun rangeIn(line: String, quote: String): IntRange? {
        val q = quote.trim()
        if (line.isEmpty() || q.isEmpty()) return null
        val l = line.lowercase()
        val ql = q.lowercase()

        val inside = l.indexOf(ql)
        if (inside >= 0) return inside until inside + ql.length
        if (ql.contains(l.trim())) return line.indices

        // Line ends with the start of the quote (quote continues on the next line).
        for (len in minOf(l.length, ql.length) downTo MIN_OVERLAP) {
            if (l.endsWith(ql.substring(0, len))) return (l.length - len) until l.length
        }
        // Line starts with the end of the quote (quote began on the previous line).
        for (len in minOf(l.length, ql.length) downTo MIN_OVERLAP) {
            if (l.startsWith(ql.substring(ql.length - len))) return 0 until len
        }
        return null
    }
}
