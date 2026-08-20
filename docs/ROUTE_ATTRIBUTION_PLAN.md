> **Status note (added when this doc was committed to the repo)**: this is the architecture plan as
> **approved** before implementation began. It is the source of truth for the overall design, but it
> predates several correction passes made *during* implementation (Phase 1.1 and Phase 1.2 in
> particular). **Read `../NEXT_STEPS.md` first** — it records exactly what has been built so far,
> every correction applied on top of this document, and what is approved to happen next. Treat this
> file as "why the architecture looks like this," and NEXT_STEPS.md as "what's actually true in the
> code right now."

# Plan: Automatic Route Attribution & Route-Aware Analysis (Gym-Camera POC)

## Context

The app already has two independently strong, well-tested subsystems that have never been
connected: a color/hold-detection vision pipeline (`colordetection/`, 103 passing tests) and a
pose-estimation/climb-scoring pipeline (`analysis/`, `pose/`, 100 passing tests). Both were built
for a single climber reviewing their own phone-recorded video after the fact, with a manually
picked (or absent) route tag that carries no real trust distinction from an official result.

The goal is a gym-camera POC: a member taps their wristband against an NFC reader assigned to a
physical wall, a fixed wall camera records the attempt, and the system automatically works out
which of the 3-4 active routes on that wall was climbed and whether it was a send/fall/unknown,
then delivers the video to the right member — all without the member's own phone ever being the
authority for that result.

This plan has been through three revision rounds: (1) an initial two-track design from the Phase-0
audit, (2) a correction pass fixing coordinate reconciliation, contact-state structure, a shared
pose artifact, a hard start-evidence gate, signal-availability contracts, versioning semantics,
trust-boundary hardening, manual-vs-club sharing, detector-change policy, and validation honesty,
and (3) **this pass**, which fixes the execution model (an explicit Edge Capture Agent, not the
member Android client, is the authority), adds a dedicated capture-agent phase and an immediate
trust-boundary bug-fix phase, replaces several ad-hoc enums/naming with a cleaner unified model,
and adds storage/retention and route-registration-UI-scale corrections. **Still not approved for
implementation** — presented for review again.

**Decisions locked in across rounds**: real gym footage is not yet available today, but this
round's Phase 2.5 gets a thin real-capture slice working early so real-footage collection can
start well before the full pipeline exists — see "Phase 2.5" below. Cloud Functions/Firestore-rules
code gets written now; actual Blaze billing/deploy remains a separate manual step outside this
session.

---

## Audit findings (Phase 0 — complete, all 10 claims confirmed, unchanged across rounds)

**Subsystem 1 (color/hold detection)**: pipeline confirmed end-to-end
(`extractReferenceFrame`→`TargetColorModel`→`RouteColorDetector.detect()`→masks→GL overlay); default
playback is the naive always-on `ColorIsolationEffect`, real detection is opt-in only (documented
reversal: "too strict for real footage"); single reference frame, fixed-camera assumption confirmed
in 3 places; every function takes exactly ONE `TargetColorModel`, zero competitive classification
exists; only persisted calibration is one JSON string per climb/video, not per route/wall.
`STRICT_DELTA_E_THRESHOLD=20.0` confirmed load-bearing with real measured evidence that no single
global threshold can serve both "detect this photo's holds" and "keep routes apart." Tests: 103/103
passing, but **every fixture is synthetic** — zero real gym-photo data anywhere.

**Subsystem 2 (pose analysis)**: pipeline confirmed end-to-end (MediaPipe→smoothing→metrics→
events→phases→scoring→coaching→persistence); strategy scoring hard-capped at 0.45 confidence,
documented as due to missing route/hold context; single-pose-by-construction (`.firstOrNull()`, no
`.setNumPoses()`), already a documented known gap; 10fps default/8-15fps range confirmed. Tests:
100/100 passing, but **zero coverage** for `CoachingRuleEngine`/`AnalysisRepository`/
`PoseAnalysisWorker`. Route-context columns already exist on `ClimbAttemptEntity`, unused by
scoring. 2-axis versioning already exists (`CURRENT_ALGORITHM_VERSION=4`, `ScoringConfig.version=2`).
Hold-contact detection confirmed absent; the colordetection engine has **zero existing references**
from analysis/pose code.

**Subsystem 3 (club/trust boundary)**: Claim 9 confirmed — manual route pick triggers
`recordClubAttempt`+`recordRouteAttempt`+`recordRouteCompletion`, identical to a verified capture,
zero distinction (`ClimbDetailsInputScreen.kt:189-241`). Claim 10 confirmed —
`ClubAttemptVideoScreen.kt:118`'s share gate is just `organizationId != null && routeId != null`,
uploads any local file unconditionally. Route/RouteVersion identity auto-generated, never derived
from color/name. Staff route-creation 100% metadata-only today. `routeStats` Firestore rule has
**no ownership check at all**. `functions/src/index.ts` already exists (2 undeployed
Firestore-triggered functions, Admin SDK wired) — not greenfield infra. Room migrations 10/10
additive/nullable-only.

---

## 1. Authoritative execution environment (new — corrects an unstated assumption)

**The normal member Android client is never the authority for verified results.** It is a
consumer only: it downloads/caches/plays delivered video and displays already-computed, read-only
results. It never runs pose extraction, hold-contact detection, attribution, or verification for a
club-camera attempt, and it never writes any official/verified Firestore field.

All gym-camera processing runs on the **Edge Capture Agent** — a separate, purpose-built
Android app/build (not the Play-Store member app) installed on the physical device that owns the
wall's camera and NFC reader for this POC. It reuses the SAME Kotlin modules already built and
tested for the personal pipeline (`pose/`, `colordetection/`, `analysis/`) as a shared library
module, rather than re-implementing anything server-side (there is no server-side ML/CV runtime in
this project and introducing one is out of scope for a POC).

