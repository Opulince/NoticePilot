package com.intraprenuers.noticepilot.verify

/** Text normalisation shared by the verifier and the date parser. Pure Kotlin. */
object Normalizer {
    private const val SINGLE_QUOTES = "‘’‚‛′"
    private const val DOUBLE_QUOTES = "“”„‟″"
    private const val DASHES = "‐‑‒–—―−"

    /** Lowercase, straight quotes, plain dashes, whitespace collapsed to single spaces, trimmed. */
    fun normalize(s: String): String {
        val out = StringBuilder(s.length)
        var pendingSpace = false
        for (c in s.lowercase()) {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = true
                continue
            }
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(
                when (c) {
                    in SINGLE_QUOTES -> '\''
                    in DOUBLE_QUOTES -> '"'
                    in DASHES -> '-'
                    else -> c
                }
            )
        }
        return out.toString()
    }
}
