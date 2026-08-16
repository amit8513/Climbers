package com.example.climb.ui.livesend.real

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.RouteEntity
import com.example.climb.clubs.VenueEntity
import com.example.climb.clubs.ZoneEntity
import com.example.climb.data.RouteColor
import com.example.climb.ui.livesend.ExploreScreen
import com.example.climb.ui.livesend.ExploreSection
import com.example.climb.ui.livesend.ExploreVenue
import com.example.climb.ui.livesend.PopularRoute
import com.example.climb.ui.livesend.RouteCompletionRow
import com.example.climb.ui.livesend.RouteDetailScreen
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

/** Fallback color cycle for route rows that have no real color set yet (every route created
 * before the color picker existed, or one a staffer left unset — color is optional on
 * [com.example.climb.clubs.RouteVersionEntity]). Assigns one by list position from the fixed
 * `liveSend*` accents, same reasoning as the rest of this package's fixed palette — not fabricating
 * a per-route value, just a stand-in until a real one is set. A per-row *live* color lookup (one
 * listener per route) is skipped here for the same cost reason real per-row send counts are (see
 * [toPopularRoute]) — [RouteDetailScreen]'s single-route view below does look up the real color
 * live, since that's one listener, not N. */
private val ROUTE_COLOR_CYCLE = listOf(
    ClimbPalette.liveSendCta to ClimbPalette.liveSendTextPrimary,
    ClimbPalette.liveSendGold to ClimbPalette.liveSendBg,
    ClimbPalette.liveSendInfo to ClimbPalette.liveSendTextPrimary,
)

/** Real nested NavHost routes for this host's own sub-screens — Browse (Explore) is the start
 * destination; RouteDetail and the add-venue/add-route steps are all real, poppable destinations
 * reached via real navigate()/popBackStack() calls. Deliberately NOT specifying any custom
 * enterTransition/exitTransition anywhere below — the whole point of converting these from a
 * hand-animated AnimatedContent crossfade to a real nested NavHost is to inherit whatever
 * Navigation Compose's actual default transition is, automatically, identical to every other real
 * destination in this app (e.g. entering Broadcast) — no more guessing at a transition spec. */
private object ExploreRoutes {
    const val BROWSE = "browse"
    const val ROUTE_DETAIL = "route_detail/{routeId}"
    fun routeDetail(routeId: Long) = "route_detail/$routeId"
    const val ADD_VENUE = "add_venue"
    const val ADD_ROUTE_PICK_VENUE = "add_route_pick_venue"
    const val ADD_ROUTE_PICK_ZONE = "add_route_pick_zone/{venueId}"
    fun addRoutePickZone(venueId: Long) = "add_route_pick_zone/$venueId"
    const val ADD_ROUTE_SET_ROUTE = "add_route_set_route/{venueId}/{zoneId}"
    fun addRouteSetRoute(venueId: Long, zoneId: Long) = "add_route_set_route/$venueId/$zoneId"
}

private fun RouteEntity.toPopularRoute(index: Int): PopularRoute {
    val (dotColor, badgeTextColor) = ROUTE_COLOR_CYCLE[index % ROUTE_COLOR_CYCLE.size]
    return PopularRoute(
        name = name,
        // Real per-route "N sends" here would need a live listener per route (observeRouteStats is
        // one document per route) — left off this list view for cost, not fabricated; the route's
        // real send-rate/attempts/sends still show on RouteDetailScreen, a single cheap read.
        subtitle = vGrade?.let { "V$it" } ?: "",
        grade = vGrade?.let { "V$it" } ?: "—",
        dotColor = dotColor,
        badgeTextColor = badgeTextColor,
        id = id,
    )
}