| Stage | Runs on | Authority |
|---|---|---|
| NFC reader listener | Edge Capture Agent | trusted device identity |
| Camera recording | Edge Capture Agent | trusted device identity |
| Upload to Storage | Edge Capture Agent | trusted device identity |
| Pose extraction (`ClubCameraPoseExtractionWorker`) | Edge Capture Agent (on-device MediaPipe, reused code) | trusted device identity |
| Hold-contact detection | Edge Capture Agent | trusted device identity |
| Route attribution | Edge Capture Agent | trusted device identity, writes `attemptAttributions` |
| Result verification | Edge Capture Agent | trusted device identity |
| Route-aware analysis | Edge Capture Agent | trusted device identity |
| Official-aggregate finalization | Cloud Function (server, Admin SDK) | server-authoritative, idempotent |
| Member Android client | — | consumer only, never writes official data |
| Staff Android app | — | annotates/confirms registration data the Edge Agent captured; never substitutes its own camera as the wall reference (see §3) |

---

## 2. Edge Capture Agent — new Phase 2.5, thin vertical slice

A dedicated phase (before the attribution/analysis algorithm phases), covering only the physical
capture pipeline — no vision/pose intelligence yet — so real-hardware assumptions get tested early
rather than only at the very end (see §14/Phase 8-real below):

- **NFC event**: `NfcReaderDevice` (ESP32+PN5180/PN532) detects a wristband tap, sends a **signed
  reader event** to the `onWallTapEvent` Cloud Function, which verifies it and creates the
  `WallCaptureSession` server-side (Admin SDK) — resolving the tap to a `WristbandCredential` and
  the reader's own wall.
- **Session pickup**: the `CameraEdgeDevice` assigned to that wall, already subscribed to
  `wallCaptureSessions` for its `wallId`, sees the new session and begins recording.
- **Fixed-duration POC recording**: a **configurable 60-second default** window (with a reasonable
  configurable max, e.g. 90-120s), not dynamic stop-detection — deliberately simple for the POC.
- **Upload**: video → `wall_capture_videos/{organizationId}/{wallId}/{cameraDeviceId}/{sessionId}/
  source.mp4`, with a **retry/local queue** (gym wifi is assumed unreliable — the agent queues
  locally on failure and retries rather than losing the capture).
- **Heartbeat**: both devices periodically report liveness/status (see the `captureDevices`
  registry doc, §Trust boundary below)
  so staff/ops can tell if a camera/reader has gone offline.
- **Video metadata**: duration, resolution, timestamps, wall/camera/reader IDs — written to a new
  `ClubVideoAsset` doc (see §7), decoupled from any one attempt so it can be referenced without
  re-deriving metadata later.
- **Member notification**: once a session reaches a deliverable state, a `memberCaptureInbox` item
  is written for the attributed member (see §7), triggering the existing FCM plumbing
  (`ClimbMessagingService`/`functions/src/index.ts` already have this wired for other notification
  types — reused, not rebuilt).
- **Android download/cache/playback**: the *member* app (not the agent) subscribes to its own
  `memberCaptureInbox`, downloads the referenced `ClubVideoAsset`, and caches/plays it — purely a
  consumer, per §1.

**This phase deliberately ships before Phase 3's algorithm work is complete** — its own explicit
purpose is to start collecting real fixed-camera footage against real hardware as early as
possible (see the corrected gate ordering below), not to wait until attribution/analysis is fully
built before ever touching a real camera.

---

## 3. Wall reference frame provenance (corrects an implicit gap)

The authoritative "clean wall" reference image **must be captured by the exact installed fixed
camera** (i.e., by the Edge Capture Agent itself, on command), never substituted with a staff
member's own phone photo — the whole geometric-reconciliation design (masks, contact detection)
depends on the reference frame sharing the exact same optics/mounting/FOV as every future capture.
The staff Android app's role in Phase 2 is to **annotate** (wall ROI, floor line, start/finish hold
taps) an image the Edge Agent produced — it may only itself be the capture source in the special
case where the installed wall camera *is* that same Android device (a valid POC simplification,
but not the general case). The route-registration flow (§13) triggers a "capture reference now"
command relayed to the Edge Agent rather than opening the staff phone's own camera for this step.

---

## 4. Phase 0.5 — Immediate trust-boundary bug fix (moved earlier, per correction)

Rather than waiting for the full Phase 7 trust-boundary overhaul, the **already-confirmed** bug
(Claims 9/10 — manual/imported videos currently trigger the same official writes a verified capture
would need) gets fixed immediately, as a small, near-standalone, low-risk patch.

