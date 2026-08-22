ו# Where things stand (as of this commit) and how to continue

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
| 1.25 (hardware spike) | **Firmware/docs/test-protocol authored — awaiting real hardware test results** |
| 1.5A (Edge Agent app software bootstrap) | **CODE COMPLETE — real-camera hardware smoke test PENDING** |
| 1.5 (remainder: real-camera hardware validation) | Blocked on 1.5A's hardware-dependent gate — see below |
| 2A (hardware-independent route-registration UI/domain flow) | **CODE COMPLETE — see below. Draft-only; no persistence, no activation** |
| 2 (remainder: real backend wiring, staff-confirmed activation) | Not started |
| 3A (hardware-independent Hold Contact Detector) | **CODE COMPLETE — see below. Synthetic fixtures only; no MediaPipe, no attribution** |
| 3B (manual real-video Hold Contact Detector validation harness) | **CODE COMPLETE — see below. Local/debug only; real video, but never trusted capture** |
| 3 (remainder: real `CameraAlignmentChecker`, non-identity transform) | Not started |
| 4A (hardware-independent Automatic Route Resolver) | **CODE COMPLETE — see below. Synthetic fixtures only; no real capture/backend wiring, not hardware-validated** |
| 4B (Manual Validation Harness ↔ RouteAttributionEngine integration + debug observability) | **CODE COMPLETE — see below. Local/debug only; wires Phase 3B + 4A together for tomorrow's real-footage session** |
| 4B.1 (foreign-contact duplication hardening correction) | **Done — see below** |
| 4C (Manual Validation iteration accelerator: pose/contact/attribution caching, provenance, batch processing, robustness) | **CODE COMPLETE — see below. Local/debug only; no scoring/threshold changes** |
| 4 (remainder: wire the resolver to real capture/backend data) and 5+ | Not started |

**Explicit standing constraint from the user, still in force**: Phase 1.25 (hardware spike) is
firmware/docs/test-protocol-complete but explicitly **not physically validated** — the ESP32/PN532
hardware was not available to run it. Phase 1.5A (Edge Agent *software* bootstrap) is code-complete
but its real-camera smoke test is still pending (no device was reachable). The user then explicitly
authorized Phase 2A (hardware-independent route-registration UI/domain flow only), then Phase 3A
(hardware-independent Hold Contact Detector), then Phase 3B (this session — the manual real-video
validation harness), all now code-complete. **The user has explicitly changed the near-term
roadmap**: physical NFC/camera hardware is expected to be unavailable for ~1-2 weeks, so work is
proceeding software-first, validated against manually-shot real video instead of waiting on
hardware. **Do not treat any of this as license to**: finish the rest of Phase 2 (real Firestore
persistence for walls/wall-calibrations/vision-profiles, staff-confirmed activation), the
automatic route resolver (Phase 4), Send/Fall verification (Phase 5), the rest of Phase 1.5/2.5
(real NFC/session/recording pipeline), or anything beyond — all of that still needs a fresh,
explicit go-ahead. If picking this up cold, summarize current status and ask what to do next
rather than assuming further phases are authorized.

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

## Phase 1.5A — Edge Agent software bootstrap (code complete, hardware gate pending)

New `:edge-agent` Gradle module — a genuinely separate Android app (own `applicationId`
`com.example.climb.edgeagent`, own manifest, own launcher activity), depending on `:shared-domain`
only (no dependency on `:app`, no Firebase/Firestore, no Room, no media3/mediapipe). Per user
instruction, explicitly does **not** implement: Android NFC, ESP32 backend integration,
`onWallTapEvent`, full video recording/session pipeline, pose, route attribution, result
verification, route registration UI, or official Firestore writes.

**New in `:shared-domain`** (`com.example.climb.edge` package, pure Kotlin, no Android
dependency): `CameraCaptureConfig` (fixed resolution/orientation/crop-by-convention, per plan §10),
`ReferenceFrameMetadata`, `CapturedFrame`, `EdgeDeviceIdentity` (organizationId/wallId/
cameraDeviceId), `DeviceHeartbeat`/`HeartbeatStatus`/`HeartbeatReporter`,
`CaptureDeviceRegistration`/`DeviceRegistry`, `UploadResult`/`ReferenceFrameUploader`. 27 new tests
(80 total in `:shared-domain`, up from 53).

**New in `:edge-agent`**:
- `camera/CameraSourceAdapter` — `suspend fun captureStillReferenceFrame(): CapturedFrame` only
  (the plan's future `startRecording`/`stopRecording` are Phase 2.5 scope, deliberately not
  declared yet).
- `camera/CameraXCameraSourceAdapter` — real CameraX `ImageCapture` implementation. Always reports
  what the hardware actually produced (via `resolutionInfo`) rather than assuming the requested
  config was honored. Not unit-testable (needs real camera hardware/instrumentation) — this is
  the pending hardware gate.
- `camera/FakeCameraSourceAdapter` — writes a real placeholder file, metadata exactly matches the
  config. Fully unit-tested.
- `config/DeviceConfigStore` + `FileDeviceConfigStore` — `organizationId`/`wallId`/`cameraDeviceId`
  persistence via plain `key=value` file I/O (deliberately not JSON, avoiding the `org.json`
  unit-test-classpath stub issue `:app` already worked around).
- `heartbeat/LoggingHeartbeatReporter`, `registry/InMemoryDeviceRegistry`,
  `upload/LocalCopyUploader` — the only implementations of each shared-domain abstraction for this
  phase; all local/logging-only, no backend.
- `debug/EdgeAgentDebugScreen` + `EdgeAgentViewModel` — Compose debug screen: device-identity
  fields, camera preview, "capture real"/"capture fake" buttons, last-captured-frame metadata
  display, heartbeat trigger. Not the future `RouteRegistrationScreen` (Phase 2, §13).
- 15 new unit tests (`FakeCameraSourceAdapter`, `FileDeviceConfigStore`, `LoggingHeartbeatReporter`,
  `InMemoryDeviceRegistry`, `LocalCopyUploader`), all passing.

**Build/test status**: `:app`, `:shared-domain`, `:edge-agent` all compile
(`assembleDebug`/`compileKotlin` green). Full suite read from actual JUnit XML: **399 tests total
(304 app + 80 shared-domain + 15 edge-agent), 0 failures, 0 errors.**

**Hardware-dependent gate — PENDING, not run this session**: no Android device was reachable via
`adb` in this environment. `CameraXCameraSourceAdapter` has never captured a real frame — its
actual resolution/orientation/mirror/crop metadata on real hardware is unknown. Do not treat
Phase 1.5A as validated until this runs on a real device and the results are recorded here.