/**
 * Shared real Explore → RouteDetail flow, backed by [ClubRepository] — used both from the staff
 * Club Dashboard's Routes/Venues tiles ([com.example.climb.navigation.ClubNavHost]) and the member
 * shell's Routes tab ([com.example.climb.navigation.MemberClubNavHost]). Owns a real, local nested
 * NavHost (see [ExploreRoutes]) for its own sub-screens rather than a top-level NavController,
 * since there are only ever a handful of screens in this particular stack — but it's still real
 * navigate()/popBackStack(), not local composable state, specifically so screen changes here
 * inherit Navigation Compose's real default transition instead of approximating one.
 *
 * [onClubTab] is what "Club" on the shared bottom bar means in the caller's context (staff: back
 * to the dashboard; member: back to the club overview) — supplied by the caller since it differs
 * per host. [onRanksTab] is optional real cross-navigation to a leaderboard-like screen where one
 * exists (only the member context has one — [com.example.climb.navigation.MemberClubNavHost]'s
 * Club leaderboard tab); it's a TODO(live-send-real) no-op by default.
 *
 * TODO(live-send-real): onPlayVideo/onLogAttempt/onRecordAttempt/onSearchClick are left as
 * no-ops here — real attempt-logging needs the full existing ClimbDetailsInputScreen-style form,
 * out of scope for this pass. [onProgressTab]/[onRanksTab] DO have real destinations (the app's
 * own personal Progress/Leaderboard screens) — supplied by the caller, since only
 * [com.example.climb.navigation.ClubNavHost] (staff) currently wires them to anything.
 *
 * [isStaff] (real Club Mode, per [com.example.climb.navigation.ClubNavHost]'s own `AppMode.Club`
 * distinction — not the member-facing [com.example.climb.navigation.MemberClubNavHost] context)
 * gates staff-only actions (add route/venue, set/replace beta video reusing the exact same
 * picker + upload + attach mechanism as [com.example.climb.ui.clubs.ClubRouteDetailScreen]'s
 * `BetaVideoUploader` — [ClubRepository.uploadBetaVideo] + [ClubRepository.setRouteBetaVideo], not
 * reinvented). The record FAB is hidden unconditionally in every club context now (staff and
 * member alike) per user request — logging a personal climb isn't a club-mode action.
 *
 * [onGoHome] is real navigation to the app's actual Home — both screens' "Feed" bottom-bar tab
 * (renamed "Home") had no real destination (no feed screen exists in club mode), so it's
 * repurposed to this instead of being dead.
 */
