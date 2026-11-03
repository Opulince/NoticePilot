package com.intraprenuers.noticepilot

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intraprenuers.noticepilot.calendar.CalendarIntents
import com.intraprenuers.noticepilot.scan.DocumentScan
import com.intraprenuers.noticepilot.scan.PhotoPicker
import com.intraprenuers.noticepilot.ui.ErrorScreen
import com.intraprenuers.noticepilot.ui.MainViewModel
import com.intraprenuers.noticepilot.ui.NoticePilotTheme
import com.intraprenuers.noticepilot.ui.ResultItem
import com.intraprenuers.noticepilot.ui.ResultsScreen
import com.intraprenuers.noticepilot.ui.ScanScreen
import com.intraprenuers.noticepilot.ui.SourceScreen
import com.intraprenuers.noticepilot.ui.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val scanner by lazy { DocumentScan.client() }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val uris = DocumentScan.pageUris(result.resultCode, result.data)
        if (uris == null) viewModel.onScanCancelled() else viewModel.process(uris)
    }

    private val pickLauncher = registerForActivityResult(PhotoPicker.contract) { uri ->
        if (uri != null) viewModel.process(listOf(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoticePilotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val history by viewModel.history.collectAsStateWithLifecycle()
                    BackHandler(enabled = state !is UiState.Idle) { viewModel.back() }
                    when (val s = state) {
                        is UiState.Idle -> ScanScreen(
                            stage = null, notice = s.notice, recent = history,
                            onScan = ::startScan, onPickPhoto = ::pickPhoto,
                            onOpenNotice = viewModel::openNotice, onDeleteNotice = viewModel::deleteNotice
                        )
                        is UiState.Working -> ScanScreen(
                            stage = s.stage, notice = null, recent = emptyList(),
                            onScan = {}, onPickPhoto = {},
                            onOpenNotice = {}, onDeleteNotice = {}
                        )
                        is UiState.Results -> {
                            val sourceId = s.sourceItemId
                            if (sourceId == null) {
                                ResultsScreen(
                                    state = s,
                                    onOpenSource = viewModel::openSource,
                                    onAddToCalendar = ::addToCalendar,
                                    onSaveEdit = viewModel::saveEdit,
                                    onScanAnother = viewModel::reset
                                )
                            } else {
                                SourceScreen(
                                    lines = s.lines,
                                    item = s.items.firstOrNull { it.id == sourceId },
                                    onBack = { viewModel.back() }
                                )
                            }
                        }
                        is UiState.Error -> ErrorScreen(
                            message = s.message, detail = s.detail, canRetry = s.canRetry,
                            onRetry = viewModel::retry, onBack = viewModel::reset
                        )
                    }
                }
            }
        }
    }

    private fun startScan() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> viewModel.onScannerUnavailable(e.message) }
    }

    private fun pickPhoto() = pickLauncher.launch(PhotoPicker.imageRequest())

    private fun addToCalendar(item: ResultItem) {
        if (!item.canAddToCalendar) return
        when (CalendarIntents.insert(this, item.shown)) {
            CalendarIntents.Result.OPENED -> Unit
            CalendarIntents.Result.NO_DATE -> toast("This item has no valid date.")
            CalendarIntents.Result.NO_CALENDAR_APP -> toast("No calendar app found on this phone.")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
