# Where things stand (as of this commit) and how to continue

This file is a handoff note for whoever (human or Claude) picks this up next. It replaces the
previous version (written after the "club rank categories" commit) — that feature is done and
unrelated to what follows; see git history if you need it. This version is entirely about the
**gym-camera automatic route attribution POC**, a large multi-phase effort currently in progress.

**If you're continuing this work in a new session, read in this order:**
1. This file (current status, what's approved, what's blocked).
2. `docs/ROUTE_ATTRIBUTION_PLAN.md` — the approved architecture. It predates the correction passes
   below, so don't treat it as 100% current on its own; treat it as "why the design looks like
   this."
3. The "Corrections applied since the plan doc" section below — these are real, already-implemented
   changes that supersede parts of the plan doc and are NOT reflected in it.

## What this is

A POC to make gym-camera video automatically attribute a climb to the right route and result
(send/fall) via wristband NFC tap + fixed wall camera, without the member's own phone ever being
the authority for verified data. Full design rationale, entity list, phase order, and diagrams are
in `docs/ROUTE_ATTRIBUTION_PLAN.md`.

This is being built through a strict, phased process: the user reviews and corrects the plan/each
phase's output in detail before the next phase is allowed to start. **Do not skip ahead or start a
new phase without explicit user approval**, even if the next phase seems obvious or easy.

## Phase status

| Phase | Status |
|---|---|
| 0 (audit) | Done — all 10 claims confirmed |
| 0.5 (trust-boundary bug fix) | Done, approved |
| 1 (models/config/domain/migration) | Done, approved |
| 1.1 (correction pass: shared module, snapshot fields, source semantics, migration verification, capture IDs, credential security) | Done, approved as a checkpoint |
| 1.2 (correction pass: AttemptSource semantics fix, real migration smoke test, capture ID implementation, credential HMAC, snapshot validator, colorHex spec) | **Done, approved as a checkpoint — this is the current HEAD state** |
| 1.25 (hardware spike) | Not started |
| 1.5 (Camera Edge Device bootstrap) | **Not started — blocked pending user approval** |
| 2 and beyond | Not started |

**Explicit standing constraint from the user, still in force**: "Phase 1.1/1.2 is accepted as a
checkpoint, but do not begin Phase 1.5 yet." Do not start Phase 1.5 (or anything beyond) without a
fresh, explicit go-ahead in the conversation. If picking this up cold, the right first move is to
summarize current status and ask what to do next — not to assume Phase 1.5 is authorized.

**Never reintroduce** any member-client call to `recordClubAttempt`, `recordRouteAttempt`,
`recordRouteCompletion`, or `ClubRepository.shareAttemptVideo` — Phase 0.5 removed these
specifically because manual/imported personal attempts must never reach official club-facing
writes. `TrustBoundaryRegressionTest.kt` guards this; keep it passing.

## Corrections applied since `docs/ROUTE_ATTRIBUTION_PLAN.md` was written

The plan doc describes the approved architecture, but implementation went through two further
correction passes (1.1 and 1.2) that changed real decisions. These are **already implemented**, not
proposals:

**Phase 1.1**:
- Introduced a genuinely separate `:shared-domain` Gradle module (pure Kotlin/JVM, no Android
  dependency) so a future Camera Edge Device app can reuse these contracts without duplication.
  Everything vision/pose/capture-domain-related that doesn't need Android lives there now (see
  file list below). **Do not duplicate these contracts back into `:app`.**
- `RouteVersionEntity` gained an explicit `setAt: Long?` distinct from `createdAt` (never derive one
  from the other).
- Established the ID-generation convention: staff-driven/low-frequency Firestore entities keep
  `nextId()`; high-frequency/offline-creatable capture entities use client-generatable String ids.

**Phase 1.2** (supersedes some Phase 1.1 output):
- **`AttemptSource` corrected**: `PHONE_CAMERA`, `IMPORTED_VIDEO`, `MANUAL_LOG` (no video at all —
  not "unknown provenance"), `LEGACY_UNKNOWN` (video exists, provenance genuinely unknown — this is
  what old/legacy rows and un-parseable nav args resolve to), `WALL_CAMERA`. A re-analysis of an
  existing climb must preserve its original source, never fall back to `MANUAL_LOG`.
  `parseAttemptSourceArg` in `ClimbNavHost.kt` implements the safe nav-arg fallback (→
  `LEGACY_UNKNOWN`, never a crash).
