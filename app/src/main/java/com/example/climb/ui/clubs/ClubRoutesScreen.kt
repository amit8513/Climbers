package com.example.climb.ui.clubs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Venues → zones → routes, scoped to one organization. Used by both Club Mode's "Manage" tab
 * (staff, [isStaff] = true — create/retire tools show) and the member club view's "Routes" tab
 * (read-only, [isStaff] = false) — same browse UI, [ClubRepository] still re-checks staff access
 * server-side on every mutation regardless of what this screen shows.
 */
@Composable
fun ClubRoutesScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    modifier: Modifier = Modifier,
) {
    var view by remember(organization.id) { mutableStateOf<ClubsView>(ClubsView.OrganizationDetail(organization)) }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            when (val current = view) {
                is ClubsView.VenueDetail -> BackRow { view = ClubsView.OrganizationDetail(current.organization) }
                is ClubsView.ZoneDetail -> BackRow { view = ClubsView.VenueDetail(current.organization, current.venue) }
                is ClubsView.RouteDetail -> BackRow { view = ClubsView.ZoneDetail(current.organization, current.venue, current.zone) }
                else -> Spacer(Modifier.height(20.dp))
            }
            Text(text = "Routes", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))

            when (val current = view) {
                is ClubsView.OrganizationDetail -> OrganizationDetailContent(
                    currentUid = currentUid,
                    clubRepository = clubRepository,
                    organization = current.organization,
                    isStaff = isStaff,
                    onOpenVenue = { venue -> view = ClubsView.VenueDetail(current.organization, venue) },
                )
                is ClubsView.VenueDetail -> VenueDetailContent(
                    currentUid = currentUid,
                    clubRepository = clubRepository,
                    organization = current.organization,
                    venue = current.venue,
                    isStaff = isStaff,
                    onOpenZone = { zone -> view = ClubsView.ZoneDetail(current.organization, current.venue, zone) },
                )
                is ClubsView.ZoneDetail -> ZoneDetailContent(
                    currentUid = currentUid,
                    clubRepository = clubRepository,
                    organization = current.organization,
                    venue = current.venue,
                    zone = current.zone,
                    isStaff = isStaff,
                    onOpenRoute = { route -> view = ClubsView.RouteDetail(current.organization, current.venue, current.zone, route) },
                )
                is ClubsView.RouteDetail -> RouteDetailContent(
                    currentUid = currentUid,
                    clubRepository = clubRepository,
                    organization = current.organization,
                    route = current.route,
                    isStaff = isStaff,
                    onRetired = { view = ClubsView.ZoneDetail(current.organization, current.venue, current.zone) },
                )
                is ClubsView.OrganizationList -> Unit // unreachable — this screen is always scoped to one organization
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Text(
        text = "← Back",
        color = ClimbPalette.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).clickable(onClick = onBack),
    )
}
