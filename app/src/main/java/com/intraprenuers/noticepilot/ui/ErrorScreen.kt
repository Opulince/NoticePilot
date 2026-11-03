package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.intraprenuers.noticepilot.ui.components.BrandMark
import com.intraprenuers.noticepilot.ui.components.NpIcons
import com.intraprenuers.noticepilot.ui.components.Overline
import com.intraprenuers.noticepilot.ui.components.hairline

@Composable
fun ErrorScreen(
    message: String,
    detail: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val c = StatusColors.needsCheck()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        BrandMark()
        Spacer(Modifier.height(48.dp))
        Surface(shape = NpShape.Tile, color = c.container, contentColor = c.content, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(NpIcons.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (canRetry) "Your scanned text is kept, so trying again won't need a new scan."
            else "Nothing was saved from this attempt.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (detail != null) {
            Spacer(Modifier.height(20.dp))
            Overline("Details")
            Spacer(Modifier.height(6.dp))
            Surface(shape = NpShape.Card, color = MaterialTheme.colorScheme.surface, border = hairline(), modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (canRetry) {
            Button(onClick = onRetry, shape = NpShape.Control, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Try again")
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onBack,
            shape = NpShape.Control,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Back to home")
        }
    }
}
