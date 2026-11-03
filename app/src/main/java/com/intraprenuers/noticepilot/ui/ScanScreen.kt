package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.intraprenuers.noticepilot.storage.SavedNotice
import com.intraprenuers.noticepilot.ui.components.BrandMark
import com.intraprenuers.noticepilot.ui.components.NpIcons
import com.intraprenuers.noticepilot.ui.components.Overline
import com.intraprenuers.noticepilot.ui.components.hairline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val savedFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

/** Home: what the app does, the scan actions and previously scanned notices. Shows progress while [stage] != null. */
@Composable
fun ScanScreen(
    stage: Stage?,
    notice: String?,
    recent: List<SavedNotice>,
    onScan: () -> Unit,
    onPickPhoto: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenNotice: (Long) -> Unit,
    onDeleteNotice: (Long) -> Unit,
    progressDetail: String? = null,
    fromPdf: Boolean = false
) {
    if (stage != null) {
        ProcessingView(stage, progressDetail, fromPdf)
        return
    }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
          Column {
            BrandMark(logoSize = 40.dp)
            Spacer(Modifier.height(36.dp))
            Text(
                text = "School notices, turned into dates you can trust.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Scan a circular. NoticePilot reads it on your phone, pulls out the deadlines and events, " +
                    "and checks each one against the original text before it reaches your calendar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            WorkflowLine()
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onScan,
                shape = NpShape.Control,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(NpIcons.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Scan notice", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImportButton("Import image", NpIcons.Photo, onPickPhoto, Modifier.weight(1f))
                ImportButton("Import PDF", NpIcons.Pdf, onImportPdf, Modifier.weight(1f))
            }
            if (notice != null) {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(notice)
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Recent notices")
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Overline("${recent.size}", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (recent.isEmpty()) {
                EmptyRecent()
            } else {
                RecentList(recent, onOpen = onOpenNotice, onDelete = { pendingDelete = it })
            }
          }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this notice?") },
            text = { Text("Its extracted items and scanned text will be removed from this phone. Events you already added to your calendar stay there.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteNotice(deleting)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NpShape.Card
        )
    }
}

@Composable
private fun ImportButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = NpShape.Control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.height(52.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Scan — Extract — Verify": the workflow in one quiet line; verification carries the accent. */
@Composable
private fun WorkflowLine() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("Scan  —  Extract  —  ") }
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("Verify") }
            withStyle(SpanStyle(color = muted)) { append("  —  Calendar") }
        },
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun NoticeBanner(text: String) {
    val c = StatusColors.approximate()
    Surface(shape = NpShape.Card, color = c.container, contentColor = c.content, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(NpIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyRecent() {
    Surface(
        shape = NpShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border = hairline(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                NpIcons.Document,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nothing scanned yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Every notice you scan is kept here with its checked dates, so you can come back to it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Saved notices as one bordered list with hairline dividers. */
@Composable
private fun RecentList(recent: List<SavedNotice>, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Surface(shape = NpShape.Card, color = MaterialTheme.colorScheme.surface, border = hairline(), modifier = Modifier.fillMaxWidth()) {
        Column {
            recent.forEachIndexed { index, n ->
                RecentRow(notice = n, onOpen = { onOpen(n.id) }, onDelete = { onDelete(n.id) })
                if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun RecentRow(notice: SavedNotice, onOpen: () -> Unit, onDelete: () -> Unit) {
    val verified = notice.items.count { it.isVerified }
    val attention = notice.items.size - verified
    val saved = Instant.ofEpochMilli(notice.savedAt).atZone(ZoneId.systemDefault()).format(savedFormat)
    val verifiedColor = StatusColors.verified().content
    val attentionColor = StatusColors.needsCheck().content
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = NpShape.Tile, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    NpIcons.Document,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = notice.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$saved · ${notice.items.size} ${if (notice.items.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            if (notice.items.isNotEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = verifiedColor, fontWeight = FontWeight.SemiBold)) {
                            append("$verified verified")
                        }
                        if (attention > 0) {
                            withStyle(SpanStyle(color = muted)) { append("  ·  ") }
                            withStyle(SpanStyle(color = attentionColor, fontWeight = FontWeight.SemiBold)) {
                                append("$attention to check")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(NpIcons.Delete, contentDescription = "Delete notice", tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Calm, honest progress: earlier steps are shown done only because they really finished. */
@Composable
private fun ProcessingView(stage: Stage, detail: String?, fromPdf: Boolean) {
    val steps = listOf(
        if (fromPdf) "Opening PDF" to Stage.OPENING_PDF else "Document captured" to null,
        "Reading text" to Stage.READING_TEXT,
        "Extracting information" to Stage.EXTRACTING,
        "Verifying evidence" to Stage.VERIFYING,
        "Preparing results" to Stage.PREPARING
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        BrandMark()
        Spacer(Modifier.height(48.dp))
        Text("Working on your notice", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            detail ?: stage.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Surface(shape = NpShape.Card, color = MaterialTheme.colorScheme.surface, border = hairline(), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                steps.forEachIndexed { i, (label, s) ->
                    val state = when {
                        s == null || s.ordinal < stage.ordinal -> StepState.DONE
                        s == stage -> StepState.CURRENT
                        else -> StepState.PENDING
                    }
                    StepRow(
                        label = label,
                        state = state,
                        note = when {
                            s == Stage.VERIFYING -> "Every item is checked against the scanned text, word by word."
                            s == Stage.READING_TEXT && state == StepState.CURRENT && detail != null -> detail
                            else -> null
                        }
                    )
                    if (i < steps.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 52.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class StepState { DONE, CURRENT, PENDING }

@Composable
private fun StepRow(label: String, state: StepState, note: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.DONE -> Icon(
                    NpIcons.CheckCircle,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                StepState.CURRENT -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                StepState.PENDING -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state == StepState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
                color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
