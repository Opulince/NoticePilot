package com.intraprenuers.noticepilot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intraprenuers.noticepilot.ui.NpShape
import com.intraprenuers.noticepilot.ui.StatusColor
import com.intraprenuers.noticepilot.ui.StatusColors
import com.intraprenuers.noticepilot.verify.Status
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun parseIso(iso: String?): LocalDate? =
    try {
        iso?.let { LocalDate.parse(it.trim()) }
    } catch (e: Exception) {
        null
    }

/** "2026-10-26" -> "Mon, 26 Oct 2026". Falls back to the raw string. */
fun formatDate(iso: String): String = parseIso(iso)?.format(dateFormat) ?: iso

/** "Mon, 26 Oct 2026 – 28 Oct · 09:30", or null if there is nothing to show. */
fun formatWhen(date: String?, endDate: String?, time: String?): String? {
    val parts = StringBuilder()
    if (!date.isNullOrBlank()) {
        parts.append(formatDate(date))
        if (!endDate.isNullOrBlank()) parts.append(" – ").append(parseIso(endDate)?.format(shortDate) ?: endDate)
    }
    if (!time.isNullOrBlank()) {
        if (parts.isNotEmpty()) parts.append(" · ")
        parts.append(time)
    }
    return if (parts.isEmpty()) null else parts.toString()
}

/** Small uppercase label used for section headers and metadata. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** A compact status label with an icon: square-ish, not a pill. */
@Composable
fun Pill(text: String, colors: StatusColor, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = NpShape.Badge, color = colors.container, contentColor = colors.content) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    when (status) {
        Status.VERIFIED -> Pill("Verified", StatusColors.verified(), modifier, NpIcons.CheckCircle)
        Status.APPROXIMATE -> Pill("Close match", StatusColors.approximate(), modifier, NpIcons.Warning)
        Status.NEEDS_CHECK -> Pill("Needs check", StatusColors.needsCheck(), modifier, NpIcons.Warning)
    }
}

@Composable
fun ConfirmedBadge(modifier: Modifier = Modifier) {
    Pill("Confirmed by you", StatusColors.confirmed(), modifier, NpIcons.Check)
}

@Composable
fun TypeChip(type: String, modifier: Modifier = Modifier) {
    Overline(text = type, modifier = modifier, color = MaterialTheme.colorScheme.primary)
}

/**
 * Calendar-style date block: month, big day number, weekday. Shows "No date" when [iso] is missing or
 * unparseable. [highlighted] gives it the pale-sage wash (used for verified items).
 */
@Composable
fun DateTile(iso: String?, highlighted: Boolean, modifier: Modifier = Modifier) {
    val date = parseIso(iso)
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(modifier = modifier.width(58.dp), shape = NpShape.Tile, color = bg, contentColor = fg) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (date != null) {
                Text(
                    text = date.month.name.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 28.sp
                )
                Text(
                    text = date.dayOfWeek.name.take(3).lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                )
            } else {
                Text(
                    text = "No\ndate",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** Thin neutral border used for cards and panels instead of shadows. */
@Composable
fun hairline(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
