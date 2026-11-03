package com.intraprenuers.noticepilot.verify

import java.time.LocalDate
import java.time.Month
import java.time.DateTimeException

/** Finds day-first dates in free text. Pure Kotlin. */
object DateParser {
    data class ParsedDate(val day: Int, val month: Int, val year: Int?)

    private val MONTHS = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sept" to 9, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    // Longest names first so "march" wins over "mar" and "sept" over "sep".
    private val MON = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private const val SUF = "(?:st|nd|rd|th)?"
    private const val SEP = "\\s*(?:-|to|until|till)\\s*"
    private const val YEAR_OPT = "(?:,?\\s*(\\d{4})(?!\\d))?"

    // 2026-10-26
    private val ISO = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    // 26 october - 2 november 2026
    private val RANGE_CROSS = Regex(
        "(?<!\\d)(\\d{1,2})$SUF\\s*(?:of\\s+)?($MON)\\b\\.?$SEP(\\d{1,2})$SUF\\s*(?:of\\s+)?($MON)\\b\\.?,?\\s*(\\d{4})(?!\\d)"
    )
    // 26-28 october 2026, 26th to 28th oct
    private val RANGE_SAME = Regex(
        "(?<!\\d)(\\d{1,2})$SUF$SEP(\\d{1,2})$SUF\\s*(?:of\\s+)?($MON)\\b\\.?$YEAR_OPT"
    )
    // 26/10/2026, 26-10-26, 26.10.2026
    private val NUMERIC_YEAR = Regex("(?<!\\d)(\\d{1,2})([/.\\-])(\\d{1,2})\\2(\\d{4}|\\d{2})(?!\\d)")
    // 26th of october 2026, 26 oct
    private val DAY_MONTH = Regex("(?<!\\d)(\\d{1,2})$SUF\\s*(?:of\\s+)?($MON)\\b\\.?$YEAR_OPT")
    // october 26, 2026
    private val MONTH_DAY = Regex("\\b($MON)\\b\\.?\\s*(\\d{1,2})$SUF(?!\\d)$YEAR_OPT")
    // 26/10, 26-10, 26.10 (not followed by a month name, not part of a longer number)
    private val NUMERIC_NO_YEAR = Regex(
        "(?<![\\d/.\\-])(\\d{1,2})[/.\\-](\\d{1,2})(?!\\d|[/.\\-]\\d)(?!\\s*(?:$MON)\\b)"
    )

    fun findDates(text: String): List<ParsedDate> {
        return try {
            find(Normalizer.normalize(text))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun find(normalized: String): List<ParsedDate> {
        val found = LinkedHashSet<ParsedDate>()
        // Each pattern blanks out what it matched so later, looser patterns don't re-read it.
        var work = normalized

        fun scan(regex: Regex, handle: (MatchResult) -> Unit) {
            val sb = StringBuilder(work)
            for (m in regex.findAll(work)) {
                handle(m)
                for (i in m.range) sb.setCharAt(i, ' ')
            }
            work = sb.toString()
        }

        fun add(day: Int, month: Int, year: Int?) {
            make(day, month, year)?.let { found.add(it) }
        }

        scan(ISO) { m -> add(m.int(3), m.int(2), m.int(1)) }
        scan(RANGE_CROSS) { m ->
            val year = m.int(5)
            add(m.int(1), monthOf(m.groupValues[2]), year)
            add(m.int(3), monthOf(m.groupValues[4]), year)
        }
        scan(RANGE_SAME) { m ->
            val month = monthOf(m.groupValues[3])
            val year = m.groupValues[4].toIntOrNull()
            add(m.int(1), month, year)
            add(m.int(2), month, year)
        }
        scan(NUMERIC_YEAR) { m ->
            val y = m.groupValues[4]
            add(m.int(1), m.int(3), if (y.length == 2) 2000 + y.toInt() else y.toInt())
        }
        scan(DAY_MONTH) { m -> add(m.int(1), monthOf(m.groupValues[2]), m.groupValues[3].toIntOrNull()) }
        scan(MONTH_DAY) { m -> add(m.int(2), monthOf(m.groupValues[1]), m.groupValues[3].toIntOrNull()) }
        scan(NUMERIC_NO_YEAR) { m -> add(m.int(1), m.int(2), null) }
        return found.toList()
    }

    private fun MatchResult.int(group: Int): Int = groupValues[group].toIntOrNull() ?: -1

    private fun monthOf(name: String): Int = MONTHS[name] ?: -1

    private fun make(day: Int, month: Int, year: Int?): ParsedDate? {
        if (month !in 1..12 || day < 1) return null
        if (year == null) {
            if (day > Month.of(month).maxLength()) return null
        } else {
            try {
                LocalDate.of(year, month, day)
            } catch (e: DateTimeException) {
                return null
            }
        }
        return ParsedDate(day, month, year)
    }
}