**Scope corrected — unconditional removal, not a future-branch gate**: do **not** introduce an
`AttemptSource`/`WALL_CAMERA` conditional into these screens for this step (that enum belongs to
Phase 1's real schema work, once Wall/Session entities actually exist). For Phase 0.5 specifically:

- `ClimbDetailsInputScreen.kt:223-233` — **remove** the `recordClubAttempt`/`recordRouteAttempt`/
  `recordRouteCompletion` calls **unconditionally**, full stop. No dead-code branch referencing a
  source value nothing can produce yet.
- `ClubAttemptVideoScreen.kt`/`ClubRepository.shareAttemptVideo` — **remove** the club local-file
  upload path from this normal-member code entirely (not gated, not deferred to Phase 7).
- **Explicitly preserved**: personal route tag, personal Send/Fall marking, personal analysis, and
  personal/non-club sharing — none of this is touched. Only the official club-facing write/upload
  path is removed.

This is a client-side stopgap; Phase 7 later adds server-side (Firestore rules) enforcement as
defense-in-depth so the fix holds even against a modified client.

---

## Consolidated entity list

**New Firestore entities** (`clubs/` package, all Firestore-only). **ID scheme corrected**: staff-
driven, low-frequency entities (`WallEntity`, `WallCalibrationEntity`, `RouteVisionProfileEntity`,
`NfcReaderDevice`, `CameraEdgeDevice`) keep the existing `nextId()` transaction-counter convention.
**High-frequency, potentially-offline-created capture entities do NOT use `nextId()`** (a shared
counter transaction is the wrong shape for something a device might need to create without
network connectivity, or under real concurrent load) — `WallCaptureSession`, `ClubVideoAsset`,
`PoseArtifactEntity`, `RouteAttributionResultEntity`, and `memberCaptureInbox` items all use a
client-generatable **UUID/ULID** string id instead:

| Entity | Purpose |
|---|---|
| `WallEntity` | physical wall; `readerDeviceId: Long?` (renamed from `nfcTagId` — the reader belongs to the wall, the tag/credential belongs to the member, see below) |
| `WallCalibrationEntity` | one versioned wall reference frame + `WallReferenceSpace` fields |
| `RouteVisionProfileEntity` | per-RouteVersion color model + normalized hold geometry, **stable persistent hold IDs assigned only at staff confirmation** (§11), optional corridor |
| `RouteAttributionResultEntity` | attribution outcome — compact summary inline, heavy trace data in Storage (§15) |
| `NfcReaderDevice` | the physical reader assigned to one wall; heartbeat/status |
| `WristbandCredential` | a member's tappable NFC credential (tag UID → userId) |
| `WallCaptureSession` | one NFC-tap-triggered capture session, full lifecycle status |
| `ClubVideoAsset` | decoupled video metadata (storage path, duration, resolution, size) |
| `PoseArtifactEntity` | one versioned pose-extraction result per session; frames live in Storage, not inline (§15) |
| `officialRouteVersionStats` | **new**, route-version-keyed, Cloud-Function-only verified aggregate (§9) |
| `officialClubMemberStats` | **new**, verified-only member aggregate, separate from legacy `clubStats` |
| `officialRouteCompletions` | **new**, verified-only, keyed by RouteVersion not Route |
| capture inbox | `memberCaptureInbox/{userId}/items/{sessionId}` — delivery/notification contract |

**Enums** (replacing the earlier ad-hoc/inconsistent set):
```kotlin
enum class AttemptSource { PHONE_CAMERA, IMPORTED_VIDEO, MANUAL_LOG, WALL_CAMERA }

/** ONE unified status model — replaces the earlier split between AttributionVerificationStatus
 * and a separately-implied VERIFIED/REJECTED set. Detailed reasons are a SEPARATE field. */
enum class AttributionStatus { PENDING, VERIFIED, REVIEW_REQUIRED, UNRESOLVED, REJECTED, CALIBRATION_INVALID }
enum class AttributionReasonCode { START_NOT_OBSERVED, START_MISMATCH, MARGIN_TOO_SMALL, TRACKING_UNRELIABLE, CAMERA_MISALIGNED, NO_CANDIDATES, STAFF_OVERRIDE }
enum class ResultAuthority { USER_REPORTED, AUTOMATICALLY_DETECTED, STAFF_CONFIRMED }
enum class StartEvidenceStatus { START_NOT_OBSERVED, START_OBSERVED_MATCH, START_OBSERVED_MISMATCH }
```
`attemptSource`, `attributionStatus`, and `resultAuthority` are always three independent fields —
never folded into one enum.

**Extended existing entity** (§8, further hardened this round): `RouteVersionEntity` becomes a
genuinely complete, self-describing immutable snapshot — not just a pointer to context, but a
denormalized copy of it, so a historical RouteVersion stays fully interpretable even if the org's
venue/zone hierarchy is later restructured or renamed. New nullable fields: `organizationId`,
`venueId`, `zoneId`, `wallId`, `grade`, `gradeSystem`, `colorHex` (already existed, kept),
`setterUserId` (already existed, kept), `setAt`, `retiredAt`, `wallCalibrationId`,
`visionProfileId`, `publicNumberOrName`, and typed policies (replacing the earlier untyped
`startHoldCountRequired: Int?`/`finishRequiresBothHands: Boolean?`):
```kotlin
enum class StartPolicy { SINGLE_HOLD_ANY_HAND, TWO_HOLDS_ONE_PER_HAND, TWO_HANDS_SAME_HOLD }
enum class FinishPolicy { ONE_HAND_ON_FINISH, TWO_HANDS_ON_FINISH, TOP_OUT_ZONE }
```
`startPolicy: StartPolicy?` / `finishPolicy: FinishPolicy?` — all additive/nullable, existing
metadata-only routes unaffected.

**Room** (only change): 4 new nullable columns on `ClimbAttemptEntity` — `attemptSource` (replaces
the earlier binary `captureProvenance`), `wallId`, `wallCalibrationId`, `captureSessionId` — one
additive `MIGRATION_11_12`.

---

## 5-15. Remaining corrections, integrated

**§5/§6 (enums)** — see consolidated entity list above; done.

**§7 (missing domain contracts)** — `NfcReaderDevice`, `WristbandCredential`, `WallCaptureSession`,
`ClubVideoAsset`, and the `memberCaptureInbox` delivery contract are all listed above, added in
Phase 1. `WallEntity`'s field is renamed `nfcTagId` → `readerDeviceId` (FK to `NfcReaderDevice`) —
the earlier design conflated "the reader assigned to a wall" with "an NFC tag," when actually the
wall has a reader device and the *member* has the tappable credential (`WristbandCredential`).

**§8 (RouteVersion as complete snapshot)** — see "Extended existing entity" above.

**§9 (separate official-aggregate collections)** — new `officialRouteVersionStats`/
`officialClubMemberStats`/`officialRouteCompletions` collections, written only by the Phase 7
Cloud Function, keyed by RouteVersion. The existing legacy `routeStats`/`clubStats`/
`routeCompletions` collections are **not** extended with new verified fields (the earlier design's
approach) — they remain exactly as they are today (informal, client-reported), and effectively stop
receiving new writes once Phase 0.5 ships, since nothing legitimate calls into them anymore. This
avoids ever mixing verified and unverified data on the same document.

**§10 (conservative CameraAlignmentChecker for v1)** — the `WallReferenceSpace`/
`CaptureToReferenceTransform` **abstraction is kept** (so the design doesn't need to change shape
later), but the **v1 implementation is deliberately simple**: fixed resolution, fixed orientation,
fixed crop are assumed by convention (the Edge Capture Agent always records at the same settings
used for calibration); `ValidIdentity` is the expected normal case; any meaningful luma-fingerprint
mismatch produces `CalibrationInvalid` directly — **no geometric registration/correction is
attempted in v1**. `ValidWithTransform` remains a defined case in the sealed type but is
unimplemented/unused until real footage actually proves simple identity-matching insufficient.
This removes the translation/rotation-search algorithm from Phase 3's real scope entirely for now.

**§11 (stable persistent hold IDs)** — hold IDs are assigned once, at the moment staff **confirms**
a `RouteVisionProfileEntity` (a simple sequential index over the final, staff-corrected hold list,
never reordered or regenerated afterward) — not derived fresh from `HoldComponentDetector`'s
per-run, potentially non-deterministic component-labeling order. Every later contact event,
debug overlay, and review UI references this stable ID.

**§12 (wall-profile conflict reconciliation) — hardened this round, no force-override**: before
persisting a new `RouteVisionProfileEntity`, check its calibrated color model's distance against
every other **active** profile on the same wall. For the POC, **enforce one active dominant route
color per wall** — registering a same/too-close color while another active route already claims it
is **blocked outright**. There is **no unconditional force-confirm escape hatch** (the earlier
design's "staff can force past the warning" option is removed, since it could silently
reintroduce the exact ambiguous-color problem the whole system exists to prevent). The only ways
past a conflict are: (a) staff performs explicit **manual hold partitioning** — assigning which
specific holds belong to which route among the disputed set — and the resulting per-route hold
sets pass conflict validation on their own merits, or (b) registration is blocked until the
conflict is resolved (e.g. retiring/recoloring the competing route first).

**New — calibration-invalidation cascade** (not in earlier rounds): when staff creates a **new**
`WallCalibrationEntity` version for a wall (e.g. after a camera bump), every existing
`RouteVisionProfileEntity` still referencing the **old** `wallCalibrationId` must be marked
invalid/needs-reconfirmation — never silently left "active" while pointing at stale geometry.
Staff must explicitly re-confirm (or re-register) each affected route's vision profile against the
new calibration before it can be used for attribution again.

**§13 (dedicated route-registration UI)** — route registration moves into its own new package
`ui/clubs/routeregistration/` (`RouteRegistrationScreen.kt` + `RouteRegistrationViewModel.kt`,
its own small wizard nav graph: wall-pick → calibration-request → color/grade → start/finish tap →
review/correct → confirm) rather than continuing to expand
`LiveSendClubExploreHost.kt` (already 637 lines per the audit). `LiveSendClubExploreHost.kt` gains
one new navigation entry point into this self-contained flow, instead of hosting every step
inline.

**§14 (early real-footage gate)** — addressed structurally by inserting Phase 2.5 (§2) right after
route registration and before the attribution/analysis algorithm phases — real capture-hardware
validation starts as soon as a thin capture slice exists, not only at the very end. See the
"Phase-by-phase plan" and "Phase gates" sections below for exactly where this sits.

**§15 (storage/retention for large artifacts)** — Firestore documents never hold unbounded
per-frame data:
- `PoseArtifactEntity` (Firestore): small — id, captureSessionId, version, modelVersion,
  frameCount, `storagePath` pointing to the actual per-frame JSON blob in Cloud Storage,
  `retentionExpiresAt`.
- `RouteAttributionResultEntity` (Firestore): small — compact summary fields inline (winning route,
  `AttributionStatus`, `AttributionReasonCode`, topline scores, margin), with the full
  `contactTimelineJson`/per-sub-score debug trace moved to a Storage blob referenced by
  `debugArtifactStoragePath: String?`.
- **Retention policy**: raw pose/contact/debug blobs retained for a bounded window (proposed 90
  days) for staff review/debugging, then purged by a scheduled Cloud Function
  (`functions/src/index.ts` gains a new `pubsub.schedule` trigger, implemented alongside the rest of
  Phase 7's Cloud Function work) — the compact summary + final decision on the Firestore doc
  persists indefinitely regardless of blob purge. For this POC pass, the cleanup function is
  written as part of Phase 7; actually enabling its schedule is subject to the same "code now,
  deploy later" decision as the rest of Cloud Functions.

**New — delivery split (VIDEO_READY vs. ANALYSIS_READY)**: the earlier single "member notification"
step is split into two independent lifecycle events, so an analysis-side failure never hides a
successfully recorded video: **VIDEO_READY** fires immediately once upload succeeds (member gets
their video right away, regardless of what happens next); **ANALYSIS_READY** fires later, once
attribution/verification/analysis finishes (successfully or not) — the `memberCaptureInbox` item
gets updated in place, not replaced, so the member always has *something* to watch even if
attribution never resolves.

**New — non-VERIFIED still gets personal analysis**: when route attribution is anything other than
`VERIFIED` (`REVIEW_REQUIRED`/`UNRESOLVED`/`REJECTED`/`CALIBRATION_INVALID`), the captured video
still gets a **pose-only personal analysis** run against it (reusing the shared `PoseArtifact`, no
route context) — exactly the value a personal video would get, just with zero official route/
result/stats effects. Attribution failure means "no official record," never "no feedback at all."

**New — hold-contact distance is distance-to-mask, not distance-to-contour** (corrects §7's
Phase-3 design): a limb whose proxy point falls **inside** the hold's mask is at distance **zero**
(full contact) — only a point **outside** the mask measures its distance to the nearest point on
the contour. The earlier "nearest point on contour" rule alone would have reported a nonzero
distance even for a limb pressed solidly into the middle of a hold, which is wrong.

**New — `WallCaptureSession` state split into two independent axes** (§4/§10 — replaces the earlier
single merged state machine): capture (physical recording/upload) and analysis (downstream
processing) are genuinely separate concerns and must not share one enum.

```kotlin
enum class CaptureStatus { ARMED, RECORDING, UPLOAD_PENDING, UPLOADING, VIDEO_READY, FAILED, CANCELLED, EXPIRED }
enum class CaptureAnalysisStatus { NOT_STARTED, QUEUED, PROCESSING, READY, FAILED, REVIEW_REQUIRED }
```
```
CaptureStatus:  ARMED → RECORDING → UPLOAD_PENDING → UPLOADING ⇄ UPLOAD_FAILED(retry)
                                                            │
                                                            ▼
                                                      VIDEO_READY ──VIDEO_READY notification──
Any stage → FAILED | CANCELLED | EXPIRED (terminal)

CaptureAnalysisStatus (independent, starts once CaptureStatus reaches VIDEO_READY):
  NOT_STARTED → QUEUED → PROCESSING → READY ──ANALYSIS_READY notification──
                                     → FAILED | REVIEW_REQUIRED
```
**Expiry/lease handling** (new): a session stuck in `ARMED` (camera device never picked it up) or
`RECORDING` (recording started but never finished/uploaded) past a configured lease timeout
transitions to `EXPIRED` rather than leaving the wall permanently "busy" — checked opportunistically
by the busy-response logic and/or a scheduled sweep, same mechanism as the debug-blob retention
cleanup (§15). **Idempotency rules**: only one non-`EXPIRED`/non-terminal session per wall camera
at a time — a new tap while a session is active gets a busy response, never a second concurrent
session; on device restart, any session not yet terminal is resumed/retried by its own persisted
state, never duplicated from a replayed tap event.

**Result verification is now an explicit, separate second gate** (§5 — previously implied only via
a single `resultAuthority` field):
```kotlin
enum class AttemptResult { SEND, FALL, ABANDONED, UNKNOWN }
enum class ResultVerificationStatus { PENDING, VERIFIED, REVIEW_REQUIRED, REJECTED }
```
**Official Send/Fall/completion counters require BOTH**: route `AttributionStatus == VERIFIED`
**AND** `ResultVerificationStatus == VERIFIED` — two independent gates, not one. `REJECTED` at
either level must never increment any official aggregate; the idempotent Cloud Function (§7) checks
both fields before incrementing anything.

**Device identity — claims for static scope, a registry doc for live state**: a `CameraEdgeDevice`'s
Firebase Auth custom claims (`{ trustedCaptureDevice: true, organizationId, cameraId, wallId }`)
establish its *static* scope, which rarely changes and is cheap to check from a rules-evaluated
token. But `enabled`/`revoked` status and heartbeat timestamps change frequently and need
instant effect (a compromised device must be revocable without waiting for a token-refresh cycle)
— those live in a separate `captureDevices/{deviceUid}` Firestore document instead
(`enabled: Boolean`, `revokedAt: Long?`, `lastHeartbeatAt: Long`), checked via a rules `get()`
alongside the token claims: `request.auth.token.trustedCaptureDevice == true && ... &&
get(/databases/$(database)/documents/captureDevices/$(request.auth.uid)).data.enabled == true`.
The `NfcReaderDevice`'s ESP32 has no such identity at all — it never authenticates to Firestore
directly (see the hardware decision above); only the Cloud Function it calls is trusted.

**Edge module structure resolved** (was an open question — now settled): the Camera Edge Device is
a genuinely separate Android app module with its own unique `applicationId`, not a build flavor or
UI toggle on the member app. Only the reusable domain/vision/pose/analysis Kotlin (the
`colordetection`/`pose`/`analysis` packages already used by the personal pipeline) is extracted
into a shared library module both apps depend on — UI, navigation, and member-facing concerns stay
in the member app only.

---

## Runtime diagram

```
┌────────────────────────┐        ┌──────────────────────────────────────────────┐
│   Member's own phone   │        │              Edge Capture Agent                │
│  (existing member app) │        │      (separate app/build, gym-owned device)    │
│                        │        │                                                │
│  • personal recording  │        │  NFC listener → WallCaptureSession(ARMED)      │
│    /import (unchanged) │        │  → fixed-duration recording                    │
│  • personal pose/       │        │  → upload (retry/local queue) → ClubVideoAsset │
│    color analysis       │        │  → ClubCameraPoseExtractionWorker (15fps,      │
│    (unchanged, 10fps)   │        │    ONCE) → PoseArtifact (Storage-backed)       │
│                        │        │  → CameraAlignmentChecker (v1: identity or     │
│  • CONSUMER ONLY of     │◄───────│    CalibrationInvalid)                         │
│    club-camera results: │ FCM +  │  → HoldContactDetector (per-limb state model)  │
│    downloads/caches/    │ inbox  │  → RouteAttributionEngine (hard start-gate +   │
│    plays delivered      │  read  │    SubScoreResult signals) → attemptAttributions│
│    video, displays      │        │  → ResultVerificationWorker → SEND/FALL/…      │
│    read-only result     │        │  → Route-aware analysis branch                 │
└────────────────────────┘        └──────────────────────────────────────────────┘
                                                     │  (trusted device identity,
                                                     │   per-device scoped claims)
                                                     ▼
                                    ┌──────────────────────────────────┐
                                    │       Firestore + Storage         │
                                    │  wallCaptureSessions,              │
                                    │  attemptAttributions,              │
                                    │  routeVisionProfiles, ...           │
                                    └──────────────────────────────────┘
                                                     │  write triggers
                                                     ▼
                                    ┌──────────────────────────────────┐
                                    │   Cloud Function (server, Admin   │
                                    │   SDK) — idempotent finalization  │
                                    │   → officialRouteVersionStats /   │
                                    │     officialClubMemberStats /     │
                                    │     officialRouteCompletions      │
                                    │   → memberCaptureInbox write      │
                                    │     → FCM notification            │
                                    └──────────────────────────────────┘

Staff Android app: annotates the Camera Edge Device's own captured wall reference (never
substitutes its own camera image, §3); drives the new dedicated route-registration flow (§13); on
a wall color conflict, can only perform explicit manual hold partitioning that then passes
validation, or leave registration blocked — there is no casual force-override (§12).
```

## Data-flow diagram (club-camera path)

```
WristbandCredential tap → signed reader event → onWallTapEvent Cloud Function verifies + creates
  WallCaptureSession (CaptureStatus=ARMED)
  → CameraEdgeDevice (subscribed to this wall) picks it up: CaptureStatus → RECORDING → UPLOADING
  → ClubVideoAsset written, CaptureStatus = VIDEO_READY ──fires VIDEO_READY notification──
  → CaptureAnalysisStatus: NOT_STARTED → QUEUED → PROCESSING
  → PoseArtifact (Storage-backed frames, Firestore summary doc) — pose-only personal analysis
    ALSO runs here unconditionally (falls back to this if attribution below doesn't VERIFY)
  → CameraAlignmentChecker → AlignmentCheckResult
       ── CalibrationInvalid ──────────────────────────► attemptAttributions
                                                          (status=CALIBRATION_INVALID)
       ── ValidIdentity ──► HoldContactDetector (per-limb LimbContactState, distance-to-mask
                              [zero if inside, else nearest contour point], transition-windowed,
                              gap-tiered)
                              → RouteAttributionEngine
                                   StartHoldMatcher [HARD GATE] + ContactCoverageScorer
                                   + CorridorScorer + FinishEvidenceScorer
                                   + foreign-contact penalty (unique events)
                                   → AttributionStatus + AttributionReasonCode
                                     + per-candidate SubScoreResult (debug blob in Storage)
                              → attemptAttributions (PENDING → VERIFIED/REVIEW_REQUIRED/
                                UNRESOLVED/REJECTED)
  → [attribution not VERIFIED] CaptureAnalysisStatus=READY anyway — member still gets the
       pose-only personal analysis computed above, just with zero official effects
  → [only if attribution VERIFIED] ResultVerificationWorker (same PoseArtifact)
       → AttemptResult (SEND|FALL|ABANDONED|UNKNOWN) + ResultVerificationStatus
         (PENDING→VERIFIED/REVIEW_REQUIRED/REJECTED) + resultConfidence + resultAuthority
  → [only if BOTH AttributionStatus==VERIFIED AND ResultVerificationStatus==VERIFIED]
       route-aware analysis branch (same PoseArtifact + result) → routeContext-aware
       metrics/events/phases/scores/tips, tagged ROUTE_AWARE_PROFILE_VERSION (personal
       algorithm/scoring versions untouched)
  → CaptureAnalysisStatus = READY ──fires ANALYSIS_READY notification──
  → attemptAttributions final write (both statuses at their terminal value)
       → Cloud Function (idempotent, transactional) — increments official aggregates ONLY when
         both gates are VERIFIED, never on REJECTED at either level → officialRouteVersionStats /
         officialClubMemberStats / officialRouteCompletions incremented exactly once
       → memberCaptureInbox item updated in place → FCM → member notified
  → member later: shareVerifiedClubAttempt(sessionId) [server call, no upload] → sharedAttempts
```

Personal path (phone/imported/manual): entirely separate, entirely unchanged —
`PoseAnalysisWorker` → `MediaPipePoseEstimator` @10fps → existing scoring, `routeContext` always
null, `attemptSource` ∈ {PHONE_CAMERA, IMPORTED_VIDEO, MANUAL_LOG}, never reaches
`attemptAttributions` or any official collection (enforced from Phase 0.5 onward).

---

## Final amended phase order (revised — resolves the registration/hardware dependency cycle)

| Phase | Name | Depends on |
|---|---|---|
| **0.5** | Immediate trust-boundary bug fix | nothing — ships first, independent |
| **1** | Models, configuration, domain contracts, audit protection | 0.5 |
| **1.25** | Hardware spike (NEW — see below) — proves physical assumptions before any registration code is written | 1 |
| **1.5** | Camera Edge Device bootstrap & reference capture (NEW — resolves the cycle below) | 1.25 |
| **2** | Minimal route registration (own UI package, wall-camera-sourced reference via 1.5, conflict reconciliation — no force-override) | 1.5 |
| **2.5** | Full capture pipeline: NfcReaderDevice signed events + CameraEdgeDevice recording/upload/heartbeat/notify | 1.5, 2 |
| **3** | Hold contact detector (conservative alignment checker, distance-to-mask) | 1, 2.5 |
| **4** | Automatic route resolver (hard start-gate, uniform sub-score contract) | 3 |
| **5** | Route-aware result verification | 4 |
| **6** | Route-aware analysis (separate versioning axis; falls back to pose-only personal analysis when not VERIFIED) | 5 |
| **7** | Trust boundary — server enforcement, per-device claims, Cloud Function finalization, storage retention | 0.5, 1-6 |
| **8** | Synthetic-fixture dry run (explicitly NOT "validated POC") | 1-7 |
| **8-real** | Real fixed-camera validation (the actual POC-validation gate) | 8, real footage collected via 1.5/2.5 |

**§1 correction — the dependency cycle**: Phase 2 (route registration) needs a wall reference
frame captured by the actual Edge Capture Agent (§3 above), but the earlier phase order put the
full Edge Capture Agent (2.5) *after* route registration (2) — a real circular dependency. Fixed
by splitting the Edge Agent's build into two phases: **1.5** is a minimal bootstrap — device
provisioning/pairing (trusted identity assignment) plus just enough capability to capture and
upload a single still reference frame on command from the staff app — enough for Phase 2 to have
something real to register routes against. **2.5** then extends that same bootstrapped app with
the full NFC/recording/session/heartbeat/notification pipeline, after routes already exist to
attribute against.

**§2 correction — adapter contracts and concrete POC hardware**, defined in Phase 1.5, before any
Edge Agent code is written:
```kotlin
interface NfcReaderAdapter {
    fun startListening(onTap: (tagUid: String) -> Unit)
    fun stopListening()
    fun heartbeatStatus(): ReaderHeartbeat
}
interface CameraSourceAdapter {
    suspend fun captureStillReferenceFrame(): CapturedFrame
    suspend fun startRecording(maxDurationMs: Long): RecordingHandle
    suspend fun stopRecording(handle: RecordingHandle): CapturedVideo
}
```
**Phase 1.25 — hardware spike (new)**: before any route-registration code is written, prove the
physical assumptions this whole plan depends on, against real hardware: NFC read range/reliability
through the actual wood wall thickness; LED/buzzer tap-feedback; the ESP32's signed-event mechanism
end-to-end; Camera Edge Device start latency from session-created to actually recording; the
busy-response behavior for a second tap during an active session; and network retry/recovery for
the upload queue. This is a validation spike, not production code — if any of these fail, the
architecture above needs revisiting before Phase 1.5 begins.

**Concrete POC hardware decision — REVISED, built-in-NFC-on-camera-device rejected**: the physical
UX requires an NFC reader mounted *behind the wooden wall* under a "Tap Here" sticker (member taps
the wall itself, not a phone/tablet) and a **separate** fixed camera positioned far enough back to
see the whole wall — these cannot be the same device. Two distinct device types, two distinct
trust mechanisms:

- **`NfcReaderDevice`** — a PN5180 or PN532 NFC reader chip driven by an ESP32 microcontroller,
  mounted behind the wall. Far too constrained for Firebase Auth/custom claims; instead it sends a
  **signed reader event** (HMAC over a pre-shared per-device secret, or a lightweight key pair) to
  a small HTTPS Cloud Function endpoint (`onWallTapEvent`). That Function verifies the signature
  server-side and — only if valid — creates the `WallCaptureSession` itself via the Admin SDK. The
  ESP32 **never writes to Firestore directly**. Includes LED/buzzer feedback so the member gets
  physical confirmation the tap registered (tested in the new Phase 1.25 spike, below).
- **`CameraEdgeDevice`** — a separate Android device running the Edge Camera Agent app (CameraX),
  with its own per-device Firebase Auth trusted identity (structured custom claims, per the earlier
  trust-boundary design). It **subscribes** to `wallCaptureSessions` filtered to its own assigned
  `wallId` (a live Firestore listener) and begins recording as soon as a new session appears for
  its wall — it does not itself listen for NFC at all.

`NfcReaderDevice` and `CameraEdgeDevice` are modeled as fully separate entities (not folded into one
"Edge Capture Agent" concept as earlier rounds implied) — different hardware, different trust
mechanisms, different failure modes.

## Phase gates (revised)

- **0.5**: existing personal-pipeline tests unaffected; a new test proves manual/phone/imported
  attempts no longer reach any official write path.
- **1→2, 2→2.5**: schema/config tests pass; conflict-reconciliation and stable-hold-id tests pass.
- **2.5**: real NFC tap → real recording → real upload → real member-side download/playback
  demonstrated on actual hardware — this is the "early real-footage collection gate," explicitly
  not deferred to the end.
- **2.5→3, 3→4**: synthetic-fixture unit/regression tests pass; alignment-checker v1 correctly
  produces `CalibrationInvalid` on deliberately mismatched synthetic fixtures and `ValidIdentity`
  on matching ones (no transform-estimation tests needed, since v1 doesn't implement one).
- **4→5**: fixture tests prove (a) strong contact-coverage with `START_NOT_OBSERVED`/MISMATCH never
  reaches VERIFIED, (b) close-margin cases produce REVIEW_REQUIRED, (c) foreign-penalty scales with
  unique events not frames.
- **5→6**: occlusion/ambiguous synthetic fixtures downgrade correctly; body-swap-jump fixtures reset
  contact state immediately.
- **6→7**: structural (not byte-for-byte) regression proves personal-attempt output AND version
  numbers are unchanged when `routeContext == null`.
- **7→8**: rules-emulator tests prove the trust boundary both directions; aggregate Cloud Function
  proven idempotent under simulated duplicate delivery; retention/cleanup function proven to leave
  the compact summary intact after purging the debug blob.
- **8→8-real**: gated on real footage existing (seeded by Phase 2.5, collected throughout 3-7's
  development, not started cold at this point). Target for the approved demo dataset: **zero false
  VERIFIED route assignments, zero false VERIFIED sends** — the only gate this claim can be made
  against.

---

## What must NOT change (any phase)

- The member Android client is never the authority for verified data (§1) — enforced structurally,
  not just by convention.
- Personal pose-only scoring/metrics/coaching output and version numbers for any `routeContext =
  null` call — unchanged, structurally regression-tested.
- Personal analysis, personal route tags, personal Send/Fall marking — fully preserved; only their
  reach into official club data is removed (Phase 0.5/7).
- `PoseAnalysisConfiguration`'s existing 10fps personal default; `ColorIsolationEffect`'s default
  status; `HoldComponentDetector`/`RouteColorDetector`/etc.'s default behavior (targeted, config-
  gated, regression-tested bug fixes remain the only allowed exception); `STRICT_DELTA_E_THRESHOLD`;
  `MediaPipePoseEstimator`'s single-pose construction; existing Room migrations; every untouched
  Firestore collection.
- No face recognition, no biometric identity. No Firebase service-account key ever reaches an edge
  device. No unbounded per-frame JSON in a Firestore document (§15).

## Verification approach

Unchanged mechanics from the prior round (run real test modules, read actual JUnit XML, dedicated
migration/regression/idempotency/rules-emulator tests per phase) — see phase gates above for what
each phase specifically must prove before the next begins.

## Remaining unresolved decisions

1. Whether per-device credential provisioning should eventually move to a Cloud Function-based
   enrollment flow rather than a manually-run admin script (fine for one wall/one POC device; a
   real question at multi-wall scale).
2. Exact fixed recording duration for Phase 2.5's POC capture window, and the exact retention
   window for debug blobs (90 days proposed, not yet confirmed).
3. Whether `shareVerifiedClubAttempt` needs an extra staff-approval step for the very first
   verified share at a newly-registered wall, or works automatically from day one.
4. Exact plausible-motion displacement bound for the contact-detector's implausible-jump reset —
   needs a concrete number grounded in real climbing movement speed once real footage exists.
5. ~~Resolved~~: Camera Edge Device is a separate app module with its own applicationId (see
   "Edge module structure resolved" above).

---

## Exact file list

**New files**:
- `clubs/RouteAttributionEntities.kt` — Wall/WallCalibration/RouteVisionProfile/
  RouteAttributionResult entities, all new enums
- `clubs/CaptureDomainEntities.kt` — NfcReaderDevice, WristbandCredential, WallCaptureSession,
  ClubVideoAsset
- `colordetection/FixedCameraRouteRegistrationConfig.kt`
- `colordetection/CompetitiveHoldClassifier.kt`
- `colordetection/HoldGeometryJson.kt`
- `colordetection/CameraAlignmentChecker.kt` (v1: identity/invalid only, transform abstraction
  present but unimplemented)
- `colordetection/WallReferenceSpace.kt` (WallReferenceSpace, CaptureToReferenceTransform,
  AlignmentCheckResult)
- `analysis/metrics/HoldContactConfig.kt`
- `analysis/metrics/HoldContactDetector.kt`
- `analysis/metrics/HoldContactTimelineJson.kt`
- `analysis/PoseArtifactEntity.kt` + `ClubCameraPoseExtractionWorker.kt`
- `attribution/RouteAttributionScoringConfig.kt`, `RouteCandidate.kt`, `StartHoldMatcher.kt`,
  `ContactCoverageScorer.kt`, `CorridorScorer.kt`, `FinishEvidenceScorer.kt`,
  `RouteAttributionEngine.kt`, `AttributionResult.kt`
- `analysis/RouteAttributionWorker.kt`, `ResultVerificationWorker.kt`
- `ui/clubs/routeregistration/RouteRegistrationScreen.kt` + `RouteRegistrationViewModel.kt` (new
  package, per §13)
- `ui/detail/HoldContactDebugScreen.kt`
- Edge Capture Agent app module (new Gradle module or build flavor — exact shape is unresolved
  decision #5 above)

**Modified files**:
- `clubs/ClubEntities.kt` — extend `RouteVersionEntity` per §8
- `clubs/ClubRepository.kt` — new collection constants/mappers; split `shareAttemptVideo`
- `analysis/ClimbAttemptEntity.kt` + `data/ClimbDatabase.kt` — new nullable columns,
  `MIGRATION_11_12`
- `analysis/PoseAnalysisWorker.kt` — no change to its own orchestration; personal path untouched
- `analysis/metrics/ClimbMetrics.kt`, `EventBuilder.kt`, `ClimbPhaseDetector.kt`,
  `analysis/scoring/PerformanceScorer.kt`, `coaching/CoachingRuleEngine.kt` — new optional trailing
  `routeContext` parameter each
- `ui/analysis/ClimbDetailsInputScreen.kt` — remove unconditional official writes (Phase 0.5)
- `ui/clubs/ClubAttemptVideoScreen.kt` — gate/remove local-upload branch (Phase 0.5/7)
- `ui/livesend/real/LiveSendClubExploreHost.kt` — one new nav entry point into the new
  registration package, instead of further inline expansion
- `firestore.rules`, `storage.rules` — new collections/paths, per-device claim scoping,
  `routeStats`/etc. allowlist hardening
- `functions/src/index.ts` — new aggregate-finalization trigger (idempotent/transactional) + new
  scheduled cleanup trigger

**Unchanged (explicitly verified as out of scope)**: `HoldComponentDetector.kt`,
`RouteColorDetector.kt`, `HoldBoundaryRefiner.kt`, `HoldColorValidator.kt`,
`HoldConfidenceEvaluator.kt`, `HoldMaskRenderer.kt`, `MediaPipePoseEstimator.kt`,
`RouteColorDetectionConfig.kt`, `ColorIsolationEffect.kt`, `DetailScreen.kt`'s personal
calibration flow.
