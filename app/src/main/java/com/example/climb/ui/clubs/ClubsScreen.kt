package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.JoinRequestStatus
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.OrganizationMembershipEntity
import com.example.climb.clubs.OrganizationRole
import com.example.climb.clubs.RouteEntity
import com.example.climb.clubs.VenueEntity
import com.example.climb.clubs.ZoneEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface ClubsView {
    data object OrganizationList : ClubsView
    data class OrganizationDetail(val organization: OrganizationEntity) : ClubsView
    data class VenueDetail(val organization: OrganizationEntity, val venue: VenueEntity) : ClubsView
    data class ZoneDetail(val organization: OrganizationEntity, val venue: VenueEntity, val zone: ZoneEntity) : ClubsView
}

/**
 * The member-side view only: browse clubs, see the ones you belong to, and request to join
 * others. There is no path here to becoming an owner or performing a staff action — the app has
 * no self-serve "create a club" UI at all (see `ClubRepository.ensureSeedOrganization`), and
 * managing a club you staff happens in the dedicated Club Mode UI (see `ClubNavHost`), reached via
 * the mode switcher after sign-in or the "Club Mode" section in Settings — never from here.
 * Tapping into a club you're already a member of hands off to [onOpenMemberClub] — the dedicated
 * member club experience (see `MemberClubNavHost`) — rather than drilling down inline.
 */
@Composable
fun ClubsScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    onBack: () -> Unit,
    onOpenMemberClub: (OrganizationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val memberships by clubRepository.observeMembershipsForUser(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).clickable(onClick = onBack),
            )
            Text(text = "Clubs", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))

            OrganizationListContent(
                currentUid = currentUid,
                clubRepository = clubRepository,
                memberships = memberships,
                onOpenOrganization = onOpenMemberClub,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OrganizationListContent(
    currentUid: String,
    clubRepository: ClubRepository,
    memberships: List<OrganizationMembershipEntity>,
    onOpenOrganization: (OrganizationEntity) -> Unit,
) {
    val allOrganizations by clubRepository.observeAllOrganizations().collectAsStateWithLifecycle(initialValue = emptyList())
    val myOrgIds = remember(memberships) { memberships.map { it.organizationId }.toSet() }
    val myOrganizations = remember(allOrganizations, myOrgIds) { allOrganizations.filter { it.id in myOrgIds } }
    val browsableOrganizations = remember(allOrganizations, myOrgIds) { allOrganizations.filter { it.id !in myOrgIds } }
    val scope = rememberCoroutineScope()

    if (myOrganizations.isNotEmpty()) {
        SectionCard(title = "Your gyms") {
            Column {
                myOrganizations.forEachIndexed { index, org ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    val role = memberships.firstOrNull { it.organizationId == org.id }?.role
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenOrganization(org) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = org.name, color = ClimbPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (role != null) RoleBadge(role)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (browsableOrganizations.isNotEmpty()) {
        SectionCard(title = "Other gyms") {
            Column {
                browsableOrganizations.forEachIndexed { index, org ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    OtherOrganizationRow(org = org, currentUid = currentUid, clubRepository = clubRepository, scope = scope)
                }
            }
        }
    } else if (myOrganizations.isEmpty()) {
        Text(text = "No clubs to join yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun OtherOrganizationRow(org: OrganizationEntity, currentUid: String, clubRepository: ClubRepository, scope: CoroutineScope) {
    val latestRequest by clubRepository.observeLatestJoinRequest(org.id, currentUid).collectAsStateWithLifecycle(initialValue = null)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = org.name, color = ClimbPalette.textPrimary, fontSize = 14.sp)
        if (latestRequest?.status == JoinRequestStatus.PENDING) {
            Text(text = "Request pending", color = ClimbPalette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(
                text = "Request to join",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable { scope.launch { clubRepository.requestToJoin(org.id, currentUid) } },
            )
        }
    }
}

@Composable
fun RoleBadge(role: OrganizationRole) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = role.name, color = ClimbPalette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OrganizationDetailContent(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    onOpenVenue: (VenueEntity) -> Unit,
) {
    val venues by clubRepository.observeVenuesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Text(text = organization.name, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

    SectionCard(title = "Venues") {
        if (venues.isEmpty()) {
            Text(text = "No venues yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
        } else {
            Column {
                venues.forEachIndexed { index, venue ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = venue.name,
                        color = ClimbPalette.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenVenue(venue) },
                    )
                }
            }
        }
    }

    if (isStaff) {
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Add a venue") {
            var name by remember { mutableStateOf("") }
            var address by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(value = name, onValueChange = { name = it; errorMessage = null }, label = { Text("Venue name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            errorMessage?.let { Text(text = it, color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Button(
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                onClick = {
                    scope.launch {
                        val result = clubRepository.createVenue(organization.id, currentUid, name, address.ifBlank { null })
                        result.onSuccess { name = ""; address = "" }
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
            ) { Text("Add venue") }
        }
    }
}

@Composable
fun VenueDetailContent(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    venue: VenueEntity,
    isStaff: Boolean,
    onOpenZone: (ZoneEntity) -> Unit,
) {
    val zones by clubRepository.observeZonesForVenue(venue.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Text(text = venue.name, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

    SectionCard(title = "Zones") {
        if (zones.isEmpty()) {
            Text(text = "No zones yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
        } else {
            Column {
                zones.forEachIndexed { index, zone ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = zone.name,
                        color = ClimbPalette.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenZone(zone) },
                    )
                }
            }
        }
    }

    if (isStaff) {
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Add a zone") {
            var name by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(value = name, onValueChange = { name = it; errorMessage = null }, label = { Text("Zone name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            errorMessage?.let { Text(text = it, color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Button(
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                onClick = {
                    scope.launch {
                        val result = clubRepository.createZone(organization.id, currentUid, venue.id, name)
                        result.onSuccess { name = "" }
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
            ) { Text("Add zone") }
        }
    }
}

@Composable
fun ZoneDetailContent(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    zone: ZoneEntity,
    isStaff: Boolean,
) {
    val routes by clubRepository.observeActiveRoutesForZone(zone.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Text(text = zone.name, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

    SectionCard(title = "Active routes") {
        if (routes.isEmpty()) {
            Text(text = "No active routes yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
        } else {
            Column {
                routes.forEachIndexed { index, route ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${route.name}${route.vGrade?.let { " (V$it)" } ?: ""}", color = ClimbPalette.textPrimary, fontSize = 14.sp)
                        if (isStaff) {
                            Text(
                                text = "Retire",
                                color = ClimbPalette.fell,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { scope.launch { clubRepository.retireRoute(organization.id, currentUid, route) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (isStaff) {
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Set a new route") {
            var name by remember { mutableStateOf("") }
            var grade by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(value = name, onValueChange = { name = it; errorMessage = null }, label = { Text("Route name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = grade, onValueChange = { grade = it.filter { c -> c.isDigit() } }, label = { Text("V-grade (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            errorMessage?.let { Text(text = it, color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Button(
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                onClick = {
                    scope.launch {
                        val result = clubRepository.createRoute(organization.id, currentUid, zone.id, name, grade.toIntOrNull())
                        result.onSuccess { name = ""; grade = "" }
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
            ) { Text("Set route") }
        }
    }
}
