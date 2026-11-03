package com.intraprenuers.noticepilot.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.intraprenuers.noticepilot.extract.ExtractedItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Opens the user's calendar app with the event prefilled (ACTION_INSERT, no calendar permissions).
 * The user saves it themselves. No time -> all-day event.
 */
object CalendarIntents {
    enum class Result { OPENED, NO_DATE, NO_CALENDAR_APP }

    fun insert(context: Context, item: ExtractedItem): Result {
        val start = parseDate(item.date) ?: return Result.NO_DATE
        val end = parseDate(item.endDate)?.takeIf { !it.isBefore(start) } ?: start
        val time = parseTime(item.time)
        val zone = ZoneId.systemDefault()

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, item.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description(item))

        if (time != null) {
            val begin = start.atTime(time).atZone(zone)
            val finish = end.atTime(time).atZone(zone).plusHours(1)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finish.toInstant().toEpochMilli())
        } else {
            // All-day: local midnight of the first day to local midnight after the last day.
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atStartOfDay(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }

        return try {
            context.startActivity(intent)
            Result.OPENED
        } catch (e: ActivityNotFoundException) {
            Result.NO_CALENDAR_APP
        }
    }

    private fun description(item: ExtractedItem): String = buildString {
        if (item.quote.isNotBlank()) append("From the notice: \"").append(item.quote.trim()).append("\"\n")
        item.conditions.filter { it.isNotBlank() }.forEach { append("- ").append(it.trim()).append('\n') }
        append("Added with NoticePilot")
    }

    private fun parseDate(s: String?): LocalDate? =
        try { s?.let { LocalDate.parse(it.trim()) } } catch (e: DateTimeParseException) { null }

    private fun parseTime(s: String?): LocalTime? =
        try { s?.let { LocalTime.parse(it.trim()) } } catch (e: DateTimeParseException) { null }
}
