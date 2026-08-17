# Friends Leaderboard

A weekly leaderboard comparing friends' climbing across five categories. This document explains
how it's scored, how privacy is enforced, and — importantly — what's real versus mocked in the
current implementation.

## What's real vs. what's still missing

Friends' climbs now sync to Firestore/Storage when marked Friends-only or Public
(`com.example.climb.sharing` — `ClimbSyncRepository`/`ClimbSyncWorker` on the write side,
`FriendClimbsRepository` on the read side), so the leaderboard reads real data, not a mock roster.

So, today (`leaderboard/data/LocalLeaderboardRepository.kt`):

- **Scoring, ranking, tie-breaks, weekly periods, and privacy filtering are all real** — pure,
  unit-tested logic in `com.example.climb.leaderboard.scoring`/`period`/`privacy`.
- **Only your real accepted friends appear** (`SocialRepository.observeFriends`) — there is no
  mock/demo roster.
- **Every entry's climbs are real** — yours from `ClimbRepository` (local), a friend's from
  `FriendClimbsRepository` (their Friends-only/Public synced climbs), both mapped into the same
  `ClimbAttempt` shape and run through the identical scoring pipeline.
- A friend who hasn't shared anything (or shared nothing in the selected period) has nothing to
  rank on and shows up under "Friends without data yet" — that's genuinely no data, not a
  stand-in for missing sync.
- Because `ClimbEntity`/`SharedClimb` have no attempt history or shared problem identity yet,
  each logged/shared climb maps to its own one-attempt "problem." Real attempt/problem tracking
  would make Consistency/lock-off-style metrics much more meaningful for everyone.
