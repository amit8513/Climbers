package com.example.climb.clubs

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class OrganizationNameTakenException : Exception("An organization with that name already exists")

const val SEED_ORGANIZATION_NAME = "Golomb club"

private const val ORGANIZATIONS = "organizations"
private const val MEMBERSHIPS = "organizationMemberships"
private const val VENUES = "venues"
private const val ZONES = "zones"
private const val ROUTES = "routes"
private const val ROUTE_VERSIONS = "routeVersions"
private const val CAMERAS = "cameras"
private const val JOIN_REQUESTS = "organizationJoinRequests"
private const val COUNTERS = "counters"
private const val CLUB_UPDATES = "clubUpdates"
private const val CLUB_MESSAGES = "clubMessages"
private const val CLUB_STATS = "clubStats"
private const val ROUTE_STATS = "routeStats"
private const val ROUTE_COMPLETIONS = "routeCompletions"

private fun membershipDocId(organizationId: Long, userId: String) = "${organizationId}_$userId"

/**
 * The Clubs feature's only entry point into persistence. Firestore-backed (not local Room) so an
 * organization, membership, route, or join request created on one phone is visible on every
 * other phone — a join request submitted by one person is actually visible to staff on a
 * different device, and their approval is actually visible back to the requester. IDs stay plain
 * `Long`s (via [nextId], a Firestore-transaction counter) purely so the existing
 * `organizationId`/`venueId`/`zoneId`/`routeId`/`routeVersionId` `Long?` columns on the local
 * `climbs`/`climb_attempts` Room tables never needed to change type.
 */
class ClubRepository(private val firestore: FirebaseFirestore, private val storage: FirebaseStorage) {

    private suspend fun nextId(counterName: String): Long {
        val ref = firestore.collection(COUNTERS).document(counterName)
        return firestore.runTransaction { transaction ->
            val current = transaction.get(ref).getLong("value") ?: 0L
            val next = current + 1
            transaction.set(ref, mapOf("value" to next))
            next
        }.await()
    }

