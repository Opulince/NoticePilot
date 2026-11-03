package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.intraprenuers.noticepilot.ui.components.ConfirmedBadge
import com.intraprenuers.noticepilot.ui.components.StatusBadge
import com.intraprenuers.noticepilot.ui.components.TypeChip
import com.intraprenuers.noticepilot.ui.components.formatWhen
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Your notice",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (state.items.isNotEmpty()) {
                    Text(
                        text = "${verified.size} verified · ${attention.size} need your attention",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onScanAnother, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan another notice")
                }
            }
        }

        if (state.items.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No dates or tasks found in this notice.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onOpenSource(-1) }) {
                        Text("View scanned text")
                    }
                }
            }
        }

        if (verified.isNotEmpty()) {
            item { SectionHeader("Verified") }
            items(verified, key = { it.id }) { item ->
                ResultCard(item, onOpenSource, onAddToCalendar, onSaveEdit)
            }
        }

        if (attention.isNotEmpty()) {
            item { SectionHeader("Needs your attention") }
            items(attention, key = { it.id }) { item ->
                ResultCard(item, onOpenSource, onAddToCalendar, onSaveEdit)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSource(item.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeChip(shown.type)
            Text(
                text = shown.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (whenText != null) {
                Text(text = whenText, style = MaterialTheme.typography.bodyMedium)
            }
            for (condition in shown.conditions) {
                Text(text = "• $condition", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(status)
                if (item.confirmed) ConfirmedBadge()
            }
            if (status != Status.VERIFIED) {
                for (reason in item.checked.reasons) {
                    Text(
                        text = "⚠ $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.edited != null) {
                Text(
                    text = "Edited by you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.canAddToCalendar) {
                Button(onClick = { onAddToCalendar(item) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to calendar")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status != Status.VERIFIED) {
                    TextButton(onClick = { editing = true }) { Text("Fix & confirm") }
                }
                TextButton(onClick = { onOpenSource(item.id) }) { Text("View source") }
            }
        }
    }

    if (editing) {
        EditDialog(item, onSaveEdit) { editing = false }
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time") },
                    placeholder = { Text("HH:mm, optional") },
                    singleLine = true,
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
        }
    )
}
