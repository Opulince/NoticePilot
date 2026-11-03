package com.intraprenuers.noticepilot.extract

/** One item Gemini pulled out of the notice. Pure Kotlin: used by verify/ and JVM unit tests. */
data class ExtractedItem(
    val type: String,              // "task" | "event" | "deadline" | "info"
    val title: String,
    val date: String?,             // ISO yyyy-MM-dd
    val endDate: String?,          // ISO yyyy-MM-dd, only for ranges
    val time: String?,             // HH:mm, 24h
    val conditions: List<String>,
    val lineIds: List<String>,
    val quote: String,
    val unclearReason: String?
)
