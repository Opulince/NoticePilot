# NoticePilot — Development Handoff

## 1. Current Status
- Overall state: the full pipeline is done (scan / import image / import PDF → OCR → Gemini → verifier → results → source → fix & confirm → calendar). It also has the Gemini model fallback, saved notices, the editorial UI and Siva's logo.
- Current milestone: laptop build plus on-phone check of PDF import, the redesign, saved notices and the logo.
- Last completed action: PDF import. OCR, the ViewModel and all screens type-check; 118/118 JVM unit tests pass.
- Current blocker: none. The cloud dev env can't run an Android build (`dl.google.com` blocked), so build on the laptop.

## 2. Completed Work
- [x] Skeleton: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, compile/targetSdk 36, minSdk 26. Versions are in `gradle/libs.versions.toml`.
  - Key: `GEMINI_API_KEY` comes from `local.properties` into BuildConfig. Laptop SDK: `sdk.dir=G:/PROGRAMS/andriod/Sdk`.
- [x] scan/ (ML Kit Document Scanner + PickVisualMedia), ocr/ (`OcrEngine`, lines L1.. across pages), calendar/ (ACTION_INSERT; all-day if no time).
- [x] **PDF import** (no new dependency):
  - Picker: `scan/PdfPicker` = SAF `OpenDocument("application/pdf")`, launched from MainActivity `pdfLauncher` (device storage and cloud, no permissions). Cancel = stay home.
  - Reading: `OcrEngine.readPdf(uri, onOpened, onPage)` copies the content URI to `cacheDir/import.pdf` (≤50 MB; picker URIs can be non-seekable or short-lived), opens it with `PdfRenderer`, then renders one page at a time onto a white ARGB bitmap.
  - Pages: long side 2400 px, sized by `PdfLimits.renderSize`; ML Kit OCR reads each page, then the bitmap is recycled. Max 10 pages (`PdfLimits.MAX_PAGES`); the results show a note when pages are skipped.
  - Cleanup: renderer, fd and the cached copy are always closed or deleted (nested finally). Cancellable between pages.
  - Text PDFs and scanned PDFs both go through OCR on the rendered pages; there is NO native PDF text extraction.
  - Errors: `PdfException(userMessage)` for password-protected (SecurityException), damaged (IOException), empty (0 pages or 0 bytes), too large, unreadable URI, page render failure and OOM. No text on any page → home notice.
  - Lines use the same continuous `L1..` ids plus `page` (not `P1-L1`): unique across the document and compatible with prompt, verifier, parser and saved notices.
  - Progress: `Working(stage, detail, fromPdf)`. Steps: Opening PDF → Reading text ("Reading page X of Y") → Extracting → Verifying → Preparing. Retry keeps the PDF context.
  - UI: home has Scan notice plus Import image | Import PDF side by side. Results heading shows the page count; the Source evidence panel shows the cited page(s).
- [x] extract/: OkHttp `generateContent` call, `responseMimeType` + `responseSchema`, defensive parser.
  - **Model fallback**: `GeminiConfig.MODELS` = 3.6-flash → 3.8-flash → 3.5-flash-lite.
  - Falls back to the next model on 503/5xx, 429, timeout, other HTTP errors (e.g. 404), empty, blocked or bad JSON.
  - Does NOT fall back on no internet, bad key or missing key; it throws at once.
  - When every model is busy: "Gemini is busy right now…" with Try again.
- [x] verify/: pure Kotlin `Normalizer`, `DateParser`, `Verifier` (CLAUDE.md rules 1–5).
- [x] **Saved notices** (storage/): `NoticeStore` keeps one JSON file, `filesDir/notices.json`, written via temp file + rename, newest 30 kept.
  - `NoticeJson` handles encode/decode. Bounding boxes aren't saved. Corrupt entries are skipped.
  - ViewModel `history: StateFlow<List<SavedNotice>>` loads at start and saves after each scan, edit and delete. `UiState.Results.noticeId` links results to their saved notice.
- [x] **UI redesign** ("editorial precision"):
  - Look: paper #F7F6F2, ink #202321, forest green #245B49 accent. Serif titles, flat surfaces with hairline borders, 8/10/12dp radii, no gradients.
  - Home: wordmark, headline, workflow line, Scan notice, Import a photo, then a Recent notices list (title, date, item count, verified/to-check summary, delete with confirm).
  - Processing: 5 honest steps. "Document captured" is done once we're working, and the Verifying step carries an explanation.
  - Results: heading, two-number summary, Verified / Needs your attention sections, cards with a date tile, status badge, reasons and actions.
  - Source: reader-style lines with cited lines washed sage plus a green rule. The exact quote is semi-bold inside them (`QuoteMatch`), with a pull-quote evidence panel.
  - Error screen restyled; detail is shown in monospace.
  - Icons: `ui/components/Icons.kt` (Material path data, no dependency).
