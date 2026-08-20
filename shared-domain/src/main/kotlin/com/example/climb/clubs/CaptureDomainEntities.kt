package com.example.climb.clubs

/**
 * Shared domain models for the physical capture pipeline (NFC reader, camera edge device, capture
 * session, pose extraction, delivered video, member notification) — usable by both the member app
 * (`:app`) and the future Camera Edge Device module. Pure data shapes only — no NFC
 * implementation, no camera implementation, no real session lifecycle code, no Firestore
 * collections/rules wired up yet. See `RouteAttributionEntities` for the attribution/result shapes
 * these sessions eventually feed.
 *
 * ID scheme: these are high-frequency, potentially-offline-created entities (an edge device might
 * need to create a session id before it has network connectivity to reach a shared counter
 * transaction), so they use a client-generatable string id (UUID/ULID), NOT the
 * `ClubRepository`-style `nextId()` transaction counter used by the staff-driven, low-frequency
 * entities in `RouteAttributionEntities` (`WallEntity`, `WallCalibrationEntity`,
 * `RouteVisionProfileEntity`) and the two device registries below. Every entity in this file whose
 * id is generated per-attempt/per-session (`WallCaptureSession`, `ClubVideoAsset`,
 * `PoseArtifactEntity`, `MemberCaptureInboxItem`) uses `String` for exactly this reason;
 * `RouteAttributionResultEntity` (in `RouteAttributionEntities.kt`) does too, for the same reason.
 */

enum class ReaderDeviceStatus { ONLINE, OFFLINE, UNKNOWN }

/** The physical NFC reader (PN5180/PN532 + ESP32) assigned to one wall, mounted behind it. Far too
 * constrained for a Firebase Auth identity — it authenticates by signing its tap events with a
 * pre-shared secret, verified by a Cloud Function (Phase 2.5+), never by writing to Firestore
 * directly. This entity is the *registry* record staff create when installing the reader; it is
 * not itself a trust credential. */
data class NfcReaderDevice(
    val id: Long,
    val organizationId: Long,
    val wallId: Long,
    val deviceLabel: String,
    val installedAt: Long,
    val lastHeartbeatAt: Long? = null,
    val status: ReaderDeviceStatus = ReaderDeviceStatus.UNKNOWN,
)

/** The separate Android device (its own app module, own applicationId) running the camera that
 * watches one wall. Has its own per-device Firebase Auth trusted identity ([deviceUid]); the
 * *static* scope (org/wall/camera) lives in that identity's custom claims, while
 * [enabled]/[revokedAt]/[lastHeartbeatAt] live here so they can be checked/updated live without
 * waiting on a token-refresh cycle (Phase 7 concern; nothing reads/writes these fields yet). */
data class CameraEdgeDevice(
    val id: Long,
    val organizationId: Long,
    val wallId: Long,
    val deviceUid: String,
    val deviceLabel: String,
    val installedAt: Long,
    val lastHeartbeatAt: Long? = null,
    val enabled: Boolean = true,
    val revokedAt: Long? = null,
)

/** A member's tappable NFC credential (their wristband). The NFC reader resolves a tap's tag UID
 * to this, not the other way around — the wall owns the *reader*, the member owns the
 * *credential*.
 *
 * Security shape: [tagUidHash] = `HMAC-SHA256(rawTagUid, serverSecret)` (see
 * [WristbandCredentialHashing]) — never a bare/unsalted hash of the tag UID alone. [id] is an
 * ordinary auto-generated `nextId()` value, same as every other staff-issued record; [tagUidHash]
 * is computed once at issuance time and again by the reader/Cloud-Function-verification step at
 * tap time for lookup, keyed by a secret held only server-side.
 *
 * The raw tag UID itself is handled ONLY transiently — briefly, in-memory, by the ESP32 reader and
 * the verifying Cloud Function — and is NEVER persisted anywhere, NEVER logged (not even in debug
 * logs), NEVER used as a public document id, and NEVER stored on the member's user account record.
 *
 * Honest limitation: UID-only NFC wristbands remain physically cloneable by anyone who can read
 * the tag (NFC UID cloning is a known, well-documented physical attack) — this is POC-level
 * identity binding, not strong production authentication. A production deployment would need a
 * challenge-response NFC credential (e.g. NTAG 424 DNA / DESFire with per-tag keys) to close this
 * gap; that is out of scope for this POC. */
data class WristbandCredential(
    val id: Long,
    val organizationId: Long,
    val userId: String,
    val tagUidHash: String,
    val issuedAt: Long,
    val enabled: Boolean = true,
    val revokedAt: Long? = null,
)