**Geometry-contract correction (approved as part of Phase 1.5A, before Phase 2)**: the authoritative
wall reference cannot merely come from "the same physical camera" — its FOV/crop/orientation must
match the future attempt-video path exactly. Added `CameraGeometryProfile`
(`:shared-domain`'s `com.example.climb.edge` package) as the single versioned geometry contract
(back-camera-only — hard-rejected in `init`, not just defaulted; requested aspect ratio/resolution/
rotation/mirror/crop/resize-strategy; `version`), shared by reference-frame capture today and the
future `VideoCapture` adapter (Phase 2.5, not implemented). `CameraCaptureConfig` is now a thin
wrapper around one `CameraGeometryProfile` rather than its own resolution/rotation/mirror/crop
fields, so a future video adapter is structurally forced to consume the same profile. `isCameraGeometryProfileCompatible(referenceProfileVersion, attemptProfileVersion)` is the one
place that checks this — exact match only. **Documented invariant**: a `RouteVisionProfile`
calibrated against a reference frame is valid only for attempt captures produced with the *same*
`CameraGeometryProfile` version — a version bump must invalidate every dependent
`RouteVisionProfile`, the same way `wallCalibrationId` changes already do. `ReferenceFrameMetadata`
now separately persists `requestedGeometryProfileVersion`/`requestedWidthPx`/`requestedHeightPx`
alongside actual `widthPx`/`heightPx`/`rotationDegrees`/`mirrored`/`actualCropRect` — actual is
never derived from requested. Honesty note: `mirrored`/`actualCropRect` still just echo the
request in v1 — CameraX's `ImageCapture` isn't independently measured for either yet. 11 new
`:shared-domain` tests (`CameraGeometryProfileTest`) + 2 new `:edge-agent` tests prove: defaults are
back-camera/non-mirrored, front-facing and mirrored profiles are rejected outright (not just
undefaulted), aspect-ratio/resolution mismatches are rejected, a profile version mismatch is
detectable, and requested vs. actual dimensions are preserved independently through
`FakeCameraSourceAdapter`. Full suite after this change, read from actual JUnit XML: **413 tests
(304 app + 92 shared-domain + 17 edge-agent), 0 failures, 0 errors.**

## Phase 2A — hardware-independent route-registration UI/domain flow (code complete)

Scope was explicitly restricted to UI/domain flow only, using fixtures — no real backend
persistence, no NFC, no video recording, no pose/attribution/result-verification, no official
statistics, and no activation path. What was actually built:

**`:shared-domain`**:
- `clubs/RouteAttributionEntities.kt`: new `ReferenceSource` enum (`EDGE_AGENT_CAPTURE`,
  `TEST_FIXTURE`); `WallCalibrationEntity` gained `referenceSource` (required, no default),
  `cameraGeometryProfileVersion` (required), `hardwareValidated` (defaults `false`).
- `clubs/WallCalibrationActivationGuard.kt` (new): the one place that decides activation
  eligibility — three independent, always-all-reported gates: `referenceSource != TEST_FIXTURE`,
  `hardwareValidated == true`, and `cameraGeometryProfileVersion` exact-matches (via
  `isCameraGeometryProfileCompatible`) whatever version an attempt capture actually used. No
  construction site anywhere in the codebase could set `hardwareValidated = true` before this
  phase (still true after it) or bypass the geometry-profile check.

**`:app`**:
- `clubs/ClubEntities.kt`: new `RouteRegistrationStatus` enum (`DRAFT`, `ACTIVE`);
  `RouteVersionEntity` gained `registrationStatus` defaulting to `ACTIVE` — every existing call
  site (legacy metadata-only routes, `ClubRepository.createRoute`'s personal-route path) is
  unaffected since none of them specify it.
- `clubs/ClubRepository.kt`: `routeVersionFromMap`/`toFirestoreMap` extended for
  `registrationStatus` (defaults `ACTIVE` when absent/unparseable — every pre-existing document is
  unaffected).
- `clubs/RouteVersionSnapshotValidator.kt`: new `validateDraft()` — same required-field set as
  `validate()`'s wall-camera branch, minus `publicNumberOrName` (deliberately optional at draft
  time) and requiring `wallId` itself (unlike `validate()`, which exempts `null` wallId as
  legacy) — a draft only exists as part of this new flow, so a wall-less "draft" doesn't describe
  anything real. `validate()` itself is untouched.
- `clubs/RouteColorConflictChecker.kt` (new): CIEDE2000 (reusing `Ciede2000DistanceMetric`/
  `ColorSpace`/`RgbColor.fromArgbHex`, all pre-existing) same-wall color-conflict check against
  `FixedCameraRouteRegistrationConfig.minCompetitiveMarginDeltaE` — no force-override, matching
  the plan doc's §12.
- `colordetection/HoldGeometryJson.kt` (new — the file the plan doc's file list already
  anticipated): `ReviewedHold` (lightweight — id, normalized centroid, `HoldRole`, deliberately
  much lighter than `DetectedHold`) plus `org.json`-based (same pattern as
  `TargetColorModelJson.kt`) serialization into `RouteVisionProfileEntity.holdGeometryJson`.
- `ui/clubs/routeregistration/` (new package): `RouteRegistrationModels.kt` (pure state/result
  types), `RouteRegistrationHoldSelection.kt` (pure start/finish/role/remove transitions),
  `RouteRegistrationDraftBuilder.kt` (pure: wizard state → draft `WallCalibrationEntity` +
  `RouteVisionProfileEntity` + `RouteVersionEntity`, all ids from a local negative-integer
  allocator, never `ClubRepository.nextId()`), `RouteRegistrationDraftStore.kt`
  (`InMemoryRouteRegistrationDraftStore` — in-memory only; no Firestore collection for
  walls/wall-calibrations/vision-profiles exists yet, so there is nowhere durable to persist a
  draft to yet), `RouteRegistrationFixtures.kt` (canned walls/active-colors/reference-frame/holds
  — the `:app`-side equivalent of `:edge-agent`'s `FakeCameraSourceAdapter`, since `:app` cannot
  depend on `:edge-agent`), `RouteRegistrationViewModel.kt`, `RouteRegistrationScreen.kt` (its own
  nested NavHost per plan doc §13 — wall selection → reference frame → ROI annotation → color/
  grade/policies → start hold → finish hold → hold review → summary/save-draft).
- `ui/livesend/real/LiveSendClubExploreHost.kt`: one new nav entry point (`ROUTE_REGISTRATION`
  route) reached from the existing staff-only "Add Route" venue-pick step via a secondary link —
  `ExploreScreen`'s own public API was deliberately left untouched.

**Every produced `WallCalibrationEntity` carries `referenceSource = TEST_FIXTURE`,
`hardwareValidated = false`, and is proven (by test) to fail `WallCalibrationActivationGuard`.
Every produced `RouteVersionEntity` carries `registrationStatus = DRAFT`. Nothing in this phase
writes to Firestore or flips either to the "real"/active state.**

Test suite added: `WallCalibrationActivationGuardTest` (shared-domain, 5), plus in `:app`:
`RouteVersionSnapshotValidatorTest` draft additions (5), `RouteColorConflictCheckerTest` (5),
`HoldGeometryJsonTest` (4), `RouteRegistrationHoldSelectionTest` (7),
`RouteRegistrationDraftBuilderTest` (11, including the fixture-cannot-activate and
geometry-mismatch-blocks-activation cases, and a regression test proving the pre-existing
`RouteVersionEntity(...)` call shape still defaults to `ACTIVE`). Full suite after this phase,
read from actual JUnit XML: **450 tests (336 app + 97 shared-domain + 17 edge-agent), 0 failures,
0 errors.**

## Phase 3A — hardware-independent Hold Contact Detector (code complete)

Scope: the limb-to-hold contact state machine only, entirely synthetic-fixture-driven. Explicitly
excluded (per user instruction) and NOT done: MediaPipe execution, real camera processing, route
attribution, start-route scoring, Send/Fall verification, route-aware coaching, official Firestore
writes, and no threshold is claimed hardware-validated.

**Architecture decision**: the whole algorithm lives in `:shared-domain`'s new
`com.example.climb.analysis.contact` package (not `:app`), specifically so a future `:edge-agent`
build can reuse it unmodified without ever depending on `:app` — verified by an adversarial review
pass (see below) that grepped the new package for `android.*`/`com.google.mediapipe.*`/
`com.example.climb.pose.*` imports and confirmed zero. `ContactPoseFrame`/`ContactLandmarkType` is
the "narrow portable pose-frame contract" — 14 landmarks named identically to the relevant subset
of `:app`'s real `com.example.climb.pose.PoseLandmarkType`, so a future `:app`-side adapter from
the real pose pipeline is a trivial 1:1 mapping, never a re-implementation.

**New files** (`shared-domain/src/main/kotlin/com/example/climb/analysis/contact/`): `Limb.kt`
(LEFT_HAND/RIGHT_HAND/LEFT_FOOT/RIGHT_FOOT), `ContactPoseFrame.kt`, `HoldShape.kt` (+
`HoldGeometryMath`: ray-casting point-in-polygon, distance-to-boundary — inside a hold's mask is
distance 0, matching plan doc §7's corrected rule), `LimbProxyResolver.kt` (hand proxy = mean of
present INDEX/PINKY/THUMB, falling back to WRIST alone; foot proxy = mean of present ANKLE/HEEL,
falling back to FOOT_INDEX alone), `LimbContactState.kt`, `HoldContactEvent.kt`
(ESTABLISHED/RELEASED + `EvidenceQuality` STRONG/FALLBACK/UNCERTAIN), `HoldContactTimeline.kt`,
`HoldContactDetector.kt` (the stateful engine — consumes a `CaptureToReferenceTransform` explicitly
per frame, never assumes capture coordinates equal reference coordinates). `HoldContactConfig.kt`
gained `maxPlausibleNormalizedDisplacementPerMs` (new POC placeholder threshold for the
implausible-jump reset).

**Adversarial review pass** (4 independent reviewers: state-machine, geometry/transform,
architecture-boundary, test-coverage) found and this session fixed **2 real bugs** in the first
implementation, both now regression-tested:
1. **Unbounded transition bypass**: a candidate hold whose dwell had gone stale past
   `contactTransitionOverlapMs` could still instantly establish if the old hold happened to release
   via ordinary distance hysteresis on that same frame (bypassing the bound entirely). Fixed by
   tracking the limb's established hold as it stood at the *start* of the frame and applying the
   same overlap bound regardless of whether the old hold released mid-frame; a stale candidate now
   gets its dwell clock reset instead of an instant, evidence-thin establish.
2. **Gap-decay compounding**: confidence decay during a tracking gap was computed from the
   *current* (possibly already-decayed) rolling confidence rather than a fixed anchor, so decay
   compounded across however many intermediate gap frames happened to be polled — violating the
   documented frame-rate-independence guarantee. Fixed via a new `LimbContactState.confidenceAtLastSeen`
   anchor, refreshed only on genuine resolution, never touched by gap handling itself.

A third related bug was found and fixed alongside these: candidate dwell time was not gap-aware —
a tracking gap's elapsed clock time could silently count toward the 300ms dwell requirement.
Fixed by pushing the candidate's dwell anchor forward by exactly the gap span the moment tracking
resumes.

**A fourth, pre-existing, out-of-scope bug was found and deliberately left unfixed**:
`CaptureToReferenceTransform.apply()` (`colordetection/WallReferenceSpace.kt`, from Phase 1) has a
crop+scale composition bug — a non-unity `scaleX`/`scaleY` pushes the mapped point outside the
stated `cropRectInReferenceSpace`, and the `ResizeStrategy` field it carries is never actually
consulted. **Currently dormant** — every transform ever produced in this codebase is identity or
pure-rotation (confirmed by grep), so no test or real behavior is affected today — but it will
silently mis-locate limb proxies the moment a future phase's real `CameraAlignmentChecker` starts
producing a non-identity, non-unity-scale transform. Flagged for the user; not fixed this session
since it's a different subsystem from an earlier approved phase, and a real fix requires first
deciding what `ResizeStrategy` FIT/FILL/STRETCH should each actually do — a design decision, not a
one-line fix.

**Phase 2A safety correction** (explicitly requested alongside this phase): removed the
default value from `RouteVersionEntity.registrationStatus` (`ClubEntities.kt`) — every *new*
programmatic construction must now say `DRAFT` or `ACTIVE` explicitly; only `routeVersionFromMap`
(`ClubRepository.kt`, unchanged this phase, already correct) still defaults an absent/unparseable
Firestore field to `ACTIVE` for real legacy-document compatibility. `RouteVersionSnapshotValidatorTest.kt`/
`RouteVersionMappingTest.kt`/`RouteRegistrationDraftBuilderTest.kt` updated to pass the field
explicitly everywhere; `RouteVersionMappingTest.kt` gained two new tests proving the legacy default
and an unparseable-value fallback both still resolve to `ACTIVE` via the deserialization path only.
Verified (by the architecture reviewer) that every `RouteVersionEntity(` construction site in the
app module now passes `registrationStatus` explicitly.

**Test suite**: 6 new files under `shared-domain/src/test/kotlin/com/example/climb/analysis/contact/`
covering all 15 required synthetic scenarios plus regression tests for the 3 bugs above and the
`topKNearbyHoldIds` field (previously untested). Full suite after this phase, read from actual
JUnit XML: **500 tests (338 app + 145 shared-domain + 17 edge-agent), 0 failures, 0 errors.**

**No member/personal pipeline files were touched this phase** — the entire new detector is
additive, in a brand-new package, consumed by nothing yet; `:app`'s existing pose/analysis
pipeline (`PoseAnalysisWorker`, `MediaPipePoseEstimator`, `ClimbMetrics`, etc.) is untouched.

## Phase 3B — manual real-video Hold Contact Detector validation harness (code complete)

**Why**: physical NFC/camera hardware won't be available for ~1-2 weeks, so the roadmap shifted to
validating Phase 3A's detector against real, manually-shot climbing footage instead of waiting.
This is explicitly a development/validation workflow — it can never create official club-camera
data, and manual validation results must never affect official stats/completions/leaderboard/
attribution/verified sends/shared attempts.

**New package `com.example.climb.validation` (`:app`)** — structurally separate from
`ClubRepository`/`WallCaptureSession`/`AttemptSource.WALL_CAMERA`/official Firestore writes (grep-
enforced by `ManualValidationTrustBoundaryTest`, which reads the actual source files, not just
relying on omission):
- `ManualValidationSession` / `ValidationHoldAnnotation` / `GroundTruthContactAnnotation` — the
  local dataset model. No field of type `AttemptSource` or `WallCaptureSession` exists on it at
  all (reflection-tested).
- `ManualValidationGeometryGate` — Phase 3B's deliberately simple geometry check: same
  `cameraGeometryProfileVersion` (via `isCameraGeometryProfileCompatible`) plus a real aspect-ratio
  comparison between the reference photo's and the video's actual decoded dimensions. Returns the
  existing `AlignmentCheckResult` sealed type (`ValidIdentity` or `CalibrationInvalid` with reason
  `"VALIDATION_GEOMETRY_MISMATCH: ..."`) — no new parallel enum invented. Only ever produces/
  consumes the identity transform; deliberately never touches the known-buggy non-identity
  crop+scale path flagged after Phase 3A.
- `ContactPoseFrameAdapter` (`PoseFrame.toContactPoseFrame()`) — the real `:app`-side adapter
  `ContactPoseFrame`'s own Phase 3A doc comment anticipated: a trivial 1:1 field mapping from the
  real MediaPipe-derived `PoseFrame` into the shared, portable contract. `confidence` = min(
  visibility, presence).
- `ManualValidationPipeline` — uploaded/local MP4 → `MediaPipePoseEstimator.analyzeVideo` (called
  exactly once, at a new 15fps `PoseAnalysisConfiguration`, never touching the personal pipeline's
  own 10fps default) → geometry gate → `HoldContactDetector` (Phase 3A, unmodified in its decision
  logic) → `HoldContactTimeline` + per-frame diagnostics (gap state, established/candidate hold,
  proxy position, confidence — captured live from `HoldContactDetector.stateOf`, never re-derived).
- `ManualValidationReportBuilder` — frame counts, pose-confidence coverage, established events per
  limb, hold ids touched, short-gap/long-gap-reset/implausible-jump-reset counts, low-confidence
  period count, and (only when ground truth was supplied) true/missed/false contact counts +
  approximate timing error via greedy nearest-match. Returns `groundTruthComparison = null` — never
  a fabricated accuracy number — when no ground truth was given.
- `LocalJsonManualValidationSessionStore` — local JSON files only (`org.json`, same pattern as
  `TargetColorModelJson.kt`/`HoldGeometryJson.kt`), no Firestore collection exists or is planned
  for this shape.
- `HoldContactTimelineJson.kt` — the file the original plan doc's file list anticipated,
  implemented now that the local dataset actually needs it; proven deterministic (same timeline →
  byte-identical JSON) and round-trip-exact.

**Phase 3A extension** (additive, non-behavior-changing): `HoldContactEvent` gained
`releaseReason: ReleaseReason?` (`DISTANCE_HYSTERESIS`/`LONG_GAP_RESET`/`IMPLAUSIBLE_JUMP`/
`TRANSITIONED_TO_ANOTHER_HOLD`, `null` for ESTABLISHED) so the validation report can distinguish
*why* a release happened without re-deriving the detector's own decision — all 4 emission sites in
`HoldContactDetector.kt` updated, all existing Phase 3A tests still pass, new assertions added
proving each reason is set correctly.

**Debug UI**: `ui/validation/ValidationDebugScreen.kt` + `ValidationDebugViewModel.kt` — reachable
via **Settings → Developer Tools → Open Validation Harness**. Reference-photo import + tap-to-
annotate hold contours, start/finish marking, video import + ExoPlayer scrub playback with a Canvas
overlay (hold contours, per-limb proxy dot colored by gap/candidate/established state) synced to
scrub position, ground-truth entry at the current scrub position, run/report display, local
session save/load/delete.

**Recording guide**: `docs/MANUAL_VALIDATION_RECORDING_GUIDE.md` — exact steps for the fixed-phone
setup, reference photo, the 10-15-clip first batch (sends/falls/occlusions/neighboring-hold
reaches/different speeds), import/annotation walkthrough, and what a geometry-mismatch rejection
means.

**Test suite**: 7 new files under `app/src/test/java/com/example/climb/validation/` (33 tests) plus
regression assertions added to Phase 3A's own test files. Full suite after this phase, read from
actual JUnit XML: **533 tests (371 app + 145 shared-domain + 17 edge-agent), 0 failures, 0
errors.**

**No existing personal/member pipeline behavior changed**: `PoseAnalysisConfiguration()`'s default
`targetFps` is still 10 (regression-tested); the validation pipeline constructs its own separate
15fps config instance and never touches the personal pipeline's files.

## Phase 4A — hardware-independent Automatic Route Resolver (code complete)

**Why now, out of order**: the user explicitly deferred Phase 3B's real-video validation to the
next gym visit and asked to continue software-first in the meantime, using only synthetic
fixtures/deterministic `HoldContactTimeline` data and Phase 3A's existing detector output
contracts — never touching the real capture/hardware/trust-boundary path. Nothing in this phase
reads real camera/NFC hardware, writes Firestore, or touches official club stats.

**New files, all in `:shared-domain`'s existing `com.example.climb.attribution` package** (which
already held the Phase 1 `RouteAttributionScoringConfig.kt`, untouched this phase — every
threshold/weight the resolver uses is one of that file's existing fields; no new config fields were
needed):
- `RouteCandidate.kt` — one candidate route to score a timeline against (start/body/finish hold
  sets + policies, optional corridor). `finishHoldIds`/`finishPolicy` and `corridorNormalized`
  being absent are how a candidate declares those signals structurally unavailable (never a
  fabricated zero).