- **Real capture-entity ID policy implemented**: `CaptureEntityIds` (in `:shared-domain`) — UUID for
  new sessions, deterministic derivation (session id + version where relevant) for
  video/pose-artifact/attribution-result/inbox-item ids. Covered by 12 real behavioral tests
  (uniqueness, determinism, cross-session distinctness) in `CaptureEntityIdsTest.kt` — a reflection
  test alone was explicitly judged insufficient and replaced.
- **`WristbandCredential` hardened**: uses HMAC-SHA256 (`WristbandCredentialHashing`, pure JVM,
  `javax.crypto`) over a server-side secret, never an unsalted hash of the raw tag UID. The raw UID
  itself is documented as transient — never persisted, logged, used as a public doc id, or stored on
  the account. Explicitly documented as POC-level (UID-only wristbands remain physically cloneable)
  — not production-grade auth.
- **`RouteVersionSnapshotValidator`** added (`app/.../clubs/RouteVersionSnapshotValidator.kt`):
  legacy metadata-only routes (`wallId == null`) are always valid; a route with a `wallId` set must
  have every snapshot field non-null (venueId, zoneId, colorHex, grade, gradeSystem,
  publicNumberOrName, setAt, wallCalibrationId, visionProfileId, startPolicy, finishPolicy) or it's
  rejected with a list of missing fields. Must be run before any camera-verified route can be used
  for attribution in Phase 2+.
- **`routeColorHex`** representation finalized as packed `0xAARRGGBB` in a `Long`
  (`RouteColorHex` object in `:shared-domain` extracts alpha/RGB/opacity), matching the app's
  existing `RouteColor.hex` convention.
- **Real on-device migration smoke test performed and passed** (see below) — this was explicitly
  required before Phase 1.2 could be considered closed, since Robolectric-based migration testing
  (attempted in Phase 1.1) hit a genuine, unresolved Room-driver/Robolectric incompatibility and was
  abandoned (fully reverted, no residual dependency/config change).

## Real migration smoke test — result: PASS

Procedure and result are recorded in detail in this session's transcript; summary:
- Built a clean v11 APK, cleared app data, created one climb (via imported video) + one pose
  analysis under a genuine v11 database.
- Restored Phase 1.2 code, rebuilt v13, reinstalled **in place** (no data clear) — a true
  upgrade-in-place.
- App launched with zero crash. Pulled the real on-device `climb.db` and confirmed via
  `node:sqlite` (Node has a built-in `sqlite` module, useful when no `sqlite3` CLI is available):
  `PRAGMA user_version` = 13, legacy rows' old fields unchanged, all new columns (`attemptSource` on
  both `climbs` and `climb_attempts`, plus `wallId`/`wallCalibrationId`/`captureSessionId`) null for
  the pre-migration rows, as expected.
- Confirmed via real UI: the old video still played back, and the old pose analysis still opened
  and rendered identically post-migration.

## Test suite status

`:app:testDebugUnitTest` + `:shared-domain:test`, read from actual JUnit XML (not just Gradle's
"BUILD SUCCESSFUL"): **357 tests total (304 app + 53 shared-domain), 0 failures, 0 errors.**

## Current file list (route-attribution work only)

**New module**: `shared-domain/` — pure Kotlin/JVM, no Android dependency, depended on by `:app`
via `implementation(project(":shared-domain"))`.
- `main/kotlin/com/example/climb/clubs/RouteAttributionEntities.kt`,
  `CaptureDomainEntities.kt`, `CaptureEntityIds.kt`, `WristbandCredentialHashing.kt`
- `main/kotlin/com/example/climb/colordetection/WallReferenceSpace.kt`,
  `FixedCameraRouteRegistrationConfig.kt`
- `main/kotlin/com/example/climb/analysis/metrics/HoldContactConfig.kt`
- `main/kotlin/com/example/climb/attribution/RouteAttributionScoringConfig.kt`
- Corresponding test files under `src/test/kotlin/...` (10 test files, 53 tests)

