package com.intraprenuers.noticepilot

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intraprenuers.noticepilot.calendar.CalendarIntents
import com.intraprenuers.noticepilot.capture.CaptureActions
import com.intraprenuers.noticepilot.capture.CaptureHandoff
import com.intraprenuers.noticepilot.capture.ScreenCaptureService
import com.intraprenuers.noticepilot.share.ShareIntents
import com.intraprenuers.noticepilot.scan.DocumentScan
import com.intraprenuers.noticepilot.scan.PdfPicker
import com.intraprenuers.noticepilot.scan.PhotoPicker
import com.intraprenuers.noticepilot.ui.ErrorScreen
import com.intraprenuers.noticepilot.ui.MainViewModel
import com.intraprenuers.noticepilot.ui.NoticePilotTheme
import com.intraprenuers.noticepilot.ui.ResultItem
import com.intraprenuers.noticepilot.ui.ResultsScreen
import com.intraprenuers.noticepilot.ui.ScanScreen
import com.intraprenuers.noticepilot.ui.SourceScreen
import com.intraprenuers.noticepilot.ui.UiState
import com.intraprenuers.noticepilot.ui.components.Backdrop

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

    private val pdfLauncher = registerForActivityResult(PdfPicker.contract) { uri ->
        if (uri != null) viewModel.processPdf(uri) // null = user cancelled: stay on home
    }

    /** Screen-capture consent (one token per capture: Android never lets us reuse it). */
    private val captureConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            try {
                ScreenCaptureService.start(this, result.resultCode, data)
                viewModel.onCaptureStarted()
                // Step aside so the capture shows the notice, not NoticePilot. No toast here: it would be
                // on screen during the capture and get OCR'd as part of the notice.
                moveTaskToBack(true)
            } catch (e: Exception) {
                CaptureHandoff.inProgress = false
                viewModel.onCaptureFailed("Screen capture couldn't start (${e.javaClass.simpleName}).")
            }
        } else {
            viewModel.onCaptureFailed("Screen capture cancelled.")
        }
    }

    /** Asked once, on first tile use, only for the "Screen captured, tap to read" notification. */
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchCaptureConsent() // continue either way
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoticePilotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                  Box(modifier = Modifier.fillMaxSize()) {
                    Backdrop(Modifier.fillMaxSize()) // dark theme only: ambient green light + faint grain
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val history by viewModel.history.collectAsStateWithLifecycle()
                    BackHandler(enabled = state !is UiState.Idle) { viewModel.back() }
                    when (val s = state) {
                        is UiState.Idle -> ScanScreen(
                            stage = null, notice = s.notice, recent = history,
                            onScan = ::startScan, onPickPhoto = ::pickPhoto, onImportPdf = ::importPdf,
                            onOpenNotice = viewModel::openNotice, onDeleteNotice = viewModel::deleteNotice
                        )
                        is UiState.Working -> ScanScreen(
                            stage = s.stage, notice = null, recent = emptyList(),
                            onScan = {}, onPickPhoto = {}, onImportPdf = {},
                            onOpenNotice = {}, onDeleteNotice = {},
                            progressDetail = s.detail, fromPdf = s.fromPdf
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
        // Only on a fresh start: after rotation the system re-delivers the original intent.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // A finished screen capture waits here even if Android didn't let the service bring us forward.
        when (val outcome = CaptureHandoff.take()) {
            is CaptureHandoff.Outcome.Captured -> viewModel.onScreenCaptured(outcome.file)
            is CaptureHandoff.Outcome.Failed -> viewModel.onCaptureFailed(outcome.message)
            null -> Unit
        }
    }

    /** Share sheet, Quick Settings tile and capture-done intents. Each intent is handled once. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            CaptureActions.ACTION_TILE_CAPTURE -> startTileCapture()
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> when (val shared = ShareIntents.parse(intent)) {
                is ShareIntents.Shared.Single -> viewModel.onSharedFileReceived(shared.uri, shared.mimeType)
                is ShareIntents.Shared.Multiple -> viewModel.onSharedFilesReceived(shared.uris)
                null -> toast("Nothing NoticePilot can read was shared.")
            }
            else -> return // MAIN, CAPTURE_DONE (picked up in onResume), etc.
        }
        intent.action = null
    }

    private fun startTileCapture() {
        if (CaptureHandoff.inProgress) {
            toast("A screen capture is already in progress.")
            return
        }
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !askedForNotifications
        if (needsAsk) {
            askedForNotifications = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchCaptureConsent()
        }
    }

    private fun launchCaptureConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            viewModel.onCaptureFailed("Screen capture isn't available on this phone.")
            return
        }
        try {
            captureConsentLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: ActivityNotFoundException) {
            viewModel.onCaptureFailed("Screen capture isn't available on this phone.")
        }
    }

    private fun startScan() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> viewModel.onScannerUnavailable(e.message) }
    }

    private fun pickPhoto() = pickLauncher.launch(PhotoPicker.imageRequest())

    private fun importPdf() {
        try {
            pdfLauncher.launch(PdfPicker.mimeTypes)
        } catch (e: ActivityNotFoundException) {
            toast("No file picker found on this phone.")
        }
    }

    private fun addToCalendar(item: ResultItem) {
        if (!item.canAddToCalendar) return
        when (CalendarIntents.insert(this, item.shown)) {
            CalendarIntents.Result.OPENED -> Unit
            CalendarIntents.Result.NO_DATE -> toast("This item has no valid date.")
            CalendarIntents.Result.NO_CALENDAR_APP -> toast("No calendar app found on this phone.")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        /** Per process: we ask for the notification permission at most once per app run. */
        var askedForNotifications = false
    }
}