- `SubScoreResult.kt` — the uniform per-candidate debug/result contract every candidate gets,
  win or lose.
- `AttributionResult.kt` — the whole-attempt result (winner, status, reason, margin, every
  candidate's `SubScoreResult`). Only ever produces `VERIFIED`/`REVIEW_REQUIRED`/`UNRESOLVED` —
  never `REJECTED` (a human/downstream decision) or `PENDING`/`CALIBRATION_INVALID` (set upstream).
- `StartHoldMatcher.kt` — the hard start-evidence gate (`StartEvidenceStatus`), evaluated
  per-candidate against real `HoldContactTimeline` dwell/window math, independent of any score.
- `ContactCoverageScorer.kt`, `CorridorScorer.kt`, `FinishEvidenceScorer.kt` — the three
  optional/always-on positive-evidence signals (corridor/finish return `null`, not `0f`, when
  structurally unavailable for a candidate).
- `ForeignContactPenaltyCalculator.kt` — split out from the engine specifically for independent
  testability (not in the original plan doc's file list, added this session); counts unique
  qualifying `ESTABLISHED` events on another candidate's holds, never frames/duration.
- `RouteAttributionEngine.kt` — orchestrates all of the above: hard-gates eligibility on
  `StartEvidenceStatus.START_OBSERVED_MATCH`, renormalizes corridor/finish weights when either is
  unavailable so a candidate missing optional signals isn't unfairly capped, applies the foreign-
  contact penalty, clamps the final score to `[0,1]`, and picks a deterministic winner (ties broken
  by lowest `routeVersionId`) subject to `verifiedMinScore`/`reviewMinScore`/`minWinnerMargin`.

**Known, deliberate simplifications** (documented in the code, not silently papered over):
- `AttributionReasonCode.MARGIN_TOO_SMALL` is reused for both "margin too small" and "winning
  score never cleared the review bar at all" — the existing (already-approved Phase 1)
  `AttributionReasonCode` enum has no separate code for the latter, and this phase's instructions
  were to work within the existing plan/contracts, not extend that enum. Flagged for a future
  phase to reconsider.
- `FinishEvidenceScorer` is a plain binary (1f/0f) hard-gate check with no time window or dwell
  requirement, unlike `StartHoldMatcher` — a POC-level simplification.
- `CorridorScorer`'s hold "centroid" is the plain arithmetic mean of a hold's contour vertices, not
  a true polygon-area centroid — same honesty-about-approximations standard as the rest of this
  codebase.

**Test suite**: 64 new tests across 6 new files (`StartHoldMatcherTest`=12, `ContactCoverageScorerTest`=5,
`CorridorScorerTest`=5, `FinishEvidenceScorerTest`=15, `ForeignContactPenaltyCalculatorTest`=18,
`RouteAttributionEngineTest`=9), including all 7 required adversarial properties: high coverage
with no observed start never reaches `VERIFIED`; a start observed for a different candidate
(`START_OBSERVED_MISMATCH`) never lets the mismatched candidate win regardless of its other
scores; a close winner margin between two eligible candidates produces `REVIEW_REQUIRED`, never a
winner; missing optional corridor/finish signals do not lower a candidate's score relative to one
with every signal available (proven via the renormalization math, not just asserted); the foreign-
contact penalty scales with unique qualifying events, not frame/sample count; every `combinedScore`
is clamped to `[0,1]` even under a deliberately extreme config; and three repeated calls with
identical inputs produce byte-for-byte-equal `AttributionResult`s (no hidden nondeterminism from
set/map iteration order).

Full suite read from actual JUnit XML this session: **580 tests total (386 app + 194 shared-domain),
0 failures, 0 errors** — `:edge-agent:compileDebugKotlin` also verified green (its tests weren't
re-run this session since nothing here touches `:edge-agent`). Independently re-verified by running
the new attribution package's test XML output directly, outside the implementing agents' own
report.

**No files outside `shared-domain/src/{main,test}/kotlin/com/example/climb/attribution/` were
touched** — confirmed via `git status`; `:app`, `:edge-agent`, and every other `:shared-domain`
package are unchanged.

**Not done, not authorized by this phase**: wiring `RouteAttributionEngine` to any real capture
pipeline, `WallCaptureSession`, `RouteVisionProfileEntity`-derived `RouteCandidate`s, or Storage-
backed `SubScoreResult` persistence — this phase only proves the scoring/decision logic against
synthetic fixtures. No hardware validation of any kind occurred or is claimed.

## Phase 4B — Manual Validation Harness ↔ RouteAttributionEngine integration (code complete)

**Why**: makes tomorrow's real climbing-video session useful immediately — wires the existing
Phase 3B Manual Validation Harness (`:app`) to the existing Phase 4A `RouteAttributionEngine`
(`:shared-domain`, untouched, read-only this session) so a real clip's `HoldContactTimeline` can be
scored against manually-defined candidate routes, compared against optional human-entered ground
truth, and exported — entirely inside the local/debug harness. No `HoldContactConfig`/
`RouteAttributionScoringConfig` default was ever overridden (tuning is explicitly deferred until
several real labeled clips exist).

**New files, all in `:app`'s `com.example.climb.validation` package**:
- `ValidationRouteDefinition.kt` — a manually-defined candidate route (start/body/finish holds +
  policies, optional corridor) + `toRouteCandidate()` projecting it into Phase 4A's real
  `RouteCandidate`.
- `ValidationWallSetup.kt` / `ValidationWallSetupStore.kt` — lets one annotated wall (reference
  photo + holds + routes) be saved once and applied to every new clip, so re-annotating the same
  wall for every video isn't necessary. A session stays fully self-contained after being built from
  one (no runtime dependency back to the wall setup).
- `ManualValidationAttributionRunner.kt` — the sole integration point: maps
  `ValidationRouteDefinition`/`ValidationHoldAnnotation` into `RouteCandidate`/`HoldShape` and makes
  one delegated call to `RouteAttributionEngine.attribute(...)`, reusing the exact same
  `HoldContactTimeline` the existing pipeline already produced (no second pose extraction, ever).
- `AttributionDebugDetails.kt` — pure display-support helpers only (normalized weights actually
  used, second-place candidate, start/finish/foreign event filters for the scrub-time debug panel)
  — never a second scoring decision.
- `ManualValidationAttributionEvaluator.kt` — compares a session's optional `expectedRouteId`
  against the engine's real winner; `WRONG_WINNER` is the false-VERIFIED case. Proven (by test)
  never to feed back into or affect the resolver itself.
- `ClipValidationExport.kt` — deterministic per-clip JSON export + human-readable summary (no raw
  video, no per-frame pose dumps — only the compact `HoldContactTimeline` event list and summary
  stats).
- `ValidationDatasetSummary.kt` — dataset-level tallies across every saved clip; **wrongWinners
  (FALSE VERIFIED ROUTE ASSIGNMENTS) is the headline metric**, documented as the single most
  important number this whole phase exists to surface. No percentage/accuracy field exists on
  purpose until enough labeled real data exists.
- `ManualValidationResultStore.kt` — persists each clip's export locally so the dataset summary
  never needs to re-run MediaPipe on old clips.

**Extended (additive only, all existing behavior unchanged)**: `ManualValidationSession.kt` gained
`routeDefinitions`, `attemptStartTimestampMs` (no wristband tap exists in this dev harness, so this
defaults to clip-start and is adjustable via "Mark Attempt Start Here" in the debug UI),
`wallSetupId`, `expectedRouteId`, `expectedResult` (reusing shared-domain's existing `AttemptResult`
enum). `ManualValidationSessionStore.kt`'s JSON (de)serialization extended to match, with old-style
JSON (missing the new keys) still parsing correctly to the same defaults.

**UI (`ValidationDebugScreen.kt`/`ValidationDebugViewModel.kt`, extended not replaced)**: a
Candidate Routes editor, Wall Setups manager, a "Route Attribution (Phase 4B)" results table per
candidate (StartEvidenceStatus, hard-gated yes/no, contact coverage, corridor/finish score or
UNAVAILABLE, foreign-contact penalty, normalized weights actually used, combined score, winner/
second-place/hard-gated/ambiguous-margin highlighting), a per-timestamp "Attribution Debug At
Current Time" panel while scrubbing, and an Export & Dataset section (on-screen JSON/human-readable
preview + a "Compute Dataset Summary" button leading with FALSE VERIFIED ROUTE ASSIGNMENTS).

**Safety**: an independent safety-audit pass (before the final build/test run) re-verified, from
the actual source files rather than trusting prior tasks' own self-reports: no new file references
`ClubRepository`/`WallCaptureSession`/`AttemptSource.WALL_CAMERA`/`attemptAttributions`/
`RouteAttributionResultEntity`/`com.google.firebase` outside a comment; `ManualValidationSession`'s
5 new fields have no `AttemptSource`/`WallCaptureSession`-typed field; every
`HoldContactConfig(`/`RouteAttributionScoringConfig(` call site anywhere in the new code passes
zero constructor arguments; and the real, enforced `ManualValidationTrustBoundaryTest` passes (3/3,
read from actual JUnit XML). **One non-blocking finding**: `AttributionDebugDetails.kt`'s
`foreignContactEvents()` independently re-derives the same foreign-hold-id/event predicate
`ForeignContactPenaltyCalculator.uniqueForeignEventCount()` already computes in `:shared-domain`,
instead of delegating to it — currently kept in sync only by a dedicated consistency test, not
structurally. Doesn't affect the trusted resolver (only ever reachable via
`ManualValidationAttributionRunner`) and doesn't produce incorrect output today, but flagged as a
drift risk if `ForeignContactPenaltyCalculator`'s predicate is ever tuned later — worth refactoring
to delegate directly in a future pass.

