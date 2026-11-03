package com.intraprenuers.noticepilot.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intraprenuers.noticepilot.ui.StatusColor
import com.intraprenuers.noticepilot.ui.StatusColors
import com.intraprenuers.noticepilot.verify.Status
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/** "2026-10-26" -> "Mon, 26 Oct 2026". Falls back to the raw string. */
fun formatDate(iso: String): String =
    try {
        LocalDate.parse(iso.trim()).format(dateFormat)
    } catch (e: Exception) {
        iso
    }

/** "Mon, 26 Oct 2026 – Wed, 28 Oct 2026 · 09:30", or null if there is nothing to show. */
fun formatWhen(date: String?, endDate: String?, time: String?): String? {
    val parts = StringBuilder()
    if (!date.isNullOrBlank()) {
        parts.append(formatDate(date))
        if (!endDate.isNullOrBlank()) parts.append(" – ").append(formatDate(endDate))
    }
    if (!time.isNullOrBlank()) {
        if (parts.isNotEmpty()) parts.append(" · ")
        parts.append(time)
    }
    return if (parts.isEmpty()) null else parts.toString()
}

@Composable
fun Pill(text: String, colors: StatusColor, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    when (status) {
        Status.VERIFIED -> Pill("Verified", StatusColors.verified(), modifier)
        Status.APPROXIMATE -> Pill("Approximate match", StatusColors.approximate(), modifier)
        Status.NEEDS_CHECK -> Pill("Needs check", StatusColors.needsCheck(), modifier)
    }
}

@Composable
fun ConfirmedBadge(modifier: Modifier = Modifier) {
    Pill("Confirmed by you", StatusColors.confirmed(), modifier)
}

@Composable
fun TypeChip(type: String, modifier: Modifier = Modifier) {
    Text(
        text = type.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
