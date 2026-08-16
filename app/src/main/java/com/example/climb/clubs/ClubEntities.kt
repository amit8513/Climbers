package com.example.climb.clubs

/**
 * The Clubs/Organizations domain — stored in Firestore (see [ClubRepository]), not the local Room
 * database, so a request/approval/route/update is visible across every phone, not just the one
 * that created it. A [ClimbAttemptEntity][com.example.climb.analysis.ClimbAttemptEntity] or
 * [ClimbEntity][com.example.climb.data.ClimbEntity] optionally references these by nullable Long
 * id columns on the *local* `climbs`/`climb_attempts` tables — a normal user with zero memberships
 * never has any row here and every one of those id columns stays null for them.
 */
data class OrganizationEntity(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

enum class OrganizationRole { MEMBER, STAFF, ADMIN }

/** A user's optional link to an organization — a normal user simply has zero rows here. There is
 * no separate "GymUser"/"StaffUser" account type; staff access is just this row's [role]. Stored
 * at Firestore doc id `"{organizationId}_{userId}"`, so there's at most one per (org, user) pair. */
data class OrganizationMembershipEntity(
    val organizationId: Long,
    val userId: String,
    /** Denormalized at write time (same pattern as [com.example.climb.data.social.FriendRequest]'s
     * fromUsername/toUsername) so displaying a member never depends on a separate, possibly-failed
     * profile lookup. */
    val userDisplayName: String,
    val role: OrganizationRole,
    val joinedAt: Long,
)

/** A physical gym location belonging to an organization. */
data class VenueEntity(
    val id: Long,
    val organizationId: Long,
    val name: String,
    val address: String? = null,
    val createdAt: Long,
)

/** An area within a venue (e.g. "Bouldering Cave", "Lead Wall"). [organizationId] is denormalized
 * from the parent venue purely so Firestore security rules can check staff access in one hop. */
data class ZoneEntity(
    val id: Long,
    val organizationId: Long,
    val venueId: Long,
    val name: String,
    val createdAt: Long,
    /** A staff-uploaded photo of the zone, shown to any member — optional, so every zone created
     * before this existed just has null. */
    val imageUrl: String? = null,
)

/** A physical route/problem on the wall. [retiredAt] marks when staff stripped it — existing
 * attempts/analyses linked to a retired route keep their link and stay fully readable; a retired
 * route just stops being offered for new attempts. */
data class RouteEntity(
    val id: Long,
    val organizationId: Long,
    val zoneId: Long,
    val name: String,
    val vGrade: Int?,
    val createdAt: Long,
    val retiredAt: Long? = null,
    /** An instructional "how to climb this" video staff uploaded, played back for any member —
     * never required to set a route, so every route created before this existed just has null. */
    val betaVideoUrl: String? = null,
)

/** One physical setting of a route — routes get stripped and reset periodically, and a route's
 * "current" setting is versioned rather than mutated in place so historic attempts stay linked
 * to the exact setting they were actually climbed on. */
data class RouteVersionEntity(
    val id: Long,
    val organizationId: Long,
    val routeId: Long,
    val setterUserId: String,
    val versionNumber: Int,
    val colorHex: Long? = null,
    val createdAt: Long,
)

/**
 * The optional, denormalized context an analysis/attempt can be enhanced with when the climber
 * chose a real gym route — never required. Mirrors the spec's `analyzeVideo(video, routeContext = null)`
 * shape: existing analysis logic takes this as an optional add-on, never a dependency.
 */
data class RouteContext(
    val organizationId: Long,
    val venueId: Long,
    val zoneId: Long,
    val routeId: Long,
    val routeVersionId: Long?,
    val routeName: String,
    val vGrade: Int?,
)

/** A physical camera staff place around the gym (e.g. mounted near a venue's walls to capture
 * beta footage) — distinct from the app's own in-app climb-recording flow. [assignedVenueId] is
 * nullable: a camera can exist unassigned before staff pick a venue for it, or be unassigned again
 * later. */
data class CameraEntity(
    val id: Long,
    val organizationId: Long,
    val name: String,
    val assignedVenueId: Long? = null,
    val createdAt: Long,
)

enum class JoinRequestStatus { PENDING, APPROVED, DENIED }

/** A member never joins instantly — a request sits here until staff act on it. Approving creates
 * an [OrganizationMembershipEntity]; denying just marks this row. Stored at Firestore doc id
 * `"{organizationId}_{userId}"`, so re-requesting after a denial reuses (and resets) the same doc
 * rather than piling up history. */
data class OrganizationJoinRequestEntity(
    val organizationId: Long,
    val userId: String,
    /** Denormalized at write time — same reasoning as [OrganizationMembershipEntity.userDisplayName]. */
    val userDisplayName: String,
    val status: JoinRequestStatus,
    val requestedAt: Long,
    val decidedAt: Long? = null,
)

/** A staff-posted announcement, read by every member — the "Updates" tab in both Club Mode
 * (staff, who can post) and the member club view (read-only). */
data class ClubUpdateEntity(
    val id: Long,
    val organizationId: Long,
    val authorUid: String,
    val text: String,
    val createdAt: Long,
    /** Optional, hand-annotated photo (see `PhotoAnnotationEditor`) — a hold circled, an arrow
     * drawn, a highlighted section of wall. Null for a plain text-only update, still the common
     * case. */
    val photoUrl: String? = null,
)

/** One message in a club's single group chat thread — any member (staff or not) can post, unlike
 * [ClubUpdateEntity] which is staff-only. [senderDisplayName] is denormalized at write time, same
 * reasoning as [OrganizationMembershipEntity.userDisplayName] — a chat history shouldn't change
 * whose name is attached to an old message just because that user later renames themself. */
data class ClubMessageEntity(
    val id: Long,
    val organizationId: Long,
    val senderUid: String,
    val senderDisplayName: String,
    val text: String,
    val sentAt: Long,
)

/**
 * A lightweight, cross-member aggregate for the "Club leaderboard" tab — deliberately not the
 * full grade/consistency/session scoring engine the main app-wide Leaderboard uses (that's built
 * entirely around [com.example.climb.data.ClimbEntity] + the friends graph, neither of which
 * applies to club-linked analysis attempts), just enough to rank members by activity at this
 * specific club: total logged attempts, total sends, and the hardest grade sent.
 */
data class ClubStatsEntity(
    val organizationId: Long,
    val userId: String,
    /** Denormalized at write time — same reasoning as [OrganizationMembershipEntity.userDisplayName]. */
    val userDisplayName: String,
    val totalAttempts: Int,
    val totalSends: Int,
    val bestVGradeSent: Int?,
    val updatedAt: Long,
)

/** Route-wide analytics: how many people tried this specific route, how many sent it, how many
 * fell — one row per route, counting every member's attempts, not just the viewer's own. */
data class RouteStatsEntity(
    val routeId: Long,
    val organizationId: Long,
    val totalAttempts: Int,
    val totalSends: Int,
    val totalFails: Int,
    val updatedAt: Long,
)

/** One row per (route, user) — who has actually sent this specific route, via the one real flow
 * that both logs a climb AND links it to a real gym route: [com.example.climb.analysis.ClimbAttemptEntity]
 * through `ClimbDetailsInputScreen`'s route picker + "Sent this climb" switch (plain `TagScreen`
 * has no route picker at all, so it can never produce one of these). Re-sending the same route just
 * refreshes [completedAt] on the same doc (id `"${routeId}_$userId"`) rather than piling up
 * duplicates — this is "have you sent it," not an attempt log. */
data class RouteCompletionEntity(
    val routeId: Long,
    val organizationId: Long,
    val userId: String,
    val userDisplayName: String,
    val completedAt: Long,
)

/**
 * A member's own sent/flashed attempt video, shared publicly for other members of the same club
 * to watch on that route's detail page — alongside, not instead of, the staff-provided beta video
 * (see [RouteEntity.betaVideoUrl]). Unlike [com.example.climb.analysis.ClimbAttemptEntity] (local
 * Room only, private to the device that recorded it), sharing uploads the video file itself to
 * Storage so every other member's phone can actually play it. A member can share the same local
 * attempt more than once — there's no dedup guard, matching the trust level of every other
 * create-only action in this repository (posting an update, sending a chat message).
 */
data class SharedAttemptEntity(
    val id: Long,
    val organizationId: Long,
    val routeId: Long,
    val userId: String,
    /** Denormalized at write time — same reasoning as [OrganizationMembershipEntity.userDisplayName]. */
    val userDisplayName: String,
    val videoUrl: String,
    val vGrade: Int?,
    val completed: Boolean,
    val flash: Boolean,
    val createdAt: Long,
)

/** One row per (shared attempt, user) who liked it — doc id `"${sharedAttemptId}_$userId"`, same
 * at-most-one-per-pair shape as [RouteCompletionEntity], so liking twice just no-ops rather than
 * piling up duplicate likes. Deliberately a real list observed live (not a denormalized counter
 * field) — this project's Firestore usage stays at gym scale, so counting a small live list is
 * cheaper to keep correct than a transactional counter would be to keep in sync. */
data class SharedAttemptLikeEntity(
    val sharedAttemptId: Long,
    val userId: String,
    val likedAt: Long,
)

fun hasStaffAccess(memberships: List<OrganizationMembershipEntity>): Boolean =
    memberships.any { it.role == OrganizationRole.STAFF || it.role == OrganizationRole.ADMIN }

fun staffOrganizationIds(memberships: List<OrganizationMembershipEntity>): Set<Long> =
    memberships.filter { it.role == OrganizationRole.STAFF || it.role == OrganizationRole.ADMIN }.map { it.organizationId }.toSet()