- Per-friend leaderboard privacy settings (`LeaderboardPrivacySettings`) are now real and
  storable: each user's own settings live as a `leaderboardPrivacy` map field on their `users/{uid}`
  profile doc (`SocialRepository.updateLeaderboardPrivacySettings`/`getLeaderboardPrivacySettings`/
  `observeLeaderboardPrivacySettings`), editable from a "Leaderboard privacy" section in
  `SettingsScreen`. `LocalLeaderboardRepository.friendEntry` fetches each friend's real stored
  settings (falling back to `DEFAULT_LEADERBOARD_PRIVACY_SETTINGS` — participating, stats visible,
  friends-only video — only for a user who's never touched their own settings) and now **excludes**
  a friend from the leaderboard entirely (not just from the ranked list — from
  `unrankedFriends` too) when their real settings say not participating or stats-sharing is off,
  instead of silently falling back to an unfiltered entry. `firestore.rules` needed no new match
  block: the field rides on `users/{uid}`'s existing owner-only-write rule.

**What must move server-side for this to be production-grade:** authoritative scoring (currently
computed on-device by whoever's viewing, from data already filtered by Firestore rules — a
malicious client could still lie about its own computation, though not about what it can read),
and persisted weekly period rows with real `Active → Calculating → Complete` status transitions
(currently just computed on demand from calendar time).

## The five categories

| Tab | Primary value | What it rewards |
|---|---|---|
| Overall | Weighted score | Five best sends + consistency + session bonuses |
| V Grade | Highest completed grade | Hardest send, tie-broken by top-3 average and attempts |
| Consistency | Send rate | Completing what you attempt (needs 5+ unique attempts) |
| Sessions | Active days | Showing up, not just sending |
| Sends | Weighted score | Every unique send, no cap |

Each tab's exact rank order (primary value, then tie-breaks) lives in
`leaderboard/scoring/TieBreaks.kt` — one `Comparator` per category, in the same order as this doc.

## Scoring formulas

**Grade points**: `gradePoints(vGrade) = (numericGrade + 1) * 10` — V0=10 ... V8=90, unbounded
above (`VGrade.kt`).

**Send points** (`GradePoints.kt`):
```
basePoints = gradePoints(vGrade)
flash            -> basePoints * 1.25
2nd-attempt send -> basePoints * 1.15
otherwise        -> basePoints
```
Internal math stays in `Double`; rounding happens once, on a final total — never per-send.

**Unique-problem rule** (`BestResultSelector.kt`): every problem counts once per period. Among a
problem's completed attempts, the best is picked by: flash > second-attempt send > any other
send > fewer attempts > earlier success.

**Overall** (`OverallScoring.kt`):
```
baseSendScore    = sum of your five highest unique send scores
consistencyBonus = baseSendScore * clamp(consistencyRate, 0, 1) * 0.20   // caps at 20% of base
sessionBonus     = min(qualitySessionCount, 5) * 10                      // caps at 50
overallScore     = round(baseSendScore + consistencyBonus + sessionBonus)
```

**V Grade** (`VGradeScoring.kt`): highest completed grade, plus the average of your top three
(or fewer, if you don't have three) hardest unique sends.

**Consistency** (`ConsistencyScoring.kt`): `uniqueProblemsSent / uniqueProblemsAttempted` — unique
problems, never raw attempt counts (so hammering one problem doesn't move the number). Requires
**5+ unique attempted problems**; below that, the category shows "Not enough data" instead of a
percentage.

**Sessions** (`SessionScoring.kt`): a session is "quality" if it has ≥3 attempts and ≥1
completion, OR ≥20 minutes of tracked activity. Active days = distinct calendar days (in the
period's timezone) with a session. Streak = consecutive active days ending at the most recent one.

**Sends** (`SendsScoring.kt`): sum of best send-points across *every* unique completed problem —
unlike Overall, not capped at five.

## Weekly periods

A week is Monday 00:00 to the next Monday 00:00, in a given `ZoneId` (`LeaderboardPeriodProvider`,
`period/`). Using `ZonedDateTime` arithmetic throughout means daylight-saving transitions are
handled for free — a week is always Monday-to-Monday in *wall-clock* time, so the one week per
year that's actually 23 or 25 hours long still starts and ends at local midnight (see
`LeaderboardPeriodProviderTest`'s DST test).

Each period gets a stable `id` derived from its ISO week (e.g. `"2026-W32"`), so re-deriving "this
week" twice never produces a duplicate. Rank movement compares against the immediately preceding
period of the same length, computed the same way.

### Adding another period filter

Add a case to `PeriodFilter` in `LeaderboardPeriodProvider.kt`, then handle it in both
`periodFor()` and `previousComparablePeriod()` — each just needs a start/end `ZonedDateTime` pair
and a stable id. The UI's `PeriodSelector` picks up new enum values automatically.

## Privacy

Enforced in `LeaderboardPrivacyFilter.kt`, called from the repository layer (never left to the UI
alone to hide fields):

- **Not participating** → excluded from everyone's leaderboard entirely.
- **Not friends**, or **friends but stats sharing is off** → excluded from that viewer's
  leaderboard.
- **Video visibility** (`Private` / `FriendsOnly` / `SelectedFriends` / `Public`, reusing
  `com.example.climb.analysis.Visibility`) governs whether a viewer sees a video *count* — never a
  URL, thumbnail, title, or any other identifying metadata, since those fields are stripped before
  a `LeaderboardEntry` leaves the repository, not hidden in Compose code.
- The owner always sees their own videos regardless of their own visibility settings.

## Rank movement

`LeaderboardCalculator.rankEntries()` sorts eligible entries with the category's comparator,
assigns 1-based ranks, then looks each user up in a `userId -> rank` map from the previous
comparable period: absent → **New**, lower number now → **Up**, higher number now → **Down**,
same → **Unchanged**.

## Changing scoring configuration safely

Every tunable constant lives at the top of its scoring file (e.g.
`CONSISTENCY_MIN_UNIQUE_ATTEMPTED` in `ConsistencyScoring.kt`, `QUALITY_SESSION_MIN_DURATION_MS` in
`SessionScoring.kt`, the flash/second-attempt multipliers in `GradePoints.kt`) — never inline
magic numbers. Change a constant, then run `./gradlew :app:testDebugUnitTest`; the existing tests
pin the current values, so an unintended change shows up as a failing assertion rather than a
silent behavior change.

## Known limitations

- Real friends currently always show "no data yet" (see above) — real cross-friend rankings need
  the climb-data sync project.
- Compose UI tests, repository/cache/offline integration tests, and accessibility semantics tests
  weren't added — this app has no UI/instrumented test infrastructure yet (only unit tests exist).
  The pure scoring/period/privacy logic is fully unit-tested instead.
- Avatars are initials-only circles; there's no image-loading dependency in this app yet.
- Your own entry treats each logged climb as a single-attempt "problem" — the current climb log
  has no attempt history or shared problem identity to draw on.
