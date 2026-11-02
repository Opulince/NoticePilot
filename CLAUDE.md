# NoticePilot

Android app: scan a school circular → OCR on device → Gemini extracts items → a deterministic
verifier checks every item against the OCR text → verified items go to the phone's calendar,
the rest are flagged for the user to fix.

This is a hackathon build with a hard time limit. Small, working and boring beats clever.

## Stack (do not add to this without asking)

- Kotlin, Jetpack Compose, single `app` module, single Activity.
- One `MainViewModel` holding a `StateFlow<UiState>`. Simple screen state, no Navigation library.
- Coroutines for async (`kotlinx-coroutines-play-services` for `Task.await()`).
- Capture: ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`),
  JPEG results, gallery import allowed. Fallback button: system photo picker (`PickVisualMedia`).
- OCR: ML Kit Text Recognition, Latin, **bundled** (`com.google.mlkit:text-recognition`).
- LLM: Gemini API called **directly over HTTPS** (no Firebase, no SDK):
  `POST https://generativelanguage.googleapis.com/v1beta/models/{MODEL_NAME}:generateContent`
  with header `x-goog-api-key`. Use OkHttp + org.json (or plain `HttpURLConnection`), on `Dispatchers.IO`,
  60 s timeout. The key comes from `local.properties` (`GEMINI_API_KEY=...`) into `BuildConfig`;
  `local.properties` stays out of git.
  Do NOT use the deprecated Google AI client SDK (`com.google.ai.client.generativeai`).
- Calendar: `Intent.ACTION_INSERT` on `CalendarContract.Events.CONTENT_URI`. No calendar permissions.
- Unit tests: JUnit for the verifier.

Before adding any dependency, check the current version in the official docs. Don't trust remembered
versions. The model name lives in ONE constant (`GeminiConfig.MODEL_NAME`) and must be a current Flash
model that supports structured output.

This is built live in a 2-hour window. Every dependency must already be in the Gradle cache from the
rehearsal: never add one that wasn't used in the rehearsal.

### Out of scope, never build these
Room or any database, Hilt/Koin, accounts, Arabic, conflict detection across notices,
offline extraction, our own notifications/alarms, text-to-speech.

## Packages

```
ui/        MainActivity, ScanScreen, ResultsScreen, SourceScreen, MainViewModel, UiState
scan/      Document Scanner + photo picker launch helpers
ocr/       OcrEngine -> OcrResult
extract/   GeminiExtractor, prompt text, response schema, JSON -> ExtractedItem
verify/    Verifier, Normalizer, DateParser  (PURE KOTLIN: no android.* imports)
calendar/  CalendarIntents
```

## Data contract

```kotlin
data class OcrLine(val id: String, val text: String, val box: android.graphics.Rect?) // id = "L1", "L2"...
data class OcrResult(val lines: List<OcrLine>)   // lines numbered continuously across all pages

data class ExtractedItem(
    val type: String,              // "task" | "event" | "deadline" | "info"
    val title: String,
    val date: String?,             // ISO yyyy-MM-dd
    val endDate: String?,          // ISO, only for ranges
    val time: String?,             // HH:mm, 24h
    val conditions: List<String>,
    val lineIds: List<String>,
    val quote: String,
    val unclearReason: String?
)

enum class Status { VERIFIED, APPROXIMATE, NEEDS_CHECK }
data class CheckedItem(val item: ExtractedItem, val status: Status, val reasons: List<String>)
```

The verifier must not depend on `android.graphics.Rect`. Pass it plain `id + text` pairs.

## Gemini call

- Input: OCR lines only, formatted one per line as `L12: text`. NEVER send the image. The verifier's
  value comes from checking Gemini against text Gemini did not produce.
- Request body: `systemInstruction` (the rules below), `contents` (the numbered lines), and
  `generationConfig` with `responseMimeType: "application/json"` and `responseSchema` (OpenAPI-style:
  `type`, `properties`, `required`, `enum`, `items`). `date`, `end_date`, `time`, `unclear_reason` are
  NOT in `required`. Check the field names against the current Gemini API docs before writing it.
- The JSON comes back as text in `candidates[0].content.parts[0].text`. Parse it defensively.
- System instruction must say:
  - Only extract what the text states. Never invent dates, times, fees or conditions.
  - `quote` = copied character-for-character from the given lines, the shortest span that contains
    the fact (including the date if there is one). `line_ids` = the lines the quote comes from.
  - Dates are day-first (DD/MM) in these documents. Output ISO dates.
  - If the year is missing, or it's unclear which instruction a date belongs to, still give the best
    reading but fill `unclear_reason` in plain words.
  - Skip marketing text, contact details and greetings.

## Verifier rules (pure Kotlin, fully unit tested)

1. `normalize(s)`: lowercase; curly quotes -> straight; en/em dashes -> `-`; collapse all whitespace
   to one space; trim.
2. Quote check against the normalized text of the cited lines joined with a space:
   - substring -> VERIFIED
   - else best sliding-window similarity (1 - levenshtein / quoteLength) >= `FUZZY_THRESHOLD` (0.85,
     one constant) -> APPROXIMATE
   - cited lines missing or no match -> repeat against the whole OCR text; a hit there -> APPROXIMATE
     with reason "cited the wrong line"
   - otherwise NEEDS_CHECK, reason "quote not found in scanned text"
3. Date check, for `date` and `endDate` when present: parse every date in the quote. Support
   `26 October 2026`, `26th Oct 2026`, `October 26, 2026`, `26/10/2026`, `26-10-2026`, `26.10.2026`
   and the same without a year. Some parsed date must equal the item's date, else NEEDS_CHECK
   ("date doesn't match source" / "date not found in quote"). A matching day+month with no year in
   the quote -> NEEDS_CHECK ("year missing in source").
4. `unclearReason != null` -> NEEDS_CHECK with that reason.
5. Final status = the worst one: NEEDS_CHECK > APPROXIMATE > VERIFIED. Keep all reasons.

## UI

- ScanScreen: "Scan notice" (Document Scanner) and "Pick photo" (fallback). Progress text while
  OCR and Gemini run.
- ResultsScreen: two sections, "Verified" and "Needs check". Each card shows type, title, date/time,
  conditions, and the reasons on flagged ones. Tap a card -> SourceScreen. Verified cards with a date
  get an "Add to calendar" button. Needs check cards get an edit action (title/date/time); after the
  user saves, the item counts as confirmed and can be added to the calendar.
- SourceScreen: all OCR lines in order, cited lines highlighted and scrolled into view.
- Error states, never a crash: no internet, Gemini error/timeout, no text found, malformed JSON
  (show "couldn't read this notice, try again").

## How to work

- One step at a time, as asked. Don't run ahead into later steps.
- After every step: `./gradlew assembleDebug` must pass, and `./gradlew testDebugUnitTest` once
  tests exist. Fix failures before saying you're done.
- Then tell me exactly what to try on the phone, and stop.
- Keep files small and readable. No speculative abstractions, interfaces with one implementation,
  or "for later" code.