**Modified in `:app`**: `ClubEntities.kt` (extended `RouteVersionEntity`), `ClubRepository.kt`
(`routeVersionFromMap`/`toFirestoreMap`), `ClimbDatabase.kt` (`MIGRATION_11_12`,
`MIGRATION_12_13`, version 13), `ClimbAttemptEntity.kt` / `ClimbEntity.kt` (new nullable columns),
`ClimbNavHost.kt` (`parseAttemptSourceArg`, `AttemptSource` threading through nav args),
`ClimbDetailsInputScreen.kt` / `ClubAttemptVideoScreen.kt` (Phase 0.5 removals),
`RecordScreen.kt` / `TagScreen.kt` / `DetailScreen.kt` / `VideoSourceScreen.kt` (thread real
`AttemptSource` through instead of discarding/hardcoding it), `MemberClubNavHost.kt` (dropped
removed `ClubAttemptVideoScreen` params).

**New in `:app`**: `clubs/RouteVersionSnapshotValidator.kt`, plus test files
`RouteVersionMappingTest.kt`, `RouteVersionSnapshotValidatorTest.kt`, `TrustBoundaryRegressionTest.kt`,
`ClimbDatabaseMigrationTest.kt`, `ClimbDatabaseMigration13Test.kt`, `AttemptSourceArgParsingTest.kt`,
plus `app/schemas/.../12.json` and `13.json`.

## Outstanding before Phase 1.5 can start

1. **User approval** — the explicit blocker. Nothing else is missing to *start* 1.5; the plan doc's
   Phase 1.5 scope (NfcReaderAdapter/CameraSourceAdapter contracts, Camera Edge Device bootstrap,
   reference-frame capture) hasn't been touched yet.
2. Phase 1.25 (hardware spike) is listed **before** 1.5 in the phase order and hasn't been done —
   confirm with the user whether it's being skipped/deferred for this POC or genuinely gates 1.5.

## Device testing notes (carried over, still true — read before doing any on-device verification)

- **Tap-injection on this test device is unreliable**: taps can silently no-op, land on a
  stale/wrong queued target several screens back, or in observed cases background the whole app or
  (once, this session) surface an unrelated live WhatsApp call. Treat multi-step blind-tap
  navigation as unreliable.
- **The reliable method**: `adb shell uiautomator dump //sdcard/ui.xml` (double-slash — Git-Bash on
  Windows mangles a single-slash `/sdcard/...` path) + `adb pull //sdcard/ui.xml <local>`, then read
  the exact `bounds="[x1,y1][x2,y2]"` of the target node and tap its center. Do not estimate tap
  coordinates from a screenshot — the displayed image (924×2000) is scaled ~1.386x from real device
  pixels (1280×2772), and arithmetic mistakes here have caused real misfires this session.
- **Pulling the Room DB for direct inspection**: the DB file is `databases/climb.db` (not
  `climb_database`). Pull it with `adb exec-out run-as com.example.climb cat databases/climb.db >
  local.db`. No `sqlite3` CLI is available in this environment (device or host); Node.js's built-in
  `node:sqlite` (`import { DatabaseSync } from 'node:sqlite'`) works well as a substitute if Node is
  available on the host.
- Only clear app data with explicit user approval — it's destructive to whatever is on the test
  device.

## Carried over from before this effort, still not done (lower priority than the above)

1. **Publish `firestore.rules`** — accumulated changes from prior sessions still need a manual
   publish via the Firebase Console (no Firebase CLI in this sandboxed dev environment).
2. **Deploy Cloud Functions** — `functions/src/index.ts`'s existing FCM push-notification function
   still needs `firebase deploy --only functions` on the Blaze plan. (Phase 7 of the route-
   attribution plan will add more functions here before this eventually gets deployed together.)
3. **`TagScreenTest` test-isolation gap** — Save triggers a real `ClimbSyncWorker.enqueue(...)` side
   effect via WorkManager, not faked in the test.
4. **Route-color-detection threshold** — `STRICT_DELTA_E_THRESHOLD` capped at 20.0 is an accepted
   limit, not a bug; tap-to-calibrate (`RoiSampler.kt`) is the intended long-term fix.
5. `references/` (untracked, contains screenshots) and `.idea/deviceManager.xml` — left alone, not
   part of any commit's work.

## If asked to keep going

Summarize the phase status above and ask the user explicitly whether to start Phase 1.25 or Phase
1.5 — do not assume either is authorized just because Phase 1.2 passed its checkpoint.
