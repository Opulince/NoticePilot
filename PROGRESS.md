# NoticePilot — Development Handoff

## 1. Current Status
- Overall state: the full pipeline is done. Entry points: scan, import image, import PDF, **share sheet (image/images/PDF)** and the **Quick Settings "Scan Notice" tile**, all → OCR → Gemini → verifier → results → source → fix & confirm → calendar. It also has the model fallback, saved notices, the editorial UI and the logo.
- Current milestone: laptop build plus on-phone check of the share target and QS tile (new), and of PDF import, the redesign and saved notices.
- Last completed action: dark-theme polish (green-black palette, ambient light + grain backdrop, separators, contrast fixes). It type-checks; 128/128 JVM unit tests pass.
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
  - A page that fails to render is skipped and reported in the results note (`PdfLimits.readNote`). The read only fails if no page renders. Cache copies use unique names (`io/CacheFiles`).
- [x] **Share target**: MainActivity is `singleTop` with SEND (image/*, application/pdf) and SEND_MULTIPLE (image/*) filters.
  - `share/ShareIntents.parse` reads EXTRA_STREAM, falling back to ClipData. `share/ShareKinds` classifies by MIME, then by extension.
  - Routing: a PDF → existing `processPdf`. Images → `onSharedFilesReceived`, which copies to unique cache files on IO first (≤10 images, ≤30 MB each), OCRs them, then deletes them.
  - Each intent is handled once: only when `savedInstanceState == null` or on `onNewIntent`, then `intent.action = null`.
  - Deviation: shared PDFs are NOT exported to per-page JPEGs; they reuse the existing page-by-page PdfRenderer→OCR path (no duplicate pipeline, one bitmap at a time).
- [x] **QS tile + screen capture**:
  - `tile/NoticeTileService`: label "Scan Notice", subtitle on API 29+, `startActivityAndCollapse` with a PendingIntent on API 34+, unlockAndRun when locked. Icon: `res/drawable/ic_qs_scan.xml`.
  - Flow in MainActivity: ACTION_TILE_CAPTURE → (first time, API 33+: ask POST_NOTIFICATIONS) → `createScreenCaptureIntent()` consent → `ScreenCaptureService.start` (FGS type mediaProjection, started while we're in the foreground) → `moveTaskToBack(true)`.
  - `capture/ScreenCaptureService`: getMediaProjection, registers its Callback before `createVirtualDisplay(ImageReader)`, keeps the newest real frame, takes it after `SETTLE_MS` 1.5 s (6 s timeout), saves a JPEG to `cacheDir/imports`, then releases everything once (display, reader, projection, thread, foreground) and calls stopSelf.
  - Handoff: `capture/CaptureHandoff` → MainActivity.onResume → `viewModel.onScreenCaptured(file)` (OCR, then delete). The service also tries `startActivity` and posts a "tap to read" notification.
  - Cancel, revoke, timeout and service death all end as `onCaptureFailed(message)` → a home notice. Repeated taps during a capture are ignored (`inProgress`).
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
  - **Dark-theme polish**:
    - Palette: background #090D0B, surface #111813, subtle green surface #1B2A20 (primaryContainer); accent #8FC3AE unchanged.
    - Contrast: outline #5E6F62 (3.4:1, WCAG non-text). Measured: body text 16:1, secondary 7.6–8.3:1, status badges ≥7.2:1.
    - `ui/components/Backdrop.kt`, drawn once in MainActivity under every screen, on its own graphics layer: one diffuse forest-green radial light at the top-left/header (≤20% alpha, wide falloff) plus a static 128 px grain tile at 4% (effective ≤2.4%). Dark theme only; light stays flat paper.
    - Hierarchy: hairline separator between home actions and Recent notices, and above each results section. Source line numbers use onSurfaceVariant (were ~2:1). Source side margins 20dp like the other screens.
- [x] **Siva's logo**. The original `WhatsApp Image 2026-11-03 at 7.07.08 AM.jpeg` (1024×1024, square, green rounded tile on white corners) is kept unmodified in the repo root:
  - In-app: `res/drawable-nodpi/noticepilot_logo.png`, the same artwork with only its white JPEG corners made transparent, 192 px. Shown square, 40dp on home and 30dp on processing/error, via `BrandMark`/`Brand.LOGO_RES`. Not on results or source.
  - Launcher: an adaptive icon (`mipmap-anydpi-v26/ic_launcher*.xml` + `drawable-nodpi/ic_launcher_foreground.png`). The artwork is scaled into the 66dp safe zone and the gradient extended to the edges. Checked under circle and squircle masks.

## 3. Current Work
- [ ] On-phone check: PDF import (1-page, multi-page, >10 pages, password-protected, image-only), the redesign, saved notices surviving a restart, logo and launcher icon.

## 4. Next Actions
1. Laptop: `git pull`, `./gradlew assembleDebug testDebugUnitTest`, `./gradlew installDebug`.
2. Phone: scan → results → source → calendar → fix & confirm. Import a PDF and check page progress and cited page numbers. Close the app fully, reopen, and check notices are under Recent.
3. Share tests: 1 image, several images, a multi-page PDF from WhatsApp/Gmail/Files, sharing while the app is open, and an unsupported file. Tile tests: add the tile (edit QS panel) → allow → capture → results; cancel consent; tap the tile twice quickly.
4. Check the launcher icon and the header logo on the phone.

## 5. Architecture & Contracts
- Flow: MainActivity → `MainViewModel.process(uris)` | `processPdf(uri)` | `onSharedFileReceived` / `onSharedFilesReceived` | `onScreenCaptured(file)` → `OcrEngine.read` / `readPdf` → `GeminiExtractor` (model chain) → `Verifier.verifyAll` → `UiState.Results(note)` + `SavedNotice` saved.
- Line ids stay `L1..` continuous across pages and images, plus `OcrLine.page` (brief suggested `P1-L1`). This keeps prompt, verifier, parser and saved notices compatible, and page mapping is preserved.
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
  - 128/128 unit tests pass: verify 68, extract 28, storage 6, QuoteMatch 8, PdfLimits 9, ShareKinds 4, ScreenFrames 5.
  - Type-checked: the Compose screens, theme, components, ViewModel, `OcrEngine`, capture service, tile, share parser and cache helper, against Compose Multiplatform desktop 1.8.2 and the real API 35 android.jar. Only ML Kit signatures, MainActivity (as a class reference) and R are stubbed.
  - `MainActivity` itself and the manifest are checked by reading only.
  - Visual check: home, results and source (dark and light) rendered off-screen with Compose desktop 1.6 and sample data. Fonts there are DejaVu, not Noto, and the processing screen was not rendered (its spinner animates forever).
  - `MainActivity` is checked by reading only.
- Unverified: the Android build of these changes, and everything on a real device (share sheet, tile, MediaProjection on Android 13 vs 14+, cloud and password PDFs, the live Gemini fallback).

## 8. Known Bugs & Risks
- The verifier doesn't check `time` against the quote. A year-less "12.10" can read as 12 Oct. Month-first ranges are not parsed.
- Worst case with all three models timing out is about 3 minutes (60 s each) before the error. System back cancels.
- First Document Scanner use downloads a Play-services module; Import image and Import PDF are the fallbacks.
- Screen capture:
  - Android may block the service from bringing NoticePilot back (background-activity-launch rules). Then the user taps the notification or reopens the app, and the frame is read in onResume.
  - Every tile use needs fresh consent (Android 14+ allows no token reuse).
  - The capture includes the status bar. It captures whatever is on screen after 1.5 s; if NoticePilot itself was the foreground app, that's the home screen.
- Share: SEND_MULTIPLE accepts images only (a PDF must be shared alone).
- PDFs: only the first 10 pages are read; files over 50 MB are refused. Very small print may OCR poorly at 2400 px (raise `TARGET_LONG_SIDE_PX` if needed; memory grows with its square).

## 9. Agent Handoff Instructions
- Read CLAUDE.md, then this file, then the code. Next action: §4.1. Keep verify/ pure Kotlin, and keep the `storage/` JSON format backward compatible (it has a `version` field).
- JVM check recipe: a kotlin("jvm") project pointing at `app/src/{main,test}` plus okhttp, org.json, coroutines-core and junit. For storage/ui tests, add android.jar minus its stubbed `org/json`.
