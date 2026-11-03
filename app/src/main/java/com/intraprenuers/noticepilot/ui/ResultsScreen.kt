package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intraprenuers.noticepilot.ui.components.ConfirmedBadge
import com.intraprenuers.noticepilot.ui.components.DateTile
import com.intraprenuers.noticepilot.ui.components.NpIcons
import com.intraprenuers.noticepilot.ui.components.Overline
import com.intraprenuers.noticepilot.ui.components.StatusBadge
import com.intraprenuers.noticepilot.ui.components.TypeChip
import com.intraprenuers.noticepilot.ui.components.formatWhen
import com.intraprenuers.noticepilot.ui.components.hairline
import com.intraprenuers.noticepilot.verify.Status

@Composable
fun ResultsScreen(
    state: UiState.Results,
    onOpenSource: (Int) -> Unit,
    onAddToCalendar: (ResultItem) -> Unit,
    onSaveEdit: (id: Int, title: String, date: String?, time: String?) -> String?,
    onScanAnother: () -> Unit
) {
    val verified = state.items.filter { it.isVerified }
    val attention = state.items.filter { !it.isVerified }
    // Same naming rule as the saved-notice list, so a notice has one title everywhere.
    val heading = state.items.firstOrNull()?.shown?.title ?: state.lines.firstOrNull()?.text ?: "Scanned notice"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TopBar(onBack = onScanAnother, onFullText = { onOpenSource(-1) })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Overline("Scanned notice")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = run {
                            val pages = state.lines.map { it.page }.distinct().size
                            val pagePart = if (pages > 1) "$pages pages · " else ""
                            "$pagePart${state.lines.size} lines read · ${state.items.size} ${if (state.items.size == 1) "item" else "items"} found"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val note = state.note
                    if (note != null) {
                        Spacer(Modifier.height(12.dp))
                        NoteBanner(note)
                    }
                    if (state.items.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Summary(verified.size, attention.size)
                    }
                }
            }

            if (state.items.isEmpty()) {
                item { EmptyResults(onViewText = { onOpenSource(-1) }) }
            }

            if (verified.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Verified",
                        count = verified.size,
                        icon = NpIcons.CheckCircle,
                        tint = StatusColors.verified().content,
                        caption = "Found word for word in the scanned text."
                    )
                }
                items(verified, key = { it.id }) { item ->
                    ResultCard(item, onOpenSource, onAddToCalendar, onSaveEdit)
                }
            }

            if (attention.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Needs your attention",
                        count = attention.size,
                        icon = NpIcons.Warning,
                        tint = StatusColors.needsCheck().content,
                        caption = "Couldn't be fully matched to the text. Check the source, then fix & confirm."
                    )
                }
                items(attention, key = { it.id }) { item ->
                    ResultCard(item, onOpenSource, onAddToCalendar, onSaveEdit)
                }
            }
        }
    }
}

@Composable
private fun NoteBanner(text: String) {
    val c = StatusColors.approximate()
    Surface(shape = NpShape.Card, color = c.container, contentColor = c.content, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NpIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onFullText: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(NpIcons.Back, contentDescription = "Back to home")
        }
        Text("Results", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onFullText) { Text("Full text") }
    }
}

/** Two quiet numbers on one neutral strip: colour only on the numerals, so it never reads as a traffic light. */
@Composable
private fun Summary(verified: Int, attention: Int) {
    Surface(shape = NpShape.Card, color = MaterialTheme.colorScheme.surface, border = hairline(), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 14.dp)) {
            SummaryCell(verified, "verified", StatusColors.verified().content, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.height(44.dp))
            SummaryCell(attention, if (attention == 1) "needs attention" else "need attention", StatusColors.needsCheck().content, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(count: Int, label: String, numberColor: Color, modifier: Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = count.toString(),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else numberColor
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, icon: ImageVector, tint: Color, caption: String) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyResults(onViewText: () -> Unit) {
    Surface(shape = NpShape.Card, color = MaterialTheme.colorScheme.surface, border = hairline(), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(NpIcons.Document, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
            Text("No dates or tasks found", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "The text was read, but nothing in it looked like an event, deadline or task. You can still read the scanned text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onViewText, shape = NpShape.Control) { Text("View scanned text") }
        }
    }
}

@Composable
private fun ResultCard(
    item: ResultItem,
    onOpenSource: (Int) -> Unit,
    onAddToCalendar: (ResultItem) -> Unit,
    onSaveEdit: (id: Int, title: String, date: String?, time: String?) -> String?
) {
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    val shown = item.shown
    val whenText = formatWhen(shown.date, shown.endDate, shown.time)
    val status = item.checked.status

    Surface(
        shape = NpShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border = hairline(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.clickable { onOpenSource(item.id) }.padding(14.dp)) {
            Row {
                DateTile(iso = shown.date, highlighted = item.isVerified || item.confirmed)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeChip(shown.type)
                    Text(text = shown.title, style = MaterialTheme.typography.titleMedium)
                    if (whenText != null) {
                        Text(
                            text = whenText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    for (condition in shown.conditions) {
                        Text(
                            text = "•  $condition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status)
                if (item.confirmed) ConfirmedBadge()
            }
            if (item.edited != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Edited by you — the AI status above is for the original reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (status != Status.VERIFIED && item.checked.reasons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (reason in item.checked.reasons) {
                        Text(
                            text = "—  " + reason.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenSource(item.id) }) { Text("Source") }
                if (status != Status.VERIFIED) {
                    TextButton(onClick = { editing = true }) {
                        Icon(NpIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.confirmed) "Edit" else "Fix & confirm")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (item.canAddToCalendar && item.isVerified) CalendarButton { onAddToCalendar(item) }
            }
            // A confirmed (not verified) item already has two text actions: put the calendar action on its own line.
            if (item.canAddToCalendar && !item.isVerified) {
                CalendarButton(Modifier.fillMaxWidth()) { onAddToCalendar(item) }
            }
        }
    }

    if (editing) {
        EditDialog(item, onSaveEdit) { editing = false }
    }
}

@Composable
private fun CalendarButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = NpShape.Control,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = modifier
    ) {
        Icon(NpIcons.Event, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add to calendar")
    }
}

@Composable
private fun EditDialog(
    item: ResultItem,
    onSaveEdit: (id: Int, title: String, date: String?, time: String?) -> String?,
    onDismiss: () -> Unit
) {
    val shown = item.shown
    var title by rememberSaveable(item.id) { mutableStateOf(shown.title) }
    var date by rememberSaveable(item.id) { mutableStateOf(shown.date ?: "") }
    var time by rememberSaveable(item.id) { mutableStateOf(shown.time ?: "") }
    var error by rememberSaveable(item.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix & confirm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Check the source, correct anything wrong, then save. The item will be marked as confirmed by you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    shape = NpShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    shape = NpShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time") },
                    placeholder = { Text("HH:mm, optional") },
                    singleLine = true,
                    shape = NpShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
                val currentError = error
                if (currentError != null) {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSaveEdit(
                    item.id,
                    title.trim(),
                    date.trim().ifBlank { null },
                    time.trim().ifBlank { null }
                )
                if (result == null) onDismiss() else error = result
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = NpShape.Card
    )
}