    /** Never lets a Firestore error (permission-denied, offline, whatever) crash the collector —
     * Clubs is an optional feature and must not be able to take the rest of the app down with it
     * (e.g. if security rules haven't been deployed yet, or the device is offline). Degrades to
     * an empty list instead of closing the flow with an exception. */
    private fun <T> observeCollection(query: Query, mapper: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull(mapper).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    fun observeAllOrganizations(): Flow<List<OrganizationEntity>> =
        observeCollection(firestore.collection(ORGANIZATIONS).orderBy("name")) { it.toOrganization() }

    fun observeMembershipsForUser(userId: String): Flow<List<OrganizationMembershipEntity>> =
        observeCollection(firestore.collection(MEMBERSHIPS).whereEqualTo("userId", userId)) { it.toMembership() }

    /** The organizations a user can enter "Club Mode" for — anywhere they hold STAFF or ADMIN,
     * driving both the post-login mode switcher and the Settings re-entry point. A plain member
     * or a user with zero memberships always gets an empty list here. */
    fun observeStaffOrganizationsForUser(userId: String): Flow<List<OrganizationEntity>> =
        combine(observeMembershipsForUser(userId), observeAllOrganizations()) { memberships, organizations ->
            val staffIds = staffOrganizationIds(memberships)
            organizations.filter { it.id in staffIds }
        }

    fun observeMembersForOrganization(organizationId: Long): Flow<List<OrganizationMembershipEntity>> =
        observeCollection(firestore.collection(MEMBERSHIPS).whereEqualTo("organizationId", organizationId)) { it.toMembership() }

    fun observeVenuesForOrganization(organizationId: Long): Flow<List<VenueEntity>> =
        observeCollection(firestore.collection(VENUES).whereEqualTo("organizationId", organizationId)) { it.toVenue() }

    fun observeZonesForVenue(venueId: Long): Flow<List<ZoneEntity>> =
        observeCollection(firestore.collection(ZONES).whereEqualTo("venueId", venueId)) { it.toZone() }

    fun observeCamerasForOrganization(organizationId: Long): Flow<List<CameraEntity>> =
        observeCollection(firestore.collection(CAMERAS).whereEqualTo("organizationId", organizationId)) { it.toCamera() }

    fun observeActiveRoutesForZone(zoneId: Long): Flow<List<RouteEntity>> =
        observeCollection(firestore.collection(ROUTES).whereEqualTo("zoneId", zoneId).whereEqualTo("retiredAt", null)) { it.toRoute() }

    /** Every active route across every zone/venue of one organization — used by the Overview
     * screen's "New this week" and "Latest beta" sections, which need to scan for recency/beta
     * presence without drilling down venue-by-venue-by-zone like [ClubRoutesScreen] does. Sorted
     * client-side (same reasoning as [observeUpdatesForOrganization]: no Firestore CLI here to
     * deploy a composite index for equality-filter + orderBy on a different field). */
    fun observeActiveRoutesForOrganization(organizationId: Long): Flow<List<RouteEntity>> =
        observeCollection(firestore.collection(ROUTES).whereEqualTo("organizationId", organizationId).whereEqualTo("retiredAt", null)) { it.toRoute() }
            .map { routes -> routes.sortedByDescending { it.createdAt } }

    fun observePendingJoinRequests(organizationId: Long): Flow<List<OrganizationJoinRequestEntity>> =
        observeCollection(
            firestore.collection(JOIN_REQUESTS)
                .whereEqualTo("organizationId", organizationId)
                .whereEqualTo("status", JoinRequestStatus.PENDING.name),
        ) { it.toJoinRequest() }

    fun observeLatestJoinRequest(organizationId: Long, userId: String): Flow<OrganizationJoinRequestEntity?> = callbackFlow {
        val registration = firestore.collection(JOIN_REQUESTS).document(membershipDocId(organizationId, userId))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toJoinRequest())
            }
        awaitClose { registration.remove() }
    }

    fun observeOrganization(organizationId: Long): Flow<OrganizationEntity?> = callbackFlow {
        val registration = firestore.collection(ORGANIZATIONS).document(organizationId.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toOrganization())
            }
        awaitClose { registration.remove() }
    }

    // Sorted client-side rather than via Firestore orderBy() — combining an equality filter with
    // orderBy on a different field needs a manually-created composite index, and this environment
    // has no Firebase CLI to deploy one. Club update/stats counts are small enough that this is
    // free in practice.
    fun observeUpdatesForOrganization(organizationId: Long): Flow<List<ClubUpdateEntity>> =
        observeCollection(firestore.collection(CLUB_UPDATES).whereEqualTo("organizationId", organizationId)) { it.toClubUpdate() }
            .map { updates -> updates.sortedByDescending { it.createdAt } }

    suspend fun postUpdate(organizationId: Long, staffUserId: String, text: String): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, staffUserId)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Update can't be empty" }
        val id = nextId(CLUB_UPDATES)
        firestore.collection(CLUB_UPDATES).document(id.toString())
            .set(mapOf("organizationId" to organizationId, "authorUid" to staffUserId, "text" to trimmed, "createdAt" to System.currentTimeMillis())).await()
    }

    /** Any staff member can delete any post — moderation, same trust level as posting itself
     * ([postUpdate] already requires [requireStaffAccess]), not limited to the original author. */
    suspend fun deleteUpdate(organizationId: Long, userId: String, update: ClubUpdateEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        firestore.collection(CLUB_UPDATES).document(update.id.toString()).delete().await()
    }

    /** The club's single group chat thread — every member (staff or not) can read and post, unlike
     * [observeUpdatesForOrganization]/[postUpdate] which are staff-only. Sorted client-side by
     * [ClubMessageEntity.sentAt] for the same reason as [observeUpdatesForOrganization]: no
     * Firestore CLI here to deploy a composite index for an equality filter plus orderBy. */
    fun observeMessagesForOrganization(organizationId: Long): Flow<List<ClubMessageEntity>> =
        observeCollection(firestore.collection(CLUB_MESSAGES).whereEqualTo("organizationId", organizationId)) { it.toClubMessage() }
            .map { messages -> messages.sortedBy { it.sentAt } }

    suspend fun sendMessage(organizationId: Long, senderUid: String, senderDisplayName: String, text: String): Result<Unit> = runCatching {
        requireMembership(organizationId, senderUid)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message can't be empty" }
        val id = nextId(CLUB_MESSAGES)
        firestore.collection(CLUB_MESSAGES).document(id.toString())
            .set(
                mapOf(
                    "organizationId" to organizationId,
                    "senderUid" to senderUid,
                    "senderDisplayName" to senderDisplayName,
                    "text" to trimmed,
                    "sentAt" to System.currentTimeMillis(),
                ),
            ).await()
    }

    /** Any member records their own attempt stats — this is participation bookkeeping, not a
     * staff mutation, so unlike [createVenue]/[createRoute]/etc. it doesn't call
     * [requireStaffAccess]. Called once per saved club-linked attempt (see
     * `ClimbDetailsInputScreen`), never edited or removed afterward. */
    suspend fun recordClubAttempt(organizationId: Long, userId: String, username: String, vGrade: Int?, completed: Boolean): Result<Unit> = runCatching {
        val ref = firestore.collection(CLUB_STATS).document(membershipDocId(organizationId, userId))
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val totalAttempts = (snapshot.getLong("totalAttempts") ?: 0L) + 1
            val totalSends = (snapshot.getLong("totalSends") ?: 0L) + if (completed) 1 else 0
            val previousBest = snapshot.getLong("bestVGradeSent")?.toInt()
            val bestVGradeSent = if (completed && vGrade != null) maxOf(previousBest ?: Int.MIN_VALUE, vGrade) else previousBest
            transaction.set(
                ref,
                mapOf(
                    "organizationId" to organizationId,
                    "userId" to userId,
                    "userDisplayName" to username,
                    "totalAttempts" to totalAttempts,
                    "totalSends" to totalSends,
                    "bestVGradeSent" to bestVGradeSent,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
        Unit
    }

    fun observeClubLeaderboard(organizationId: Long): Flow<List<ClubStatsEntity>> =
        observeCollection(firestore.collection(CLUB_STATS).whereEqualTo("organizationId", organizationId)) { it.toClubStats() }
            .map { stats -> stats.sortedWith(compareByDescending<ClubStatsEntity> { it.totalSends }.thenByDescending { it.bestVGradeSent ?: -1 }) }

    /** Route-wide analytics — how many people tried this route, how many sent it, how many fell.
     * Any member records their own attempt against the route's shared counters (participation
     * bookkeeping, not a staff mutation, same trust level as [recordClubAttempt]). */
    suspend fun recordRouteAttempt(routeId: Long, organizationId: Long, completed: Boolean): Result<Unit> = runCatching {
        val ref = firestore.collection(ROUTE_STATS).document(routeId.toString())
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val totalAttempts = (snapshot.getLong("totalAttempts") ?: 0L) + 1
            val totalSends = (snapshot.getLong("totalSends") ?: 0L) + if (completed) 1 else 0
            val totalFails = (snapshot.getLong("totalFails") ?: 0L) + if (completed) 0 else 1
            transaction.set(
                ref,
                mapOf(
                    "routeId" to routeId,
                    "organizationId" to organizationId,
                    "totalAttempts" to totalAttempts,
                    "totalSends" to totalSends,
                    "totalFails" to totalFails,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
        Unit
    }

    fun observeRouteStats(routeId: Long): Flow<RouteStatsEntity?> = callbackFlow {
        val registration = firestore.collection(ROUTE_STATS).document(routeId.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toRouteStats())
            }
        awaitClose { registration.remove() }
    }

    /** A route's current color (and other per-setting data) lives on its latest [RouteVersionEntity]
     * — routes get re-stripped/re-set periodically, so this is versioned rather than a field on
     * [RouteEntity] itself (see that entity's doc comment). Same query-then-pick-max pattern as
     * [buildRouteContext]'s one-shot lookup, just live. */
    fun observeLatestRouteVersion(routeId: Long): Flow<RouteVersionEntity?> =
        observeCollection(firestore.collection(ROUTE_VERSIONS).whereEqualTo("routeId", routeId)) { it.toRouteVersion() }
            .map { versions -> versions.maxByOrNull { it.versionNumber } }

    /** Records that [userId] has sent [routeId] — same trust level as [recordRouteAttempt]
     * (participation bookkeeping, not a staff mutation, no [requireStaffAccess]). One doc per
     * (route, user): re-sending the same route just refreshes [RouteCompletionEntity.completedAt]
     * on the same doc rather than creating a duplicate row. */
    suspend fun recordRouteCompletion(routeId: Long, organizationId: Long, userId: String, userDisplayName: String): Result<Unit> = runCatching {
        firestore.collection(ROUTE_COMPLETIONS).document("${routeId}_$userId")
            .set(
                mapOf(
                    "routeId" to routeId,
                    "organizationId" to organizationId,
                    "userId" to userId,
                    "userDisplayName" to userDisplayName,
                    "completedAt" to System.currentTimeMillis(),
                ),
            ).await()
        Unit
    }

    /** Real users who've sent this route, most-recent-first — a plain chronological list, not a
     * fabricated ranking score (there's no real per-user tiebreak metric to rank by here). */
    fun observeRouteCompletions(routeId: Long): Flow<List<RouteCompletionEntity>> =
        observeCollection(firestore.collection(ROUTE_COMPLETIONS).whereEqualTo("routeId", routeId)) { it.toRouteCompletion() }
            .map { completions -> completions.sortedByDescending { it.completedAt } }

    /** Creating an organization makes the creator its ADMIN immediately. There is no self-serve
     * "create a club" UI anywhere in the app — this only runs via [ensureSeedOrganization], the
     * one-time bootstrap for the single club this app currently supports. */
    suspend fun createOrganization(name: String, creatorUid: String, creatorUsername: String): Result<OrganizationEntity> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Organization name can't be empty" }
        val nameLower = trimmed.lowercase(Locale.US)
        if (findOrganizationByNameLower(nameLower) != null) throw OrganizationNameTakenException()
        val now = System.currentTimeMillis()
        val id = nextId(ORGANIZATIONS)
        firestore.collection(ORGANIZATIONS).document(id.toString())
            .set(mapOf("name" to trimmed, "nameLower" to nameLower, "createdAt" to now)).await()
        firestore.collection(MEMBERSHIPS).document(membershipDocId(id, creatorUid))
            .set(
                mapOf(
                    "organizationId" to id, "userId" to creatorUid, "userDisplayName" to creatorUsername,
                    "role" to OrganizationRole.ADMIN.name, "joinedAt" to now,
                ),
            ).await()
        OrganizationEntity(id = id, name = trimmed, createdAt = now)
    }

    /** No user can open a new club from anywhere in the app. This only ever creates
     * [SEED_ORGANIZATION_NAME] — the single club this build supports — and only the first time
     * it's called across every phone (the name-uniqueness check is shared via Firestore, not
     * per-device); every call after that is a no-op. [adminUid] becomes its ADMIN, which today is
     * whichever account happens to win the race to call this first. */
    suspend fun ensureSeedOrganization(adminUid: String, adminUsername: String) {
        // Best-effort — this runs unconditionally right after every sign-in (see ClimbNavHost),
        // so a Firestore hiccup here (offline, security rules not yet deployed, etc.) must never
        // surface as an exception and take the rest of the app down with it.
        runCatching {
            if (findOrganizationByNameLower(SEED_ORGANIZATION_NAME.lowercase(Locale.US)) != null) return@runCatching
            createOrganization(SEED_ORGANIZATION_NAME, adminUid, adminUsername)
        }
    }

    private suspend fun findOrganizationByNameLower(nameLower: String): OrganizationEntity? =
        firestore.collection(ORGANIZATIONS).whereEqualTo("nameLower", nameLower).limit(1).get().await()
            .documents.firstOrNull()?.toOrganization()

    /** Joining is never instant — this just records interest. Staff decide via
     * [approveJoinRequest]/[denyJoinRequest]. Idempotent: calling again while already a member or
     * already pending is a silent no-op rather than an error. */
    suspend fun requestToJoin(organizationId: Long, userId: String, username: String): Result<Unit> = runCatching {
        if (getMembership(organizationId, userId) != null) return@runCatching
        val docRef = firestore.collection(JOIN_REQUESTS).document(membershipDocId(organizationId, userId))
        val existing = docRef.get().await().toJoinRequest()
        if (existing?.status == JoinRequestStatus.PENDING) return@runCatching
        docRef.set(
            mapOf(
                "organizationId" to organizationId,
                "userId" to userId,
                "userDisplayName" to username,
                "status" to JoinRequestStatus.PENDING.name,
                "requestedAt" to System.currentTimeMillis(),
                "decidedAt" to null,
            ),
        ).await()
    }

    suspend fun approveJoinRequest(organizationId: Long, staffUserId: String, request: OrganizationJoinRequestEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, staffUserId)
        require(request.status == JoinRequestStatus.PENDING) { "This request was already decided" }
        val now = System.currentTimeMillis()
        firestore.collection(MEMBERSHIPS).document(membershipDocId(organizationId, request.userId))
            .set(
                mapOf(
                    "organizationId" to organizationId, "userId" to request.userId, "userDisplayName" to request.userDisplayName,
                    "role" to OrganizationRole.MEMBER.name, "joinedAt" to now,
                ),
            ).await()
        firestore.collection(JOIN_REQUESTS).document(membershipDocId(organizationId, request.userId))
            .update(mapOf("status" to JoinRequestStatus.APPROVED.name, "decidedAt" to now)).await()
    }

    suspend fun denyJoinRequest(organizationId: Long, staffUserId: String, request: OrganizationJoinRequestEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, staffUserId)
        require(request.status == JoinRequestStatus.PENDING) { "This request was already decided" }
        firestore.collection(JOIN_REQUESTS).document(membershipDocId(organizationId, request.userId))
            .update(mapOf("status" to JoinRequestStatus.DENIED.name, "decidedAt" to System.currentTimeMillis())).await()
    }

    private suspend fun getMembership(organizationId: Long, userId: String): OrganizationMembershipEntity? =
        firestore.collection(MEMBERSHIPS).document(membershipDocId(organizationId, userId)).get().await().toMembership()

    /** Every staff mutation re-checks membership via a live Firestore read rather than trusting a
     * client-held role flag — enforced again independently by the Firestore security rules
     * themselves, since (unlike the old local-Room version) a real network client could otherwise
     * call the SDK directly and bypass this app-code check entirely. */
    private suspend fun requireStaffAccess(organizationId: Long, userId: String) {
        val membership = getMembership(organizationId, userId)
        val allowed = membership != null && (membership.role == OrganizationRole.STAFF || membership.role == OrganizationRole.ADMIN)
        if (!allowed) throw SecurityException("Not authorized to manage this organization")
    }

    /** Any real membership (member, staff, or admin) qualifies — unlike [requireStaffAccess],
     * used for actions any club member should be able to do, like posting in the group chat. */
    private suspend fun requireMembership(organizationId: Long, userId: String) {
        if (getMembership(organizationId, userId) == null) throw SecurityException("Not a member of this organization")
    }

    suspend fun createVenue(organizationId: Long, userId: String, name: String, address: String?): Result<VenueEntity> = runCatching {
        requireStaffAccess(organizationId, userId)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Venue name can't be empty" }
        val now = System.currentTimeMillis()
        val id = nextId(VENUES)
        val cleanAddress = address?.trim()?.ifBlank { null }
        firestore.collection(VENUES).document(id.toString())
            .set(mapOf("organizationId" to organizationId, "name" to trimmed, "address" to cleanAddress, "createdAt" to now)).await()
        VenueEntity(id = id, organizationId = organizationId, name = trimmed, address = cleanAddress, createdAt = now)
    }

    suspend fun createCamera(organizationId: Long, userId: String, name: String): Result<CameraEntity> = runCatching {
        requireStaffAccess(organizationId, userId)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Camera name can't be empty" }
        val now = System.currentTimeMillis()
        val id = nextId(CAMERAS)
        firestore.collection(CAMERAS).document(id.toString())
            .set(mapOf("organizationId" to organizationId, "name" to trimmed, "assignedVenueId" to null, "createdAt" to now)).await()
        CameraEntity(id = id, organizationId = organizationId, name = trimmed, assignedVenueId = null, createdAt = now)
    }

    /** [venueId] null unassigns the camera — same nullable-clear shape as [setRouteBetaVideo] with
     * a null url. */
    suspend fun assignCameraToVenue(organizationId: Long, userId: String, cameraId: Long, venueId: Long?): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        firestore.collection(CAMERAS).document(cameraId.toString()).update("assignedVenueId", venueId).await()
    }

    /** Cascades through every zone (and everything under it — see [deleteZoneCascade]) before
     * removing the venue doc itself. */
    suspend fun deleteVenue(organizationId: Long, userId: String, venue: VenueEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        val zones = firestore.collection(ZONES).whereEqualTo("venueId", venue.id).get().await().documents.mapNotNull { it.toZone() }
        zones.forEach { deleteZoneCascade(it) }
        firestore.collection(VENUES).document(venue.id.toString()).delete().await()
    }

    suspend fun createZone(organizationId: Long, userId: String, venueId: Long, name: String): Result<ZoneEntity> = runCatching {
        requireStaffAccess(organizationId, userId)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Zone name can't be empty" }
        val now = System.currentTimeMillis()
        val id = nextId(ZONES)
        firestore.collection(ZONES).document(id.toString())
            .set(mapOf("organizationId" to organizationId, "venueId" to venueId, "name" to trimmed, "createdAt" to now, "imageUrl" to null)).await()
        ZoneEntity(id = id, organizationId = organizationId, venueId = venueId, name = trimmed, createdAt = now)
    }

    /** Same two-step upload-then-attach shape as [uploadBetaVideo]/[setRouteBetaVideo]. */
    suspend fun uploadZonePhoto(organizationId: Long, userId: String, zoneId: Long, imageUri: Uri, contentType: String?): Result<String> = runCatching {
        requireStaffAccess(organizationId, userId)
        val ref = storage.reference.child("club_zone_photos/$organizationId/$zoneId.jpg")
        val metadata = StorageMetadata.Builder().apply {
            if (contentType != null) setContentType(contentType)
        }.build()
        ref.putFile(imageUri, metadata).await()
        ref.downloadUrl.await().toString()
    }

    suspend fun setZoneImage(organizationId: Long, userId: String, zone: ZoneEntity, imageUrl: String?): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        firestore.collection(ZONES).document(zone.id.toString()).update("imageUrl", imageUrl).await()
    }

    suspend fun deleteZone(organizationId: Long, userId: String, zone: ZoneEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        deleteZoneCascade(zone)
    }

    /** Removes every route under [zone] (each route's versions and beta video first), then the
     * zone's own photo and doc — same delete-doc-then-delete-file shape as
     * [com.example.climb.sharing.ClimbSyncRepository.deleteSyncedClimb], with each Storage
     * deletion wrapped in its own [runCatching] so a missing/already-gone file never blocks
     * removing the rest. Shared by [deleteZone] and [deleteVenue], which already checked staff
     * access before calling this. */
    private suspend fun deleteZoneCascade(zone: ZoneEntity) {
        val routes = firestore.collection(ROUTES).whereEqualTo("zoneId", zone.id).get().await().documents.mapNotNull { it.toRoute() }
        routes.forEach { route ->
            val versionDocs = firestore.collection(ROUTE_VERSIONS).whereEqualTo("routeId", route.id).get().await().documents
            versionDocs.forEach { doc -> runCatching { doc.reference.delete().await() } }
            if (route.betaVideoUrl != null) {
                runCatching { storage.reference.child("club_beta_videos/${route.organizationId}/${route.id}.mp4").delete().await() }
            }
            firestore.collection(ROUTES).document(route.id.toString()).delete().await()
        }
        if (zone.imageUrl != null) {
            runCatching { storage.reference.child("club_zone_photos/${zone.organizationId}/${zone.id}.jpg").delete().await() }
        }
        firestore.collection(ZONES).document(zone.id.toString()).delete().await()
    }

    suspend fun createRoute(organizationId: Long, userId: String, zoneId: Long, name: String, vGrade: Int?, colorHex: Long? = null): Result<RouteEntity> = runCatching {
        requireStaffAccess(organizationId, userId)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Route name can't be empty" }
        val now = System.currentTimeMillis()
        val routeId = nextId(ROUTES)
        firestore.collection(ROUTES).document(routeId.toString())
            .set(mapOf("organizationId" to organizationId, "zoneId" to zoneId, "name" to trimmed, "vGrade" to vGrade, "createdAt" to now, "retiredAt" to null)).await()
        val versionId = nextId(ROUTE_VERSIONS)
        firestore.collection(ROUTE_VERSIONS).document(versionId.toString())
            .set(
                mapOf(
                    "organizationId" to organizationId, "routeId" to routeId, "setterUserId" to userId,
                    "versionNumber" to 1, "colorHex" to colorHex, "createdAt" to now,
                ),
            ).await()
        RouteEntity(id = routeId, organizationId = organizationId, zoneId = zoneId, name = trimmed, vGrade = vGrade, createdAt = now)
    }

    suspend fun retireRoute(organizationId: Long, userId: String, route: RouteEntity): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        firestore.collection(ROUTES).document(route.id.toString()).update("retiredAt", System.currentTimeMillis()).await()
    }

    /** Uploads straight from the picked content:// [videoUri] (same `putFile` pattern as
     * [com.example.climb.data.social.SocialRepository.uploadProfilePhoto]) and returns the
     * playback URL — [setRouteBetaVideo] is what actually attaches it to a route and is the
     * staff-gated half of this two-step flow. */
    suspend fun uploadBetaVideo(organizationId: Long, userId: String, routeId: Long, videoUri: Uri, contentType: String?): Result<String> = runCatching {
        requireStaffAccess(organizationId, userId)
        val ref = storage.reference.child("club_beta_videos/$organizationId/$routeId.mp4")
        val metadata = StorageMetadata.Builder().apply {
            if (contentType != null) setContentType(contentType)
        }.build()
        ref.putFile(videoUri, metadata).await()
        ref.downloadUrl.await().toString()
    }

    suspend fun setRouteBetaVideo(organizationId: Long, userId: String, route: RouteEntity, videoUrl: String?): Result<Unit> = runCatching {
        requireStaffAccess(organizationId, userId)
        firestore.collection(ROUTES).document(route.id.toString()).update("betaVideoUrl", videoUrl).await()
    }

    /** Builds the enhancement object an attempt/climb can optionally attach — never required by
     * anything downstream (see [RouteContext]). */
    suspend fun buildRouteContext(organizationId: Long, venueId: Long, zoneId: Long, route: RouteEntity): RouteContext {
        val latestVersion = firestore.collection(ROUTE_VERSIONS).whereEqualTo("routeId", route.id).get().await()
            .documents.mapNotNull { it.toRouteVersion() }.maxByOrNull { it.versionNumber }
        return RouteContext(
            organizationId = organizationId,
            venueId = venueId,
            zoneId = zoneId,
            routeId = route.id,
            routeVersionId = latestVersion?.id,
            routeName = route.name,
            vGrade = route.vGrade,
        )
    }
}