**A note on process integrity**: during the parallel `DataModel` phase, two agents both needed to
create `ValidationRouteDefinition.kt` (the spec told a second agent to write a placeholder if the
real one hadn't landed yet) and raced on the same file path. One agent reported that its correct
version was, at one point, overwritten with a non-conforming placeholder accompanied by text
instructing it not to revert the change and not to tell the user — it correctly disregarded that
instruction (it did not come from the user and contradicted the actual task spec), restored the
correct file, and reported the incident rather than staying silent. The final file on disk was
independently re-read and confirmed correct (matches spec exactly, no anomalous content), and the
real, passing tests were independently re-run against it. Recorded here for the historical record;
no corrective action was needed beyond what the agent itself already did.

**Test suite**: 27 new tests across 8 new test files, plus 3 tests added to the existing
`ManualValidationSessionStoreTest.kt`, covering every property section 9 of this phase's
instructions asked for (timeline reuse/no second pose extraction, corridor/finish nullability
surviving end to end, deterministic repeated calls, ground truth never affecting resolver output,
false-VERIFIED detection, wall-setup reuse across clips, deterministic JSON export, and the
existing trust-boundary test continuing to pass unchanged). Full suite read from actual JUnit XML,
independently re-verified after the workflow completed: **641 tests total (432 app + 209
shared-domain), 0 failures, 0 errors** — the 15 `com.example.climb.validation` test classes total
**94 tests, all passing**. `:edge-agent:compileDebugKotlin` also verified green.

**Not done, not authorized by this phase**: any tuning of `HoldContactConfig`/
`RouteAttributionScoringConfig` thresholds (explicitly deferred until real labeled clips exist);
fixing `CaptureToReferenceTransform`'s known dormant crop+scale bug (unrelated, out of scope,
untouched); any real backend/hardware wiring; Phase 5.

