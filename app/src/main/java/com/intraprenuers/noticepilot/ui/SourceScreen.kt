package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.ui.components.ConfirmedBadge
import com.intraprenuers.noticepilot.ui.components.NpIcons
import com.intraprenuers.noticepilot.ui.components.Overline
import com.intraprenuers.noticepilot.ui.components.QuoteMatch
import com.intraprenuers.noticepilot.ui.components.StatusBadge
import com.intraprenuers.noticepilot.ui.components.hairline

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
    val quote = item?.checked?.item?.quote.orEmpty()
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
        // Leave one line of context above the first cited line.
        if (firstCitedIndex >= 0) listState.animateScrollToItem((firstCitedIndex - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(NpIcons.Back, contentDescription = "Back to results")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Source text", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${lines.size} lines read on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (item != null) {
            // Pages the cited lines are on, shown only for multi-page notices (PDFs, multi-page scans).
            val multiPage = lines.map { it.page }.distinct().size > 1
            val citedPages = if (multiPage) lines.filter { normalizeId(it.id) in citedIds }.map { it.page }.distinct() else emptyList()
            EvidencePanel(item, citedPages)
        }

        if (lines.isEmpty()) {
            Text(
                text = "No text was recognised.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            Surface(
                shape = NpShape.Card,
                color = MaterialTheme.colorScheme.surface,
                border = hairline(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(rows) { r ->
                        val line = r.line
                        if (line == null) {
                            PageDivider(r.page ?: 1)
                        } else {
                            LineRow(line, cited = normalizeId(line.id) in citedIds, quote = quote)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDivider(page: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Overline("Page $page")
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A line of the notice. Cited lines get a pale sage wash and a green rule; the exact quote is emphasised. */
@Composable
private fun LineRow(line: OcrLine, cited: Boolean, quote: String) {
    val accent = MaterialTheme.colorScheme.primary
    val wash = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onSurface
    val text: AnnotatedString = if (cited) highlight(line.text, quote, MaterialTheme.colorScheme.onPrimaryContainer) else AnnotatedString(line.text)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cited) {
                    Modifier
                        .background(wash)
                        .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Text(
            text = line.id.removePrefix("L"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            textAlign = TextAlign.End,
            color = if (cited) accent else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .width(26.dp)
                .padding(top = 4.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

/** Emphasises (semi-bold, darker ink) the quoted span inside a cited line. */
private fun highlight(line: String, quote: String, strong: Color): AnnotatedString {
    val range = QuoteMatch.rangeIn(line, quote) ?: return AnnotatedString(line)
    return buildAnnotatedString {
        append(line.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) {
            append(line.substring(range.first, range.last + 1))
        }
        append(line.substring(range.last + 1))
    }
}

/** What the AI claimed and why: the supporting quote set like a pull quote, then status and reasons. */
@Composable
private fun EvidencePanel(item: ResultItem, citedPages: List<Int>) {
    val checked = item.checked
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = NpShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border = hairline(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Capped height so long quotes never push the source text off screen.
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(item.shown.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(checked.status)
                if (item.confirmed) ConfirmedBadge()
            }
            Overline("Supporting quote")
            Text(
                text = if (checked.item.quote.isBlank()) "(no quote given)" else "“${checked.item.quote}”",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .drawBehind { drawRect(accent, size = Size(2.dp.toPx(), size.height)) }
                    .padding(start = 12.dp)
            )
            for (reason in checked.reasons) {
                Text(
                    text = "—  " + reason.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (checked.item.lineIds.isNotEmpty()) {
                val pagePart = when (citedPages.size) {
                    0 -> ""
                    1 -> " · page ${citedPages[0]}"
                    else -> " · pages " + citedPages.joinToString(", ")
                }
                Text(
                    text = "Cited lines " + checked.item.lineIds.joinToString(", ") { it.removePrefix("L") } + pagePart,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

