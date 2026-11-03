package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.ui.components.ConfirmedBadge
import com.intraprenuers.noticepilot.ui.components.StatusBadge

/** "12" means "L12"; everything is trimmed and upper-cased. */
private fun normalizeId(raw: String): String {
    val t = raw.trim().uppercase()
    return if (t.isNotEmpty() && t.all { it.isDigit() }) "L$t" else t
}

/** One row of the list: a page divider (page != null) or a text line. */
private class SourceRow(val page: Int?, val line: OcrLine?)

@Composable
fun SourceScreen(lines: List<OcrLine>, item: ResultItem?, onBack: () -> Unit) {
    val citedIds = remember(item?.id, item?.checked) {
        item?.checked?.item?.lineIds?.map { normalizeId(it) }?.toSet() ?: emptySet()
    }
    val rows = remember(lines) {
        val multiPage = lines.map { it.page }.distinct().size > 1
        val out = ArrayList<SourceRow>()
        var lastPage = Int.MIN_VALUE
        for (line in lines) {
            if (multiPage && line.page != lastPage) {
                out.add(SourceRow(line.page, null))
                lastPage = line.page
            }
            out.add(SourceRow(null, line))
        }
        out
    }
    val firstCitedIndex = remember(rows, citedIds) {
        rows.indexOfFirst { r ->
            val l = r.line
            l != null && normalizeId(l.id) in citedIds
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(item?.id, firstCitedIndex) {
        if (firstCitedIndex >= 0) listState.animateScrollToItem(firstCitedIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                text = "Scanned text",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (item != null) {
            ComparisonPanel(item)
        }

        if (lines.isEmpty()) {
            Text(
                text = "No text was recognised.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rows) { r ->
                    val line = r.line
                    if (line == null) {
                        Text(
                            text = "Page ${r.page}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        LineRow(line, normalizeId(line.id) in citedIds)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: OcrLine, cited: Boolean) {
    val bg = if (cited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val fg = if (cited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = line.id,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (cited) fg else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cited) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
    }
}

@Composable
private fun ComparisonPanel(item: ResultItem) {
    val checked = item.checked
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.shown.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(checked.status)
                if (item.confirmed) ConfirmedBadge()
            }
            Text(
                text = "AI quote:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\"${checked.item.quote}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic
            )
            for (reason in checked.reasons) {
                Text(
                    text = "⚠ $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Cited lines: " + checked.item.lineIds.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