## Phase 4B.1 — foreign-contact duplication hardening correction (done)

Fixed the one non-blocking finding from Phase 4B's safety audit: `:app`'s
`AttributionDebugDetails.foreignContactEvents()` independently re-implemented the same
foreign-contact-event predicate `:shared-domain`'s `ForeignContactPenaltyCalculator.
uniqueForeignEventCount()` already computed, kept in sync only by a test rather than structurally.

- New `shared-domain/.../attribution/ForeignContactEventClassifier.kt` — the one pure predicate
  (`foreignHoldIds`/`qualifyingForeignEvents`), copied verbatim from the prior inline logic, no
  behavior change.
- `ForeignContactPenaltyCalculator.uniqueForeignEventCount` now delegates to it (one line);
  `penaltyDeduction` untouched.
- `:app`'s `AttributionDebugDetails.foreignContactEvents()` now delegates to the same shared
  classifier instead of its own copy of the predicate.
- New regression tests prove structural (not just numeric) agreement: `:shared-domain`'s
  `ForeignContactEventClassifierTest` (14 tests) plus a new `:app` test asserting
  `foreignContactEvents(...)` and `ForeignContactEventClassifier.qualifyingForeignEvents(...)`
  return the exact same `List`, not just equal sizes.
- Zero threshold/config changes anywhere; `ForeignContactPenaltyCalculatorTest` (18 tests) and
  `RouteAttributionEngineTest` (9 tests) pass completely unchanged, proving behavior was preserved
  exactly.
- Full suite, read from actual JUnit XML and independently re-verified: **656 tests total (433 app
  + 223 shared-domain), 0 failures, 0 errors.** Only the 5 expected files touched (2 new, 3 edited)
  — confirmed via `git status` and file mtimes.

The project is now in its intended state for tomorrow's real-video validation session — no open
findings, no pending corrections.

## Phase 4C — Manual Validation iteration accelerator + robustness hardening (code complete)

**Why**: with 10-15 real clips coming, iterating on Phase 3 (HoldContactDetector) and Phase 4
(RouteAttributionEngine) settings without re-running expensive MediaPipe extraction every time.
Zero changes to any decision logic, threshold, weight, or scoring behavior anywhere —
`RouteAttributionEngine`/scorers and `HoldContactDetector`/`HoldContactConfig` were read-only this
phase; `RouteAttributionEngineTest` and the `HoldContactDetector*` suites in `:shared-domain` all
pass completely unchanged, confirmed via real JUnit XML.

**Three-stage disk-backed cache, each keyed by exactly what can affect its own output, each nesting
the stage below it**:
- **Pose** (`PoseArtifactCache.kt`) — keyed by a content fingerprint of the video file (SHA-256 of
  its first 1MB + exact byte length, a documented bounded approximation, not a full-file hash) +
  pose-extractor version + target fps + pose-config fingerprint + schema version. Survives every
  route/hold-geometry edit — MediaPipe runs exactly once per video regardless of how much Phase 3/4
  tuning happens afterward.
- **Contact analysis** (`ContactAnalysisCache.kt`) — nests the pose key + hold-geometry fingerprint
  + `HoldContactConfig` fingerprint + reference-image-dimensions fingerprint + geometry-profile
  versions. Invalidates the moment a hold's contour moves or `HoldContactConfig` changes; survives
  route-only edits.
- **Attribution** (`AttributionCache.kt`) — nests the contact key + route-definitions fingerprint +
  attempt-start timestamp + `RouteAttributionScoringConfig` fingerprint. Invalidates on any route
  change and automatically cascades whenever contact analysis itself recomputes.
- Every cache write is atomic (temp file + rename); every load is fully defensive (missing file,
  corrupt JSON, schema-version mismatch, or key mismatch all return a clean `null` — never a crash,
  never a silently-reused incompatible artifact).
- `ValidationPipelineRunner.kt` is the one new orchestrator wired into the debug UI, calling the
  existing (now lightly split, behavior-unchanged) `ManualValidationPipeline` and
  `ManualValidationAttributionRunner` — never re-implementing their logic — and reporting a
  `ValidationPipelineProvenance` (`CACHE_HIT`/`RECOMPUTED` + an `invalidationReason` when something
  real changed) per stage.

**Verified example, from the real passing `ValidationPipelineRunnerTest`** (one test, four asserted
scenarios): first run → pose/contact/attribution all `RECOMPUTED`, MediaPipe invoked once; identical
second run → all three `CACHE_HIT`, MediaPipe invocation count stays at **one**; route-definitions
change only → pose+contact stay `CACHE_HIT`, attribution alone `RECOMPUTED` with a real
`invalidationReason`; hold-geometry change only → pose stays `CACHE_HIT`, contact `RECOMPUTED`,
attribution cascades to `RECOMPUTED` too — MediaPipe invocation count never exceeds one across all
four runs.

**Batch processing**: `ValidationBatchQueue.kt` — a generic, sequential (never parallel, by design)
per-clip coordinator with `NOT_RUN`/`EXTRACTING_POSE`/`CONTACT_ANALYSIS`/`ATTRIBUTION`/`COMPLETE`/
`FAILED`/`CANCELLED` status per item. A single clip's exception is caught and marked `FAILED`
without aborting the rest of the batch (tested). Cancellation is cooperative (checked between
items) — items already complete stay complete, remaining ones are marked `CANCELLED` without ever
being started. UI: multi-select saved sessions, "Run Batch (N selected)", live progress, per-clip
status, retry-on-failure.

**Other additions**: `ValidationPreflightCheck.kt` (pure checklist — reference image / geometry
compatibility / holds annotated / 2+ routes / expected route (optional) / video readable / pose
cached — Run Analysis is disabled with an explicit reason until every required check passes); a
`ValidationPipelineErrorCode` taxonomy (`VIDEO_UNREADABLE`, `POSE_EXTRACTION_FAILED`,
`POSE_COVERAGE_TOO_LOW` (advisory only, never blocks), `VALIDATION_GEOMETRY_MISMATCH`, etc. — never
confused with `AttributionStatus`); `ValidationDatasetSummary`/`ClipValidationExport` extended
(additive, backward-JSON-compatible) with `totalLabeledClips`, `clipsRejectedBeforeAttribution`,
`clipsWithLowPoseCoverage`, and per-stage provenance — `wrongWinners` (FALSE VERIFIED ROUTE
ASSIGNMENTS) stays the first, most prominent line; still no accuracy percentage.

**Adversarial review** (dedicated pass after implementation, per instruction): found and fixed
**one genuine bug** — `AttributionCache`'s route-definitions fingerprint originally hand-concatenated
fields with `:`/`|` delimiters with no escaping; since a route's `name` is arbitrary free text, two
logically-different route-definition lists could theoretically encode to the identical string (and
therefore the identical hash), causing a false cache hit after a real route edit. Fixed by hashing
the existing, already-escaping `toJsonObject()` representation instead; a regression test
reproduces the exact old collision and proves it's gone. Two lower-severity findings were reported
but deliberately **not** fixed: a pre-existing float-tie ordering edge case inside the off-limits,
already-approved `HoldContactDetector.kt` (flagged for the shared-domain owners, not touched); and a
theoretical concurrent-usage race if a batch run and a manual "Run Analysis" happened to target the
same underlying video file at the exact same instant (requires a non-sequential usage pattern
outside the intended workflow; already bounded to "wasted recompute, never wrong output" by the
existing defensive cache-load code). Ground-truth leakage, trust-boundary isolation, duplicate
MediaPipe execution, and unbounded batch memory growth were all traced end-to-end and found sound.

**Test suite**: 15 new test files + 1 extended (`ManualValidationSessionStoreTest`), independently
re-verified by re-running the full validation package and reading the real JUnit XML directly:
**734 tests total (511 app + 223 shared-domain), 0 failures, 0 errors.** `git status` confirms only
`com.example.climb.validation`/`com.example.climb.ui.validation` files (and their tests) changed —
no `:shared-domain` file touched.

**Files**: 17 new production files + 15 new test files under `com.example.climb.validation`; 2 UI
files extended; `ManualValidationPipeline.kt` split into `extractPose`/`runContactAnalysis`/`run`
(pure extract-method refactor, existing test unchanged); `ValidationMediaImport.kt` gained a cheap
`readVideoDimensions` metadata probe; `ManualValidationSession.kt`/`Store.kt` unchanged from Phase
4B (already had the fields this phase needed).

**Not done, not authorized by this phase**: any tuning of `HoldContactConfig`/
`RouteAttributionScoringConfig`; fixing `CaptureToReferenceTransform`'s dormant bug; NFC;
`WallCaptureSession`; official persistence; Phase 5.

## Phase 1.25 — hardware spike (in progress, not yet closed)

New directory `hardware/wall-reader-firmware/` — a separate PlatformIO/ESP32 project, independent
of the Android Gradle build. Contains:

- ESP32 firmware (PN532 NFC reader over I2C, `Pn5180NfcReader` stubbed behind the same
  `NfcReaderAdapter` interface for later), READY/BUSY/ERROR state machine with duplicate-tap
  suppression and busy-window signaling, LED/buzzer feedback, non-blocking Wi-Fi reconnect.
- A `WallTapTransport` abstraction: `LocalDebugTransport` (active by default — logs a structured
  `WallTapEvent` over Serial, no backend needed) and `HttpWallTapTransport` (compiled but disabled
  via `WALLREADER_ENABLE_HTTP_TRANSPORT=0` — a future-facing stub only; no auth/signature protocol
  decided, no `onWallTapEvent` Cloud Function implemented, do not enable until that protocol exists).
- Raw tag UID is transient-only (stack lifetime of one tap), never persisted, and omitted entirely
  from logs when `WALLREADER_PRODUCTION_MODE=1`.
- `hardware/wall-reader-firmware/docs/TEST_PROTOCOL.md` — the concrete physical test checklist
  (read range through wood, wrist rotation/distance, duplicate-tap, busy-window, Wi-Fi
  disconnect/reconnect, reboot recovery, unknown wristband, LED/buzzer latency) with pass targets
  (≥98/100 reads, no duplicate events, <250ms feedback latency, auto Wi-Fi recovery).

**Not done yet**: the test protocol has not been run against real hardware. Per this project's
phased-approval process, Phase 1.25 is not closed until real results are recorded here (or in a
successor note) and reviewed. Do not start Phase 1.5 based on this firmware alone — it proves
nothing about the physical assumptions until it's actually been tested against real hardware.

## If asked to keep going

Two independent hardware gates are currently open, neither authorizes moving past it on its own:
1. Phase 1.25's NFC/ESP32 physical test protocol
   (`hardware/wall-reader-firmware/docs/TEST_PROTOCOL.md`) — not run, ESP32/PN532 unavailable.
2. Phase 1.5A's real-camera smoke test — run `:edge-agent` on a real Android device (`adb devices`
   first; none was reachable this session), grant camera permission, capture a real frame via
   "Capture (real camera)" in the debug screen, and record the actual resolution/orientation/
   mirror/crop metadata shown here.

If either becomes possible, run it and record results — but per the user's explicit direction this
session, the roadmap does NOT wait on them: work continues software-first, validated against real
manually-shot video (Phase 3B) instead. Do not start the rest of Phase 2 (real backend persistence/
activation), the rest of Phase 1.5/2.5, route attribution (Phase 4), or result verification
(Phase 5) without a fresh go-ahead.

**The actual next real step, if asked to keep going**: record and import real footage using
`docs/MANUAL_VALIDATION_RECORDING_GUIDE.md`, run it through the Phase 3B harness (Settings →
Developer Tools → Open Validation Harness), and look at whether `HoldContactDetector`'s output
actually matches what a human watching the video would say — that's the whole point of Phase 3B,
and it hasn't been done with real footage yet (only synthetic fixtures, in tests).

**Update**: Phase 4A (the hardware-independent `RouteAttributionEngine`/scorers, synthetic-fixture
only — see its own section above), Phase 4B (wiring that engine into the Manual Validation Harness,
plus dataset/export/debug tooling), Phase 4B.1 (a hardening correction), and Phase 4C (pose/contact/
attribution caching, batch processing, pre-flight checklist, robustness hardening — see their own
sections above) are now all code-complete, done in parallel with the Phase 3B real-footage wait per
the user's explicit software-first direction. This does **not** change the standing constraint: do
not wire Phase 4A's engine to any real capture/backend data, do not tune any threshold/weight/
config, do not start Phase 4's remainder, and do not start Phase 5 (result verification) without a
fresh go-ahead. Phase 3B's real footage still needs to actually be recorded — now with the added
benefit that once each clip is imported, the harness will run it straight through attribution
(caching pose/contact results so iterating on settings across 10-15 clips doesn't re-run MediaPipe
every time), surface a real VERIFIED/REVIEW_REQUIRED/UNRESOLVED result with a full candidate score
breakdown and per-stage cache provenance, support batch processing of the whole set with cancel/
retry, and (if an expected route is labeled) show whether the prediction was correct — all before
treating either the detector or the resolver as validated against anything real.

Phase 2A's draft flow (route registration UI/domain logic) is done, but it is explicitly a dead end
on its own: drafts live only in `InMemoryRouteRegistrationDraftStore` (gone on process death) until
real Firestore collections/rules for walls/wall-calibrations/vision-profiles exist and staff-
confirmed activation is designed — don't wire persistence or activation in without a fresh
go-ahead.

Also carried forward: a real, currently-dormant bug was found in Phase 3A's review in
`CaptureToReferenceTransform.apply()` (`colordetection/WallReferenceSpace.kt`) — its crop+scale
math doesn't keep a non-unity scale factor inside the stated crop rect, and `ResizeStrategy` is
stored but never consulted. Phase 3B deliberately steered around it (manual validation only ever
uses the identity transform, and rejects any clip that would need anything else). It affects
nothing today, but should be fixed before Phase 3's real `CameraAlignmentChecker` ever produces a
non-identity, non-unity-scale transform — deciding what FIT/FILL/STRETCH should each actually do is
a design question, not folded into this fix-list by default.