/** Physical recording/upload lifecycle only — deliberately independent of
 * [CaptureAnalysisStatus] (downstream processing), since the two are genuinely separate concerns
 * that used to be modeled as one merged state machine. `EXPIRED` exists so a session stuck in
 * `ARMED`/`RECORDING` past a lease timeout doesn't leave its wall permanently "busy" — see
 * [WallCaptureSession.leaseExpiresAt]. */
enum class CaptureStatus { ARMED, RECORDING, UPLOAD_PENDING, UPLOADING, VIDEO_READY, FAILED, CANCELLED, EXPIRED }

/** Downstream processing lifecycle, starting only once [CaptureStatus] reaches `VIDEO_READY`. A
 * `VIDEO_READY` notification (member gets their video) and a later, independent `ANALYSIS_READY`
 * notification (member gets their result) are two separate delivery events — analysis failure
 * must never hide a successfully recorded video. Deliberately a field completely separate from
 * [CaptureStatus] on [WallCaptureSession] — the two axes are independent by construction: nothing
 * in either enum, or on the entity, derives one from the other. */
enum class CaptureAnalysisStatus { NOT_STARTED, QUEUED, PROCESSING, READY, FAILED, REVIEW_REQUIRED }

/** One NFC-tap-triggered capture session for one wall. Idempotency (Phase 2.5 concern, not
 * enforced by anything here): only one non-terminal session per wall camera at a time — a new tap
 * while a session is active gets a busy response; on device restart, a non-terminal session is
 * resumed/retried by its own persisted state, never duplicated from a replayed tap. Default
 * capture window is a configurable 60 seconds (with a reasonable configurable max, e.g. 90-120s)
 * for the initial hardware POC — [leaseExpiresAt] carries whatever value applied to this session. */
data class WallCaptureSession(
    val id: String,
    val organizationId: Long,
    val wallId: Long,
    val cameraDeviceId: Long,
    val readerDeviceId: Long,
    val wristbandCredentialId: Long,
    val attributedUserId: String,
    val captureStatus: CaptureStatus,
    val captureAnalysisStatus: CaptureAnalysisStatus = CaptureAnalysisStatus.NOT_STARTED,
    val videoAssetId: String? = null,
    val armedAt: Long,
    val recordingStartedAt: Long? = null,
    val recordingEndedAt: Long? = null,
    val leaseExpiresAt: Long,
    val failureReason: String? = null,
)

/** Decoupled video metadata — referenced by id rather than re-derived, from
 * [WallCaptureSession.videoAssetId], any future attribution/sharing code, etc. */
data class ClubVideoAsset(
    val id: String,
    val organizationId: Long,
    val wallId: Long,
    val cameraDeviceId: Long,
    val captureSessionId: String,
    /** `wall_capture_videos/{organizationId}/{wallId}/{cameraDeviceId}/{sessionId}/source.mp4` —
     * the exact scoping the Phase 7 trust boundary needs to match rules against. */
    val storagePath: String,
    val durationMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val uploadedAt: Long,
)

/** One versioned pose-extraction result for one club-camera capture session — produced exactly
 * once per session (`ClubCameraPoseExtractionWorker`, Phase 3+), then read identically by route
 * attribution, result verification, and route-aware analysis, rather than each of those
 * independently re-running pose estimation. Frames themselves live in Storage
 * ([framesStoragePath]), not inlined here as unbounded JSON — this record is just the small
 * summary + pointer. */
data class PoseArtifactEntity(
    val id: String,
    val organizationId: Long,
    val captureSessionId: String,
    val poseArtifactVersion: Int,
    val modelVersion: String,
    val frameCount: Int,
    val framesStoragePath: String,
    val createdAt: Long,
    val retentionExpiresAt: Long? = null,
)

/** The member-facing delivery/notification contract — one item per capture session, updated in
 * place (not replaced) as the two independent lifecycle events land: [videoReadyAt] fires the
 * "your climb was recorded" notification immediately after upload; [analysisReadyAt] fires the
 * "here's your result" notification later, once attribution/verification/analysis finishes
 * (successfully or not) — attribution failure means "no official record," never "no video/
 * feedback at all." */
data class MemberCaptureInboxItem(
    val id: String,
    val userId: String,
    val organizationId: Long,
    val wallId: Long,
    val videoAssetId: String,
    val attributionStatus: AttributionStatus? = null,
    val resultValue: AttemptResult? = null,
    val videoReadyAt: Long? = null,
    val analysisReadyAt: Long? = null,
    val readAt: Long? = null,
)