- [x] **Siva's logo**. The original `WhatsApp Image 2026-11-03 at 7.07.08 AM.jpeg` (1024×1024, square, green rounded tile on white corners) is kept unmodified in the repo root:
  - In-app: `res/drawable-nodpi/noticepilot_logo.png`, the same artwork with only its white JPEG corners made transparent, 192 px. Shown square, 40dp on home and 30dp on processing/error, via `BrandMark`/`Brand.LOGO_RES`. Not on results or source.
  - Launcher: an adaptive icon (`mipmap-anydpi-v26/ic_launcher*.xml` + `drawable-nodpi/ic_launcher_foreground.png`). The artwork is scaled into the 66dp safe zone and the gradient extended to the edges. Checked under circle and squircle masks.

## 3. Current Work
- [ ] On-phone check: PDF import (1-page, multi-page, >10 pages, password-protected, image-only), the redesign, saved notices surviving a restart, logo and launcher icon.

## 4. Next Actions
1. Laptop: `git pull`, `./gradlew assembleDebug testDebugUnitTest`, `./gradlew installDebug`.
2. Phone: scan → results → source → calendar → fix & confirm. Import a PDF and check page progress and cited page numbers. Close the app fully, reopen, and check notices are under Recent.
3. Check the launcher icon and the header logo on the phone.

## 5. Architecture & Contracts
- Flow: MainActivity → `MainViewModel.process(uris)` or `processPdf(uri)` → `OcrEngine.read` / `readPdf` → `GeminiExtractor` (model chain) → `Verifier.verifyAll` → `UiState.Results(note)` + `SavedNotice` saved.
- Types: `OcrLine(id, text, box: Rect?, page)`. `SourceLine(id, text)` for verify/extract. `ResultItem(id, checked, edited?, confirmed)`. `SavedNotice(id, savedAt, lines, items)`.
- Stages: (OPENING_PDF) → READING_TEXT → EXTRACTING → VERIFYING → PREPARING. `UiState.Working(stage, detail?, fromPdf)`.
- `ScanScreen(stage, notice, recent, onScan, onPickPhoto, onImportPdf, onOpenNotice, onDeleteNotice, progressDetail, fromPdf)`. Other screen signatures are unchanged.

## 6. Decisions & Constraints
- Fallback means several model names (`GeminiConfig.MODELS`), against CLAUDE.md's "one constant". The user asked for it, and the list still lives in one file.
- Persistence is a JSON file, not a database (CLAUDE.md bans Room/DBs).
- Secondary text #6B6F6A instead of the brief's #747873, for WCAG AA contrast on paper.
- No screen-transition animation: Crossfade over UiState would reset scroll and dialog state on every edit.
- Earlier decisions still stand: AGP 8.13.2 (works on any 2025+ Studio), ISO `yyyy-MM-dd`, APPROXIMATE needs confirming before calendar, org.json is a test-only dependency.
- Out of scope: Room, Hilt, accounts, Arabic, conflicts, offline AI, alarms, TTS, Firebase, Gemini SDK.

## 7. Build & Test Status
- Laptop, last run: assembleDebug, testDebugUnitTest and installDebug passed, BEFORE the fallback, persistence and redesign changes. Re-run.
- Cloud JVM checks (not a real Android build):
  - 118/118 unit tests pass: verify 68, extract 28, storage 6, QuoteMatch 8, PdfLimits 8.
  - The Compose screens, theme, components, ViewModel and `OcrEngine` type-check against Compose Multiplatform desktop 1.8.2 (same API as our Jetpack Compose) and the real android.jar (PdfRenderer, ParcelFileDescriptor, Bitmap). Only the ML Kit signatures are stubbed.
  - `MainActivity` is checked by reading only.
- Unverified: the Android build of these changes, PDF behaviour on a real device (cloud PDFs, password PDFs), the live Gemini fallback, and how it looks and behaves on the phone.

## 8. Known Bugs & Risks
- The verifier doesn't check `time` against the quote. A year-less "12.10" can read as 12 Oct. Month-first ranges are not parsed.
- Worst case with all three models timing out is about 3 minutes (60 s each) before the error. System back cancels.
- First Document Scanner use downloads a Play-services module; Import image and Import PDF are the fallbacks.
- PDFs: only the first 10 pages are read; files over 50 MB are refused. Very small print may OCR poorly at 2400 px (raise `TARGET_LONG_SIDE_PX` if needed; memory grows with its square).

## 9. Agent Handoff Instructions
- Read CLAUDE.md, then this file, then the code. Next action: §4.1. Keep verify/ pure Kotlin, and keep the `storage/` JSON format backward compatible (it has a `version` field).
- JVM check recipe: a kotlin("jvm") project pointing at `app/src/{main,test}` plus okhttp, org.json, coroutines-core and junit. For storage/ui tests, add android.jar minus its stubbed `org/json`.
