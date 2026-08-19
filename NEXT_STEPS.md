# Where things stand (as of this commit) and how to continue

This file is a handoff note for whoever (human or Claude) picks this up next. It replaces the
previous version (written after commit `3ce7fb3`) — that one's still fully accurate for the
history it describes, just superseded by what shipped in this commit on top of it.

## What just shipped (this commit)

- **Club rank categories** (`ClubLeaderboardScreen.kt`, reached via the member club shell's
  "Ranks" tab in `MemberClubNavHost`): the single fixed sends-ranking is now a 3-way toggle —
  **Most Attempted / Most Sent / Most Failed** — re-sorting the same `ClubStatsEntity` list by
  `totalAttempts` / `totalSends` / `(totalAttempts - totalSends)`. Purely club-scoped data, no
  friends graph involved anywhere in this screen, and it's strictly a Club Mode feature — it does
  not feed the personal, friends-based Leaderboard (`LeaderboardCategory`), and shouldn't.
- **"Best senders by grade"** — a new card below the rank list: for each real V grade in the club,
  who has the most real sends at that grade (ties list every tied name). Built from the new
  `ClubRepository.observeRouteCompletionsForOrganization(organizationId)` (org-wide counterpart to
  the existing per-route `observeRouteCompletions`) joined against each route's real `vGrade`.
- **Route Betas rename** — `ExploreScreen.kt`'s routes-list section label changed from
  "Most attempted" to "Route Betas" (the toggle that sorts the list by today/this-week attempt
  count is unchanged, just the heading).
- Also bundled in this commit (pending work from before this session, unrelated to the above):
  a staff Route History screen (`RouteHistoryScreen.kt`), a real route hard-delete
  (`ClubRepository.deleteRoute`/`deleteRouteCascade`), and member-shell parity changes across
  `LiveSendCamerasScreen`/`LiveSendClubExploreHost`/`LiveSendBroadcastScreen`/`LiveSendMembersScreen`.

## Verification discipline (please keep doing this)

Don't trust a workflow/fork/agent's self-report, and don't trust Gradle's console
"BUILD SUCCESSFUL" text at face value for on-device runs. For this commit: `compileDebugKotlin`
passed clean, and the club rank screen specifically **was** opened on the real device — see below
for how and what was actually confirmed vs. not.

### Real-device check: what was and wasn't verified

Normal tap-driven navigation into the club rank screen (Home → Settings → Open Clubs → club row →
Ranks tab) turned out to be unreliable on this test device — this session re-confirmed the
existing known issue (see the `reference-climb-device-testing` memory) is worse than previously
documented: taps don't just get denied, they can silently no-op, land on a stale/wrong queued
target several screens back, or in one case backgrounded the app entirely into an unrelated app.
Blind coordinate-tap automation should not be trusted for multi-step navigation on this device.

To actually see the new screen, two **temporary, fully-reverted** code changes were made and then
undone before this commit (confirmed via `git diff` showing no residual diff):
1. `ClimbNavHost.kt`: forced `modeChosen = true` (skips the Club Mode switch screen) and added a
   one-shot `LaunchedEffect` that auto-navigated into `MemberClubNavHost` for the first staff org.
2. `MemberClubNavHost.kt`: temporarily set the inner `NavHost`'s `startDestination` to
   `MemberClubRoutes.LEADERBOARD` instead of `OVERVIEW`.

With those in place, a fresh launch landed directly on the rank screen with real data (real club
"Golomb club", 3 real members). Confirmed correct from the screenshot:
- "Most Sent" (default) sorted Amit (14) > luna (2) > Puterman (1), matching their real
  attempts/sends.
- "Best senders by grade" showed real per-grade tallies, including a genuine 3-way tie at V8
  (Amit, Puterman, luna all listed) — the tie-handling path actually exercised, not just reasoned
  about.

**Not confirmed on-device**: switching to "Most Attempted" or "Most Failed" — a single stray tap
right after the good screenshot backgrounded the app before that could be checked, and given the
tap-reliability problem above, it wasn't worth chasing further this session. The sort logic is a
one-line `sortedByDescending` per category (see `ClubStatsEntity.valueFor` in
`ClubLeaderboardScreen.kt`), so risk is low, but it's genuinely unverified on-device — worth an
actual tap-through (or a Compose UI test) before considering this fully done.

## Outstanding / not yet done

1. **Manually verify the Most Attempted / Most Failed toggle states on-device** (see above) —
   the one real gap left from this session's verification.
2. **Firestore rules check for `observeRouteCompletionsForOrganization`**: this is a new
   single-equality-filter query (`whereEqualTo("organizationId", ...)` on `routeCompletions`,
   same shape as several existing org-scoped queries), so it likely needs no new rule, but that's
   an assumption, not confirmed against the deployed rules. If "Best senders by grade" comes back
   empty for a club with real graded sends, check this first.
3. **Publish `firestore.rules`** (carried over, still not done) — accumulated changes from prior
   sessions still need a manual publish via the Firebase Console (no Firebase CLI in this sandboxed
   dev environment): `routeAttemptEvents` collection, `lastActiveAt`-only update exception on
   `organizationMemberships`, `friendCount`-only update exception on `/users/{uid}`, plus whatever
   was unpublished before that. Check deployed rules against `firestore.rules` before assuming any
   dependent feature works — they tend to silently degrade to empty/no-op on a stale-rules
   mismatch rather than error loudly.
4. **Deploy Cloud Functions** (carried over) — the FCM push-notification Cloud Function
   (`functions/src/index.ts`) still needs `firebase deploy --only functions` on the Blaze plan.
5. **`TagScreenTest` test-isolation gap** (carried over, low priority) — Save triggers a real
   `ClimbSyncWorker.enqueue(...)` side effect via WorkManager, not faked in the test.
6. **Route-color-detection** (carried over, unchanged) — 8-phase rebuild functionally complete;
   known accepted limit is `STRICT_DELTA_E_THRESHOLD` capped at 20.0. Tap-to-calibrate
   (`RoiSampler.kt`) is the intended long-term fix, not further threshold tuning.
7. **Device tap-injection unreliability is worse than previously documented** — worth writing up
   as its own thing (or updating the existing device-testing memory) rather than rediscovering it
   fresh each session: taps can silently no-op, queue and land on a stale screen several steps
   later, or in one observed case background the whole app. Treat any on-device UI verification
   that depends on multi-step tap navigation as unreliable; prefer a temporary, fully-reverted
   direct-navigation code patch (as used this session) over chained blind taps.

## Also present in the working tree, not part of this commit

- `references/` (WhatsApp screenshots) and `.idea/deviceManager.xml` are untracked and were left
  alone again — still don't look related to any commit's work.

## If asked to keep going

Ask the user what they want next rather than guessing. Item 1 above (confirm the two untested
toggle states) is the cheapest next step if continuing directly on this feature.
