package com.intraprenuers.noticepilot.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.intraprenuers.noticepilot.BuildConfig
import com.intraprenuers.noticepilot.extract.ExtractionException
import com.intraprenuers.noticepilot.extract.GeminiExtractor
import com.intraprenuers.noticepilot.ocr.OcrEngine
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.verify.SourceLine
import com.intraprenuers.noticepilot.verify.Verifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** Pipeline: images -> OCR -> Gemini -> Verifier -> Results. All business logic lives here, not in Composables. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UiState>(UiState.Idle())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val ocr = OcrEngine(app)
    private val extractor = GeminiExtractor(BuildConfig.GEMINI_API_KEY)

    /** OCR lines of the last scan, kept so a failed Gemini call can be retried without rescanning. */
    private var lastLines: List<OcrLine> = emptyList()
    private var job: Job? = null

    fun process(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _state.value = UiState.Idle("No pages were scanned.")
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UiState.Working(Stage.READING_TEXT)
            val lines = try {
                ocr.read(uris).lines
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error(
                    message = "Couldn't read that image. Try another photo.",
                    detail = "${e.javaClass.simpleName}: ${e.message}"
                )
                return@launch
            }
            if (lines.isEmpty()) {
                _state.value = UiState.Idle("No text found. Try a clearer, well-lit photo of the notice.")
                return@launch
            }
            lastLines = lines
            extractAndVerify(lines)
        }
    }

    fun retry() {
        val lines = lastLines
        if (lines.isEmpty()) {
            _state.value = UiState.Idle()
            return
        }
        job?.cancel()
        job = viewModelScope.launch { extractAndVerify(lines) }
    }

    private suspend fun extractAndVerify(lines: List<OcrLine>) {
        _state.value = UiState.Working(Stage.EXTRACTING)
        val source = lines.map { SourceLine(it.id, it.text) }
        val items = try {
            extractor.extract(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtractionException) {
            _state.value = UiState.Error(
                message = e.userMessage,
                detail = "${e.kind}: ${e.detail}",
                canRetry = e.kind != ExtractionException.Kind.MISSING_KEY
            )
            return
        } catch (e: Exception) {
            _state.value = UiState.Error(
                message = "Couldn't read this notice. Please try again.",
                detail = "${e.javaClass.simpleName}: ${e.message}",
                canRetry = true
            )
            return
        }
        _state.value = UiState.Working(Stage.VERIFYING)
        val checked = withContext(Dispatchers.Default) { Verifier.verifyAll(items, source) }
        _state.value = UiState.Results(lines, checked.mapIndexed { i, c -> ResultItem(id = i, checked = c) })
    }

    /** [itemId] = -1 opens the scanned text with nothing highlighted. */
    fun openSource(itemId: Int) = updateResults { it.copy(sourceItemId = itemId) }

    /**
     * Validates and stores the user's correction. Returns an error message to show in the dialog,
     * or null on success. A saved item is "confirmed by you" — its AI status is left untouched.
     */
    fun saveEdit(id: Int, title: String, date: String?, time: String?): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return "Title can't be empty."
        val cleanDate = date?.trim()?.takeIf { it.isNotEmpty() }
        val parsedDate = if (cleanDate == null) null else try {
            LocalDate.parse(cleanDate)
        } catch (e: DateTimeParseException) {
            return "Date must look like 2026-10-26 (YYYY-MM-DD)."
        }
        val cleanTime = time?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            normalizeTime(t) ?: return "Time must look like 09:30 (24-hour HH:mm)."
        }
        updateResults { results ->
            results.copy(items = results.items.map { r ->
                if (r.id != id) return@map r
                val old = r.shown
                // Keep an end date only if it still comes on/after the corrected start date.
                val end = old.endDate?.let { e ->
                    try { LocalDate.parse(e) } catch (x: DateTimeParseException) { null }
                }?.takeIf { parsedDate != null && !it.isBefore(parsedDate) }?.toString()
                r.copy(
                    edited = old.copy(title = cleanTitle, date = parsedDate?.toString(), endDate = end, time = cleanTime),
                    confirmed = true
                )
            })
        }
        return null
    }

    /** Handles system back. Returns false when there is nothing to go back to (let the system close the app). */
    fun back(): Boolean {
        when (val s = _state.value) {
            is UiState.Idle -> return false
            is UiState.Working -> { job?.cancel(); _state.value = UiState.Idle() }
            is UiState.Results ->
                _state.value = if (s.sourceItemId != null) s.copy(sourceItemId = null) else UiState.Idle()
            is UiState.Error -> _state.value = UiState.Idle()
        }
        return true
    }

    fun reset() {
        job?.cancel()
        _state.value = UiState.Idle()
    }

    fun onScanCancelled() {
        if (_state.value !is UiState.Working) _state.value = UiState.Idle()
    }

    fun onScannerUnavailable(reason: String?) {
        _state.value = UiState.Idle(
            "Document scanner isn't available on this phone right now (${reason ?: "unknown error"}). Use Pick photo instead."
        )
    }

    private fun updateResults(change: (UiState.Results) -> UiState.Results) {
        _state.update { if (it is UiState.Results) change(it) else it }
    }

    override fun onCleared() {
        ocr.close()
    }

    private companion object {
        private val TIME = Regex("""^([01]?\d|2[0-3])[:.]([0-5]\d)$""")

        /** "9:30" / "09.30" -> "09:30"; anything else -> null. */
        fun normalizeTime(t: String): String? {
            val m = TIME.matchEntire(t) ?: return null
            return String.format(Locale.ROOT, "%02d:%s", m.groupValues[1].toInt(), m.groupValues[2])
        }
    }
}