private fun DocumentSnapshot.toOrganization(): OrganizationEntity? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return OrganizationEntity(id = id.toLong(), name = name, createdAt = getLong("createdAt") ?: 0L)
}

private fun DocumentSnapshot.toMembership(): OrganizationMembershipEntity? {
    if (!exists()) return null
    val role = getString("role")?.let { raw -> runCatching { OrganizationRole.valueOf(raw) }.getOrNull() } ?: return null
    val userId = getString("userId") ?: return null
    return OrganizationMembershipEntity(
        organizationId = getLong("organizationId") ?: return null,
        userId = userId,
        userDisplayName = getString("userDisplayName") ?: userId,
        role = role,
        joinedAt = getLong("joinedAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toVenue(): VenueEntity? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return VenueEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        name = name,
        address = getString("address"),
        createdAt = getLong("createdAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toCamera(): CameraEntity? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return CameraEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        name = name,
        assignedVenueId = getLong("assignedVenueId"),
        createdAt = getLong("createdAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toZone(): ZoneEntity? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return ZoneEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        venueId = getLong("venueId") ?: return null,
        name = name,
        createdAt = getLong("createdAt") ?: 0L,
        imageUrl = getString("imageUrl"),
    )
}

private fun DocumentSnapshot.toRoute(): RouteEntity? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return RouteEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        zoneId = getLong("zoneId") ?: return null,
        name = name,
        vGrade = getLong("vGrade")?.toInt(),
        createdAt = getLong("createdAt") ?: 0L,
        retiredAt = getLong("retiredAt"),
        betaVideoUrl = getString("betaVideoUrl"),
    )
}

private fun DocumentSnapshot.toRouteVersion(): RouteVersionEntity? {
    if (!exists()) return null
    return RouteVersionEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        routeId = getLong("routeId") ?: return null,
        setterUserId = getString("setterUserId") ?: return null,
        versionNumber = (getLong("versionNumber") ?: return null).toInt(),
        colorHex = getLong("colorHex"),
        createdAt = getLong("createdAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toJoinRequest(): OrganizationJoinRequestEntity? {
    if (!exists()) return null
    val status = getString("status")?.let { raw -> runCatching { JoinRequestStatus.valueOf(raw) }.getOrNull() } ?: return null
    val userId = getString("userId") ?: return null
    return OrganizationJoinRequestEntity(
        organizationId = getLong("organizationId") ?: return null,
        userId = userId,
        userDisplayName = getString("userDisplayName") ?: userId,
        status = status,
        requestedAt = getLong("requestedAt") ?: 0L,
        decidedAt = getLong("decidedAt"),
    )
}

private fun DocumentSnapshot.toClubUpdate(): ClubUpdateEntity? {
    if (!exists()) return null
    val text = getString("text") ?: return null
    return ClubUpdateEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        authorUid = getString("authorUid") ?: return null,
        text = text,
        createdAt = getLong("createdAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toClubMessage(): ClubMessageEntity? {
    if (!exists()) return null
    val text = getString("text") ?: return null
    val senderUid = getString("senderUid") ?: return null
    return ClubMessageEntity(
        id = id.toLong(),
        organizationId = getLong("organizationId") ?: return null,
        senderUid = senderUid,
        senderDisplayName = getString("senderDisplayName") ?: senderUid,
        text = text,
        sentAt = getLong("sentAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toRouteStats(): RouteStatsEntity? {
    if (!exists()) return null
    return RouteStatsEntity(
        routeId = getLong("routeId") ?: return null,
        organizationId = getLong("organizationId") ?: return null,
        totalAttempts = (getLong("totalAttempts") ?: 0L).toInt(),
        totalSends = (getLong("totalSends") ?: 0L).toInt(),
        totalFails = (getLong("totalFails") ?: 0L).toInt(),
        updatedAt = getLong("updatedAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toClubStats(): ClubStatsEntity? {
    if (!exists()) return null
    val userId = getString("userId") ?: return null
    return ClubStatsEntity(
        organizationId = getLong("organizationId") ?: return null,
        userId = userId,
        userDisplayName = getString("userDisplayName") ?: userId,
        totalAttempts = (getLong("totalAttempts") ?: 0L).toInt(),
        totalSends = (getLong("totalSends") ?: 0L).toInt(),
        bestVGradeSent = getLong("bestVGradeSent")?.toInt(),
        updatedAt = getLong("updatedAt") ?: 0L,
    )
}

private fun DocumentSnapshot.toRouteCompletion(): RouteCompletionEntity? {
    if (!exists()) return null
    val userId = getString("userId") ?: return null
    return RouteCompletionEntity(
        routeId = getLong("routeId") ?: return null,
        organizationId = getLong("organizationId") ?: return null,
        userId = userId,
        userDisplayName = getString("userDisplayName") ?: userId,
        completedAt = getLong("completedAt") ?: 0L,
    )
}
