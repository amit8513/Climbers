# Where things stand (as of this commit) and how to continue

This file is a handoff note for whoever (human or Claude) picks this up next.

## What just shipped (commit `9a7b9a1`)

- **Manage Social**: renamed from "Broadcast", tabbed Updates/Chat, staff can delete
  club updates and chat messages. `firestore.rules` updated to match
  (`clubMessages`: no update, staff-only delete) — **not yet published**, see below.
- **Leaderboard privacy settings**: participation/stats-sharing/video-visibility
  toggles in Settings, stored on `users/{uid}.leaderboardPrivacy`. Fixed a real bug
  where per-route leaderboard times could resolve to the *wrong user's* local
  attempt (no ownership check) — now filtered by `userId`.
  See `LEADERBOARD.md` for the fuller status/history of this feature.
- **Video trim** on the tag screen (RangeSlider + looping preview), backed by new
  `VideoTrimExporter.kt`.
- **Retry UI** for a FAILED pose-analysis attempt (`DetailScreen.kt`'s
  `PoseAnalysisStatusRow`), with a double-tap guard.
- **Compose UI test infra** — first of its kind in this repo
  (`androidx.compose.ui:ui-test-junit4`/`ui-test-manifest`, see `app/build.gradle.kts`
  / `gradle/libs.versions.toml`). Two test classes:
  - `PoseAnalysisRetryTest.kt` — 5 tests over the retry/view-progress/view-result flow.
  - `TagScreenTest.kt` — 5 tests over the tag/save flow. One of these
    (`tappingSave_callsRepositoryWithSelectedData_andInvokesOnSaved`) was failing on
    real hardware with "could not find node with text 'V7'" — root cause was that the
    Grade picker is an 18-item `LazyRow` and item 7 wasn't composed until scrolled into
    view. Fixed by adding `Modifier.testTag("gradeRow")` to the LazyRow in
    `TagScreen.kt` and calling `performScrollToNode(hasText("V7"))` before the click in
    the test. **All 10 tests confirmed passing on-device** (verified via the raw XML
    at `app/build/outputs/androidTest-results/connected/debug/`, not just console output
    — that discipline matters, the console summary line has been misleading before).

## Verification discipline (please keep doing this)

Don't trust a workflow/fork/agent's self-report, and don't trust Gradle's console
"BUILD SUCCESSFUL"/"X tests completed" text at face value for on-device runs — read
the actual `TEST-*.xml` under `app/build/outputs/androidTest-results/connected/debug/`
for real `failures="N"` counts and any `<failure>` elements. This has caught real
problems before.

## Outstanding / not yet done

1. **Publish `firestore.rules`** — several changes have accumulated (chat-message
   delete rule from this batch, plus earlier ones) and need a manual publish via the
   Firebase Console; there's no Firebase CLI in this sandboxed dev environment.
2. **Deploy Cloud Functions** — the FCM push-notification Cloud Function
   (`functions/src/index.ts`, from an earlier commit `c106277`) still needs
   `firebase deploy --only functions`, which requires the project to be on the Blaze
   plan. Not done.
3. **No real-device test yet** for: video-trim export, leaderboard-privacy-settings
   UI. Both were verified by reading code / unit-level reasoning only.
4. **`TagScreenTest` test-isolation gap**: Save also triggers a real
   `ClimbSyncWorker.enqueue(...)` side effect via WorkManager, which isn't faked in the
   test. Not a functional bug, just means the test isn't hermetic w.r.t. WorkManager.
   Low priority.
5. **Live Send full replacement is deliberately deferred** — see
   `C:\Users\Puterman\.claude\plans\cheerful-gathering-sunbeam.md` for the reasoning.
   Short version: several real features (recording, tagging, detail screen, full
   pose-analysis pipeline, friends, staff club management) have no Live Send screen at
   all, and `HomeFeedScreen`/`ClubDashboardScreen`'s social-feed/live-presence UI has no
   backing data model in the real app yet. The user's call was: leave Live Send as an
   opt-in preview (Settings → "UI Concepts" → "Preview: Live Send →") and don't start
   real-data wiring until the "what powers the feed" product question is answered.
6. **Route-color-detection**: the 8-phase rebuild (Phases 1-8) is functionally
   complete and merged (calibration, hold detection/segmentation, boundary
   refinement, confidence scoring, real-app integration via `HoldHighlightPipeline`,
   debug visualization, regression/benchmark tests). Known, accepted limits:
   `STRICT_DELTA_E_THRESHOLD` is deliberately capped at 20.0 (raising it further was
   proven unsafe against real cross-color measurements — see the doc comment on
   `RouteColorDetectionConfig.kt`), and holds under ~20px still fail the confidence
   floor even after the wall-halo dilution fix. Tap-to-calibrate
   (`RoiSampler.kt` + the calibration picker in `DetailScreen.kt`) is the intended
   long-term fix for real-footage color drift, not further threshold tuning.

## If asked to keep going

Ask the user what they want next rather than guessing — the most recently-stated
open items are the two "suggest 2 more features / 2 ways to improve" answers the user
hasn't yet picked from, and the Firebase publish/deploy steps above which need the
user's own Firebase Console/CLI access (not achievable from this sandbox).
