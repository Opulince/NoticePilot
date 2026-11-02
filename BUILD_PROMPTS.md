# NoticePilot: Claude Code build prompts

Paste these one at a time. Test on the phone and commit after each one (`git commit -am "step N"`),
so you can always roll back to a working build.

Rehearse the whole thing at home against a 2-hour timer first, then delete the rehearsal code.
The Gradle cache (`~/.gradle`) stays on the laptop, so the real sync is fast.

Target times on the day (start 8:00): step 1 by 8:15 · step 2 by 8:45 · step 3 by 9:15 ·
steps 4 + 5 by 9:25 · step 6 by 9:35 · step 7 by 9:45 · **freeze at 9:45**. Step 8 only if
you're ahead. Step 4 can go to Argon in parallel (it's pure Kotlin, no Android files).

---

## Step 0: plan (use plan mode, Shift+Tab)

Read CLAUDE.md. Check the current versions of every dependency it lists in the official docs, the
current Gemini Flash model name for the Gemini API, and the current generateContent request fields
for JSON output. Look at the existing project (Gradle files, package name, minSdk). Then give me:
the dependency list with versions, the file list per package, and anything in CLAUDE.md that
conflicts with this project. Don't write code yet.

(On the day, skip the version check and reuse exactly what the rehearsal used.)

## Step 1: skeleton

Add the dependencies. Read GEMINI_API_KEY from local.properties into BuildConfig (make sure
local.properties is gitignored). Add the INTERNET permission. Create MainActivity, MainViewModel, UiState and the three screens as placeholders, with screen
switching through UiState. No real scanning or AI yet. Build it.

## Step 2: scan + OCR

Wire up the Document Scanner (JPEG, gallery import allowed) and the photo-picker fallback. Run
ML Kit OCR on every page and number the lines L1, L2... across pages. For now, show the raw numbered
lines on ResultsScreen so I can check OCR quality. Build it.

## Step 3: Gemini extraction

Build GeminiExtractor exactly as CLAUDE.md describes: a direct HTTPS call to generateContent,
text-only input, the response schema with the optional fields left out of `required`, and the
system instruction rules. Parse into ExtractedItem with org.json. Show one plain card per item (no verification yet).
Handle no internet, errors and bad JSON without crashing. Build it.

## Step 4: verifier + tests

Write verify/ as pure Kotlin following the rules in CLAUDE.md. Write JUnit tests for at least:
exact match; double spaces ("Oct  12" vs "Oct 12"); curly quotes; an OCR typo that should be
APPROXIMATE; a quote that isn't in the text; a wrong date; a quote with no year; a day-first numeric
date (26/10/2026); a date range; the wrong line cited but the quote exists elsewhere; unclearReason set.
Run the tests and fix them until they all pass. Don't touch the UI in this step.

## Step 5: wire verification into the UI

Run the verifier on Gemini's output. Split ResultsScreen into Verified and Needs check, show the
reasons on flagged cards. Build it.

## Step 6: source view

Tap a card -> SourceScreen with all OCR lines, the cited lines highlighted and scrolled into view.
Build it.

## Step 7: calendar

Add to calendar via ACTION_INSERT: title, all-day if there's no time, begin/end from date/time,
the quote in the description. Catch ActivityNotFoundException. Only for verified or user-confirmed
items with a date. Build it.

## Step 8: fix flagged items + polish

Edit action on Needs check cards (title, date, time). Saving marks the item confirmed. Then clean
up the error and loading states. Build it, run the tests, and list anything that's still rough.
