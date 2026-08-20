package com.example.climb.clubs

import java.util.UUID

/** The single source of truth for how every capture-pipeline entity id is generated — see
 * CaptureDomainEntities.kt / RouteAttributionEntities.kt's own doc comments on why these are
 * client-generatable String ids, never ClubRepository's nextId() counter. New session ids are
 * genuinely random (nothing to derive them from yet); every other id in one session's pipeline is
 * DETERMINISTIC from that session's id, specifically so a retried upload/re-run never mints a
 * second, orphaned id for what is really the same logical artifact — the retry naturally
 * "finds" the same id again instead of creating a duplicate. */
object CaptureEntityIds {

    /** [WallCaptureSession.id]: the one genuinely random id in this pipeline — a capture session
     * begins with no prior identifier to derive one from. */
    fun newSessionId(): String = UUID.randomUUID().toString()

    /** [ClubVideoAsset.id]: deterministic from [sessionId] alone, so a retried upload after a
     * partial failure resolves to the same video asset id instead of minting a new one. */
    fun videoAssetId(sessionId: String): String = "video:$sessionId"

    /** [PoseArtifactEntity.id]: deterministic from [sessionId] plus [poseArtifactVersion], so a
     * re-run at a new pose-extraction algorithm version gets a distinct id, while re-running the
     * same version for the same session resolves to the same id. */
    fun poseArtifactId(sessionId: String, poseArtifactVersion: Int): String =
        "pose:$sessionId:v$poseArtifactVersion"

    /** [RouteAttributionResultEntity.id]: deterministic from [sessionId] alone — one attribution
     * result per session. */
    fun attributionResultId(sessionId: String): String = "attribution:$sessionId"

    /** [MemberCaptureInboxItem.id]: deterministic from [sessionId] alone, matching that entity's
     * own doc comment that its id is conceptually "== the capture session id". */
    fun inboxItemId(sessionId: String): String = "inbox:$sessionId"
}
