package com.example.climb.ui.livesend.real

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.climb.clubs.CameraEntity
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.VenueEntity
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

private object CamerasRoutes {
    const val LIST = "list"
    const val PICK_VENUE = "pick_venue/{cameraId}"
    fun pickVenue(cameraId: Long) = "pick_venue/$cameraId"
}

/**
 * Real, Live-Send-styled "Cameras" screen — replaces the Dashboard's former "Venues" browsing
 * entry point (see [com.example.climb.navigation.ClubNavHost]'s `club_cameras` route and
 * [com.example.climb.ui.livesend.ClubDashboardScreen]'s "Cameras" Manage tile). About physical
 * cameras staff place around the gym to capture beta footage — distinct from the in-app
 * record-a-climb flow (already removed from every Club Mode screen). Each camera is optionally
 * assignable to one of the org's real venues via [ClubRepository.assignCameraToVenue]; the
 * underlying venue/zone/route hierarchy itself is untouched — venues still exist and still back
 * the real route-creation flow in [LiveSendClubExploreHost], only this Dashboard entry point's
 * destination changed. Same fixed, non-scrolling page + bounded-internal-scroll-list + own
 * floating bottom bar conventions as the rest of real Club Mode.
 *
 * The list <-> venue-picker swap is a real, local nested NavHost (same reasoning as
 * [LiveSendClubExploreHost]'s own nested NavHost) rather than local composable state, so it
 * inherits Navigation Compose's real default transition automatically instead of approximating
 * one with a hand-picked crossfade.
 */
@Composable
fun LiveSendCamerasScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    // Real, poppable navigation to the app's actual Home screen (a destination inside the staff
    // shell's own back stack, see ClubNavHost's `club_home_preview`) — not a permanent exit.
    onGoHome: () -> Unit,
    // The genuinely separate, permanent "leave Club Mode" action the "Exit" tab uses.
    onExitClub: () -> Unit,
    onNavBroadcast: () -> Unit,
    onNavMembers: () -> Unit,
    onNavStats: () -> Unit,
) {
    val cameras by clubRepository.observeCamerasForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val venues by clubRepository.observeVenuesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    // Drives the bottom bar's visibility below — kept outside the nested NavHost (see why below)
    // so it always reflects which of this screen's own two destinations is showing.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        NavHost(navController = navController, startDestination = CamerasRoutes.LIST, modifier = Modifier.fillMaxSize()) {
            composable(CamerasRoutes.LIST) {
                var newCameraName by remember { mutableStateOf("") }
                var addErrorMessage by remember { mutableStateOf<String?>(null) }
                var saving by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // No statusBarsPadding here — the enclosing staff Scaffold already
                        // reserves top system-bar inset space; applying it again pushed this
                        // headline visibly lower than the rest of Club Mode.
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        // A bit taller than the island's own footprint (56dp bar + 14dp*2 vertical
                        // margin), matching every other Club Mode screen's reservation, so it never
                        // overlaps this page's content even with a larger system nav-bar inset.
                        .padding(bottom = 104.dp),
                ) {
                    LiveSendPageHeader(title = "Cameras", onGoHome = onGoHome)
                    Spacer(Modifier.height(20.dp))

                    LiveSendSectionLabel(text = "Cameras (${cameras.size})")
                    Spacer(Modifier.height(10.dp))
                    if (cameras.isEmpty()) {
                        Text(text = "No cameras yet — add one below.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                    } else {
                        // Fixed-height + its own scroll (~3 rows) so a growing real camera list
                        // scrolls in place rather than stretching this fixed page.
                        Column(
                            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            cameras.forEach { camera ->
                                CameraRow(
                                    camera = camera,
                                    venueName = venues.find { it.id == camera.assignedVenueId }?.name,
                                    onClick = { navController.navigate(CamerasRoutes.pickVenue(camera.id)) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    LiveSendSectionLabel(text = "Add a camera")
                    Spacer(Modifier.height(10.dp))
                    LiveSendTextField(
                        value = newCameraName,
                        onValueChange = { newCameraName = it; addErrorMessage = null },
                        placeholder = "Camera name",
                    )
                    addErrorMessage?.let { message ->
                        Text(text = message, color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    LiveSendPrimaryButton(
                        text = "Add Camera",
                        enabled = newCameraName.isNotBlank() && !saving,
                        loading = saving,
                        onClick = {
                            saving = true
                            addErrorMessage = null
                            scope.launch {
                                val result = clubRepository.createCamera(organization.id, currentUid, newCameraName)
                                saving = false
                                result.onSuccess { newCameraName = "" }
                                result.onFailure { addErrorMessage = it.message ?: "Something went wrong" }
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            composable(
                route = CamerasRoutes.PICK_VENUE,
                arguments = listOf(navArgument("cameraId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val cameraId = backStackEntry.arguments?.getLong("cameraId") ?: return@composable
                val camera = cameras.find { it.id == cameraId }
                if (camera != null) {
                    VenuePicker(
                        camera = camera,
                        venues = venues,
                        onGoHome = onGoHome,
                        onPick = { venueId ->
                            scope.launch { clubRepository.assignCameraToVenue(organization.id, currentUid, camera.id, venueId) }
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }

        // Rendered here, as a direct sibling of NavHost rather than inside its LIST destination
        // content, so BottomCenter aligns against this screen's real full-size Box — not the
        // nested NavHost's own per-destination content box, which was only ever as tall as the
        // page's Column + bar stacked together, pinning the bar visibly near the top instead of
        // the true bottom. Hidden on the venue-picker destination, matching that it never showed
        // there before either.
        if (currentRoute == CamerasRoutes.LIST) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                    LiveSendNavTab(Icons.Filled.Campaign, "Social", selected = false, onClick = onNavBroadcast),
                    LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                    LiveSendNavTab(Icons.Filled.BarChart, "Stats", selected = false, onClick = onNavStats),
                    LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExitClub),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CameraRow(camera: CameraEntity, venueName: String?, onClick: () -> Unit) {
    LiveSendCard(cornerRadius = 14, padding = 14, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = camera.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = venueName ?: "Unassigned",
                color = if (venueName != null) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )
        }
    }
}

/** Real venue picker for one camera — a plain venue name list plus an explicit "Unassign" option,
 * matching [ClubRepository.assignCameraToVenue]'s nullable-clear shape. Shown full-screen (this
 * host's own real nested-NavHost destination, same shape as [LiveSendClubExploreHost]'s add-route
 * flow) rather than a dialog, so it keeps the same fixed, non-scrolling page conventions. */
@Composable
private fun VenuePicker(camera: CameraEntity, venues: List<VenueEntity>, onGoHome: () -> Unit, onPick: (Long?) -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        LiveSendPageHeader(title = "Assign \"${camera.name}\"", onGoHome = onGoHome)
        Spacer(Modifier.height(20.dp))
        BackLink(onClick = onCancel)
        Spacer(Modifier.height(16.dp))

        LiveSendCard(cornerRadius = 14, padding = 14, onClick = { onPick(null) }) {
            Text(text = "Unassign", color = ClimbPalette.liveSendTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))

        if (venues.isEmpty()) {
            Text(
                text = "No venues yet — add one from the Routes tile before assigning a camera.",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )
        } else {
            LiveSendSectionLabel(text = "Venues")
            Spacer(Modifier.height(10.dp))
            venues.forEachIndexed { index, venue ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                LiveSendCard(cornerRadius = 14, padding = 14, onClick = { onPick(venue.id) }) {
                    Text(text = venue.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BackLink(onClick: () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = "← Back", color = ClimbPalette.liveSendTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