@Composable
fun LiveSendClubExploreHost(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    onClubTab: () -> Unit,
    onGoHome: () -> Unit,
    onProgressTab: () -> Unit = {},
    onRanksTab: () -> Unit = {},
    isStaff: Boolean = false,
    // Which section the Dashboard's Routes vs Venues tile should land on — see [ExploreSection].
    // Member context (no tile distinction exists there) always uses the default.
    initialSection: ExploreSection = ExploreSection.ROUTES,
) {
    val routes by clubRepository.observeActiveRoutesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val venueEntities by clubRepository.observeVenuesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Hoisted above the nested NavHost, not inside any one destination — rememberLauncherForActivityResult
    // needs a stable @Composable call site that isn't torn down when navigating between
    // destinations. Tracks which route the upload is for via plain state (there's no more
    // `selectedRouteId` composable state to read now that route selection is a real nav arg).
    var betaUploadRouteId by remember { mutableStateOf<Long?>(null) }
    val pickBetaVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val route = routes.find { it.id == betaUploadRouteId }
        if (uri == null || route == null) return@rememberLauncherForActivityResult
        scope.launch {
            val contentType = context.contentResolver.getType(uri)
            val uploadResult = clubRepository.uploadBetaVideo(organization.id, currentUid, route.id, uri, contentType)
            val url = uploadResult.getOrNull() ?: return@launch
            clubRepository.setRouteBetaVideo(organization.id, currentUid, route, url)
        }
    }

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ExploreRoutes.BROWSE) {
        composable(ExploreRoutes.BROWSE) {
            // Scoped to this destination's own back-stack entry (Navigation Compose keeps a
            // destination's rememberSaveable state alive while it stays on the stack — e.g.
            // pushing RouteDetail on top and popping back still remembers this filter), matching
            // the original host-level state's survival across a Browse -> RouteDetail -> back trip.
            var selectedVenueId by rememberSaveable { mutableStateOf<Long?>(null) }
            val selectedVenue = venueEntities.find { it.id == selectedVenueId }
            // Real venue -> routes relationship is two hops (route.zoneId -> ZoneEntity.venueId),
            // so tapping a venue tile pulls just that venue's zones, then filters the org's
            // already-loaded route list by zone membership client-side rather than a second
            // per-venue route query.
            val visibleRoutes = if (selectedVenue != null) {
                val venueZones by clubRepository.observeZonesForVenue(selectedVenue.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val zoneIds = venueZones.map { it.id }.toSet()
                routes.filter { it.zoneId in zoneIds }
            } else {
                routes
            }

            ExploreScreen(
                organizationName = organization.name,
                routes = visibleRoutes.mapIndexed { index, route -> route.toPopularRoute(index) },
                venues = venueEntities.map { venue -> ExploreVenue(name = venue.name, routesLabel = "", id = venue.id) },
                venueFilterLabel = selectedVenue?.name,
                onClearVenueFilter = { selectedVenueId = null },
                onSearchClick = { /* TODO(live-send-real): no real route-search screen exists yet */ },
                onRouteClick = { route -> route.id?.let { navController.navigate(ExploreRoutes.routeDetail(it)) } },
                onVenueClick = { venue -> selectedVenueId = venue.id },
                onNavigateFeed = onGoHome,
                onNavigateProgress = onProgressTab,
                onNavigateRanks = onRanksTab,
                onNavigateClub = onClubTab,
                onFabClick = { /* TODO(live-send-real): needs the real record flow */ },
                // Unconditionally off in every club context now (staff and member) per user
                // request — was `!isStaff` before, so members still saw it; logging a personal
                // climb from this browsing screen isn't a club-mode action regardless of role.
                showRecordFab = false,
                initialSection = initialSection,
                onAddRouteClick = if (isStaff) { { navController.navigate(ExploreRoutes.ADD_ROUTE_PICK_VENUE) } } else null,
                onAddVenueClick = if (isStaff) { { navController.navigate(ExploreRoutes.ADD_VENUE) } } else null,
            )
        }

        composable(
            route = ExploreRoutes.ROUTE_DETAIL,
            arguments = listOf(navArgument("routeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong("routeId") ?: return@composable
            val route = routes.find { it.id == routeId }
            val stats by clubRepository.observeRouteStats(routeId).collectAsStateWithLifecycle(initialValue = null)
            val latestVersion by clubRepository.observeLatestRouteVersion(routeId).collectAsStateWithLifecycle(initialValue = null)
            val routeCompletions by clubRepository.observeRouteCompletions(routeId).collectAsStateWithLifecycle(initialValue = emptyList())
            val attempts = stats?.totalAttempts ?: 0
            val sends = stats?.totalSends ?: 0
            if (route != null) {
                RouteDetailScreen(
                    routeName = route.name,
                    vGrade = route.vGrade,
                    routeColorHex = latestVersion?.colorHex,
                    sendRatePercent = if (attempts > 0) sends * 100 / attempts else null,
                    totalAttempts = attempts,
                    totalSends = sends,
                    betaVideoAvailable = route.betaVideoUrl != null,
                    betaVideoUrl = route.betaVideoUrl,
                    completions = routeCompletions.map { RouteCompletionRow(userDisplayName = it.userDisplayName, completedAt = it.completedAt) },
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { /* no-op: real playback is now inline in RouteDetailScreen's beta card via betaVideoUrl */ },
                    onLogAttempt = { /* TODO(live-send-real): needs the full attempt-logging form (ClimbDetailsInputScreen) */ },
                    onRecordAttempt = { /* TODO(live-send-real): same as above */ },
                    showRecordFab = false,
                    onFeedTab = onGoHome,
                    onProgressTab = onProgressTab,
                    onRanksTab = onRanksTab,
                    onClubTab = onClubTab,
                    isStaff = isStaff,
                    onUploadBeta = {
                        betaUploadRouteId = routeId
                        pickBetaVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                )
            }
        }

        composable(ExploreRoutes.ADD_VENUE) {
            AddVenueForm(
                clubRepository = clubRepository,
                organizationId = organization.id,
                currentUid = currentUid,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(ExploreRoutes.ADD_ROUTE_PICK_VENUE) {
            AddRoutePickVenueStep(
                venues = venueEntities,
                onVenueChosen = { venueId -> navController.navigate(ExploreRoutes.addRoutePickZone(venueId)) },
                onGoAddVenue = {
                    // Same as Browse's own "Add Venue" entry — a fresh add-venue screen replacing
                    // the whole add-route sub-stack rather than nesting under it, matching the
                    // original state reset (addingRoute=false, addingVenue=true: the add-route
                    // flow is fully abandoned, not resumed after the venue is created).
                    navController.navigate(ExploreRoutes.ADD_VENUE) {
                        popUpTo(ExploreRoutes.BROWSE) { inclusive = false }
                    }
                },
                // Every step's Back/Cancel always fully exits the whole add-route flow back to
                // Browse — matching the original AddRouteFlow's single shared onCancel, which
                // reset all the way regardless of which step it was tapped from.
                onBack = { navController.popBackStack(ExploreRoutes.BROWSE, inclusive = false) },
            )
        }

        composable(
            route = ExploreRoutes.ADD_ROUTE_PICK_ZONE,
            arguments = listOf(navArgument("venueId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val venueId = backStackEntry.arguments?.getLong("venueId") ?: return@composable
            AddRoutePickZoneStep(
                clubRepository = clubRepository,
                organizationId = organization.id,
                currentUid = currentUid,
                venueId = venueId,
                onZoneChosen = { zoneId -> navController.navigate(ExploreRoutes.addRouteSetRoute(venueId, zoneId)) },
                onBack = { navController.popBackStack(ExploreRoutes.BROWSE, inclusive = false) },
            )
        }

        composable(
            route = ExploreRoutes.ADD_ROUTE_SET_ROUTE,
            arguments = listOf(
                navArgument("venueId") { type = NavType.LongType },
                navArgument("zoneId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getLong("zoneId") ?: return@composable
            AddRouteSetRouteStep(
                clubRepository = clubRepository,
                organizationId = organization.id,
                currentUid = currentUid,
                zoneId = zoneId,
                onCreated = { navController.popBackStack(ExploreRoutes.BROWSE, inclusive = false) },
                onBack = { navController.popBackStack(ExploreRoutes.BROWSE, inclusive = false) },
            )
        }
    }
}

/** Real, staff-only "add a venue" form — same [ClubRepository.createVenue] call and requirement
 * (name required, address optional) as the real [com.example.climb.ui.clubs.ClubsScreen]'s
 * "Add a venue" section this replaces at the staff Club Mode entry point, just styled to match
 * this package's dark/neon-lime look instead of the old Material dialog style. */
@Composable
private fun AddVenueForm(clubRepository: ClubRepository, organizationId: Long, currentUid: String, onDone: () -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
            BackLink(onClick = onCancel)
            Spacer(Modifier.height(16.dp))
            Text2("Add Venue")
            Spacer(Modifier.height(20.dp))
            LiveSendTextField(value = name, onValueChange = { name = it; errorMessage = null }, placeholder = "Venue name")
            Spacer(Modifier.height(12.dp))
            LiveSendTextField(value = address, onValueChange = { address = it }, placeholder = "Address (optional)")
            ErrorText(errorMessage)
            LiveSendPrimaryButton(
                text = "Add Venue",
                enabled = name.isNotBlank() && !saving,
                loading = saving,
                onClick = {
                    saving = true
                    errorMessage = null
                    scope.launch {
                        val result = clubRepository.createVenue(organizationId, currentUid, name, address.ifBlank { null })
                        saving = false
                        result.onSuccess { onDone() }
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Shared full-screen scaffold for the add-route flow's 3 steps — same Box/wallTexture/Column/
 * BackLink shell each step already used, factored out once so it's not repeated 3 times now that
 * each step is its own NavHost destination rather than a branch inside one composable. */
@Composable
private fun AddRouteStepScaffold(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
            BackLink(onClick = onBack)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Real, staff-only "add a route" flow's step 1 — pick which venue the new route lives under
 * (required, since a route can't exist without a zone, and a zone can't exist without a venue).
 * If the org has no venues at all yet, sends the staffer to [onGoAddVenue] instead of dead-ending. */
@Composable
private fun AddRoutePickVenueStep(venues: List<VenueEntity>, onVenueChosen: (Long) -> Unit, onGoAddVenue: () -> Unit, onBack: () -> Unit) {
    AddRouteStepScaffold(onBack = onBack) {
        Text2("Choose a Venue")
        Spacer(Modifier.height(16.dp))
        if (venues.isEmpty()) {
            Text(
                text = "No venues yet — add one first, then come back to set a route.",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            LiveSendPrimaryButton(text = "Add Venue", onClick = onGoAddVenue, height = 44)
        } else {
            venues.forEachIndexed { index, venue ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                LiveSendCard(cornerRadius = 14, padding = 14, onClick = { onVenueChosen(venue.id) }) {
                    Text(text = venue.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Step 2 — pick an existing zone in that venue, or create one inline ([ClubRepository.createZone]). */
@Composable
private fun AddRoutePickZoneStep(
    clubRepository: ClubRepository,
    organizationId: Long,
    currentUid: String,
    venueId: Long,
    onZoneChosen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    AddRouteStepScaffold(onBack = onBack) {
        val zones by clubRepository.observeZonesForVenue(venueId).collectAsStateWithLifecycle(initialValue = emptyList<ZoneEntity>())
        Text2("Choose a Zone")
        Spacer(Modifier.height(16.dp))
        if (zones.isNotEmpty()) {
            LiveSendSectionLabel(text = "Existing zones")
            Spacer(Modifier.height(10.dp))
            zones.forEachIndexed { index, zone ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                LiveSendCard(cornerRadius = 14, padding = 14, onClick = { onZoneChosen(zone.id) }) {
                    Text(text = zone.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        NewZoneForm(clubRepository = clubRepository, organizationId = organizationId, currentUid = currentUid, venueId = venueId, onCreated = onZoneChosen)
    }
}

/** Step 3 — the route form itself ([ClubRepository.createRoute]). */
@Composable
private fun AddRouteSetRouteStep(
    clubRepository: ClubRepository,
    organizationId: Long,
    currentUid: String,
    zoneId: Long,
    onCreated: () -> Unit,
    onBack: () -> Unit,
) {
    AddRouteStepScaffold(onBack = onBack) {
        NewRouteForm(clubRepository = clubRepository, organizationId = organizationId, currentUid = currentUid, zoneId = zoneId, onCreated = onCreated)
    }
}

@Composable
private fun NewZoneForm(clubRepository: ClubRepository, organizationId: Long, currentUid: String, venueId: Long, onCreated: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LiveSendSectionLabel(text = "Or create a new zone")
    Spacer(Modifier.height(10.dp))
    LiveSendTextField(value = name, onValueChange = { name = it; errorMessage = null }, placeholder = "Zone name")
    ErrorText(errorMessage)
    LiveSendPrimaryButton(
        text = "Create Zone",
        enabled = name.isNotBlank() && !saving,
        loading = saving,
        onClick = {
            saving = true
            errorMessage = null
            scope.launch {
                val result = clubRepository.createZone(organizationId, currentUid, venueId, name)
                saving = false
                result.onSuccess { onCreated(it.id) }
                result.onFailure { errorMessage = it.message ?: "Something went wrong" }
            }
        },
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun NewRouteForm(clubRepository: ClubRepository, organizationId: Long, currentUid: String, zoneId: Long, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    // Optional — a route's color is optional on the real RouteVersionEntity (colorHex: Long?), so
    // this can stay unset, matching that.
    var selectedColor by remember { mutableStateOf<RouteColor?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text2("Set a Route")
    Spacer(Modifier.height(20.dp))
    LiveSendTextField(value = name, onValueChange = { name = it; errorMessage = null }, placeholder = "Route name")
    Spacer(Modifier.height(12.dp))
    LiveSendTextField(value = grade, onValueChange = { grade = it.filter { c -> c.isDigit() } }, placeholder = "V-grade (optional)")
    Spacer(Modifier.height(16.dp))
    LiveSendSectionLabel(text = "Color (optional)")
    Spacer(Modifier.height(10.dp))
    RouteColorPicker(selected = selectedColor, onSelect = { selectedColor = if (selectedColor == it) null else it })
    ErrorText(errorMessage)
    LiveSendPrimaryButton(
        text = "Set Route",
        enabled = name.isNotBlank() && !saving,
        loading = saving,
        onClick = {
            saving = true
            errorMessage = null
            scope.launch {
                val result = clubRepository.createRoute(organizationId, currentUid, zoneId, name, grade.toIntOrNull(), selectedColor?.hex)
                saving = false
                result.onSuccess { onCreated() }
                result.onFailure { errorMessage = it.message ?: "Something went wrong" }
            }
        },
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** Real gym tape colors — mirrors [RouteColor], the same enum the shipped app's own local climb
 * log already uses to color-code routes, rather than inventing a separate swatch set. */
@Composable
private fun RouteColorPicker(selected: RouteColor?, onSelect: (RouteColor) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RouteColor.entries.forEach { color ->
            val isSelected = selected == color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color.hex))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, CircleShape)
                    .clickable(onClick = { onSelect(color) })
                    .semantics {
                        role = Role.Button
                        contentDescription = "${color.name}${if (isSelected) ", selected" else ""}"
                    },
            )
        }
    }
}

@Composable
private fun BackLink(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "Back" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = "← Back", color = ClimbPalette.liveSendTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun Text2(text: String) {
    Text(text = text, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(text = message, color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
