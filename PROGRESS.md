# NoticePilot — Development Handoff

## 1. Current Status
- Overall state: Steps 1–8 are all written. The pipeline is complete: scan/pick → OCR → Gemini → verifier → results → source view → fix & confirm → calendar.
- Current milestone: on-phone testing. On the laptop, `./gradlew assembleDebug testDebugUnitTest` and `installDebug` pass (Galaxy M21, Android 13).
- Last completed action: 89 JVM unit tests pass (verify/ + extract/). calendar/ compiles against a real API 35 android.jar.
- Current blocker: none. The cloud dev env can't build Android (`dl.google.com` blocked), so build on the laptop.

## 2. Completed Work
- [x] Gradle skeleton: AGP 8.13.2, Gradle 8.14.3 wrapper, Kotlin 2.2.21, compileSdk/targetSdk 36, minSdk 26. Versions are in `gradle/libs.versions.toml`.
  - `GEMINI_API_KEY` goes from root `local.properties` into `BuildConfig`. A missing key gives an empty string, so the app still builds and shows "key missing" at extraction time. `local.properties` is gitignored; see `local.properties.example`.
- [x] scan/: `DocumentScan` (ML Kit scanner: JPEG, gallery import, 5 pages) and `PhotoPicker` (PickVisualMedia, images only).
- [x] ocr/: `OcrEngine.read(uris)` uses bundled ML Kit Latin. Lines are numbered L1.. continuously across pages, in ML Kit block/line order. Blank lines are dropped.
- [x] extract/: `GeminiExtractor(apiKey).extract(List<SourceLine>)` does a direct OkHttp POST to `generateContent` on Dispatchers.IO with a 60 s timeout.
  - Request: `systemInstruction` + `contents` + `generationConfig.responseMimeType/responseSchema` (OpenAPI types in uppercase).
  - `ExtractionParser` parses defensively: JSON nulls, code fences, thought parts, blocked responses, a top-level array.
  - Errors are typed as `ExtractionException.Kind`, each with a user message. The API key never appears in errors.
- [x] verify/: pure Kotlin `Normalizer`, `DateParser`, `Verifier`, following CLAUDE.md rules 1–5.
  - Fuzzy matching = minimum edit distance against any substring. Reasons are kept and de-duplicated.
  - `Verifier.verify` never throws.
- [x] ui/: `MainViewModel` (one `StateFlow<UiState>`), `UiState` (Idle / Working(stage) / Results(sourceItemId) / Error(canRetry)), and the Scan, Results, Source and Error screens plus Theme.
  - A Gemini failure keeps the OCR lines, so "Try again" re-runs extraction without rescanning.
- [x] calendar/: `CalendarIntents.insert` uses ACTION_INSERT with no permissions. No time means an all-day event. The description holds the quote and conditions. ActivityNotFound is caught.
- [x] Fix & confirm: available on non-verified cards. The ViewModel validates title, YYYY-MM-DD date and HH:mm time. A saved item shows "Confirmed by you" next to its unchanged AI status and becomes calendar-eligible.

## 3. Current Work
- [ ] On-phone test of the full flow (not done yet). The app has no launcher icon yet, so it shows the default Android icon.

## 4. Next Actions
1. Laptop build: DONE.
2. Install on the phone and run the demo script from the README handoff: scan → results → source → calendar → fix & confirm.
3. Tune the prompt or verifier on 2–3 real school circulars. Do this only if items are flagged wrongly.

## 5. Architecture & Contracts
- Flow: MainActivity (launchers) → `MainViewModel.process(uris)` → `OcrEngine` → `GeminiExtractor` → `Verifier.verifyAll` → `UiState.Results`.
- `OcrLine(id, text, box: Rect?, page)`. `SourceLine(id, text)` is what verify/ and extract/ see, so neither uses android types.
- `ExtractedItem` matches CLAUDE.md. `CheckedItem(item, status, reasons)`. `ResultItem(id, checked, edited?, confirmed)`, where `shown = edited ?: checked.item` and `canAddToCalendar = (VERIFIED || confirmed) && date != null`.
- Model name lives only in `extract/GeminiConfig.MODEL_NAME`.

## 6. Decisions & Constraints
- Model `gemini-3.6-flash` (stable, previous-gen Flash). Switched from `gemini-3.8-flash`, which returned 503 "experiencing high demand" on the phone. Other option: `gemini-3.5-flash-lite`.
- `responseSchema` was kept, not the newer `responseFormat.text.schema` that the docs also show. CLAUDE.md asks for `responseSchema`, and it is still documented for generateContent. If the API rejects it, switch only `ExtractionPrompt.buildRequestBody`.
- AGP 8.13.2 was chosen over the newest 9.5, because it is supported by any 2025+ Android Studio and needs no AGP 9 built-in-Kotlin changes. AndroidX versions are pinned to that generation (Compose BOM 2025.10.01).
- No rehearsal Gradle cache existed in the repo, so the first sync downloads everything. Do it before the event.
- ISO dates are `yyyy-MM-dd`. The master prompt's "dd-MM-yyy" is a typo; CLAUDE.md wins.
- APPROXIMATE items go under "Needs your attention" and are not calendar-eligible until confirmed.
- `org.json` is a testImplementation only, because android.jar's version is a stub in local unit tests.
- This file goes past CLAUDE.md's 30-line limit, because the handoff spec asks for 9 sections.
- Out of scope: Room, Hilt, accounts, Arabic, conflicts, offline AI, alarms, TTS, Firebase, Gemini SDK.

## 7. Build & Test Status
- Android build: PASSES on the laptop (assembleDebug, testDebugUnitTest, installDebug). It can't run in the cloud env.
- Unit tests: 89/89 pass on plain JVM (Kotlin 2.2.21, real org.json + OkHttp): verify 68, extract 21. These are the same files `./gradlew testDebugUnitTest` runs.
- Unverified: the live Gemini call and all runtime behaviour on the phone.
- Commands: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`.

## 8. Known Bugs & Risks
- Verifier doesn't check `time` against the quote (spec has no rule). A wrong time can reach a verified card.
- DateParser: a year-less `12.10` (a price or decimal) can read as 12 Oct. Month-first ranges like "October 26-28, 2026" are not parsed, so they get flagged.
- First Document Scanner use downloads a Play-services module and needs internet. Pick photo is the fallback.

## 9. Agent Handoff Instructions
- Read CLAUDE.md, then this file, then the code. Next action: §4.1. Don't change contracts in §5 or move verify/ off pure Kotlin.
- To test verify/extract without the Android SDK: a kotlin("jvm") project whose sourceSets point at `app/src/{main,test}/java/.../{verify,extract}`, plus okhttp 4.12.0, org.json, coroutines-core and junit.
