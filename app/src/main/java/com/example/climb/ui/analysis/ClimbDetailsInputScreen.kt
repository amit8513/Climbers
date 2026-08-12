package com.example.climb.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.analysis.PoseAnalysisWorker
import com.example.climb.analysis.Visibility
import com.example.climb.analysis.WallType
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.RouteContext
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

@Composable
fun ClimbDetailsInputScreen(
    videoPath: String,
    durationMs: Long,
    currentUid: String,
    currentUsername: String,
    sourceClimbId: Long?,
    analysisRepository: AnalysisRepository,
    clubRepository: ClubRepository,
    onAnalyzeStarted: (attemptId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vGrade by remember { mutableStateOf<Int?>(null) }
    var wallType by remember { mutableStateOf(WallType.UNKNOWN) }
    var attemptNumber by remember { mutableStateOf("1") }
    var completed by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var routeName by remember { mutableStateOf("") }
    var gymName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.PRIVATE) }
    var saving by remember { mutableStateOf(false) }
    // Entirely optional — stays null for anyone who never touches the picker below, which is
    // exactly the "outdoor/no-gym" state the analysis pipeline already treats as normal.
    var routeContext by remember { mutableStateOf<RouteContext?>(null) }

    val attemptNumberInt = attemptNumber.toIntOrNull()
    val flashAllowed = attemptNumberInt == 1 && completed

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Climb details",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 16.dp),
            )

            FieldLabel("V grade")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0..17).toList()) { grade ->
                    FilterChip(
                        selected = vGrade == grade,
                        onClick = { vGrade = if (vGrade == grade) null else grade },
                        label = { Text("V$grade") },
                    )
                }
            }

            FieldLabel("Wall type")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WallType.entries.toList()) { type ->
                    FilterChip(
                        selected = wallType == type,
                        onClick = { wallType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            FieldLabel("Attempt number")
            OutlinedTextField(
                value = attemptNumber,
                onValueChange = { attemptNumber = it.filter { c -> c.isDigit() } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.4f),
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Sent this climb", color = ClimbPalette.textPrimary, fontSize = 14.sp)
                Switch(
                    checked = completed,
                    onCheckedChange = { completed = it; if (!it) flash = false },
                    colors = SwitchDefaults.colors(checkedTrackColor = ClimbPalette.sent),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Flash", color = if (flashAllowed) ClimbPalette.textPrimary else ClimbPalette.textMuted, fontSize = 14.sp)
                    if (!flashAllowed) {
                        Text("Only on attempt 1 of a sent climb", color = ClimbPalette.textMuted, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = flash && flashAllowed,
                    onCheckedChange = { flash = it },
                    enabled = flashAllowed,
                    colors = SwitchDefaults.colors(checkedTrackColor = ClimbPalette.sent),
                )
            }

            FieldLabel("Route name (optional)")
            OutlinedTextField(value = routeName, onValueChange = { routeName = it; routeContext = null }, singleLine = true, modifier = Modifier.fillMaxWidth())

            FieldLabel("Link to a gym route (optional)")
            GymRoutePicker(
                currentUid = currentUid,
                clubRepository = clubRepository,
                selected = routeContext,
                onSelected = { context ->
                    routeContext = context
                    if (context != null) {
                        routeName = context.routeName
                        vGrade = context.vGrade ?: vGrade
                    }
                },
            )

            FieldLabel("Gym / location (optional)")
            OutlinedTextField(value = gymName, onValueChange = { gymName = it }, singleLine = true, modifier = Modifier.fillMaxWidth())

            FieldLabel("Notes (optional)")
            OutlinedTextField(value = notes, onValueChange = { notes = it }, minLines = 2, modifier = Modifier.fillMaxWidth())

            FieldLabel("Who can see this")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Visibility.entries.toList()) { option ->
                    FilterChip(
                        selected = visibility == option,
                        onClick = { visibility = option },
                        label = { Text(option.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                enabled = !saving && attemptNumberInt != null && attemptNumberInt > 0,
                onClick = {
                    val validAttemptNumber = attemptNumberInt ?: return@Button
                    saving = true
                    scope.launch {
                        val attemptId = analysisRepository.createAttempt(
                            ClimbAttemptEntity(
                                userId = currentUid,
                                sourceClimbId = sourceClimbId,
                                videoPath = videoPath,
                                createdAt = System.currentTimeMillis(),
                                durationMs = durationMs,
                                vGrade = vGrade,
                                wallType = wallType,
                                attemptNumber = validAttemptNumber,
                                completed = completed,
                                flash = flash && flashAllowed,
                                routeName = routeName.ifBlank { null },
                                gymName = gymName.ifBlank { null },
                                notes = notes,
                                visibility = visibility,
                                organizationId = routeContext?.organizationId,
                                venueId = routeContext?.venueId,
                                zoneId = routeContext?.zoneId,
                                routeId = routeContext?.routeId,
                                routeVersionId = routeContext?.routeVersionId,
                            ),
                        )
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            PoseAnalysisWorker.uniqueWorkName(attemptId),
                            ExistingWorkPolicy.KEEP,
                            PoseAnalysisWorker.buildRequest(attemptId),
                        )
                        routeContext?.let { route ->
                            clubRepository.recordClubAttempt(route.organizationId, currentUid, currentUsername, vGrade, completed)
                            clubRepository.recordRouteAttempt(route.routeId, route.organizationId, completed)
                            if (completed) {
                                clubRepository.recordRouteCompletion(route.routeId, route.organizationId, currentUid, currentUsername)
                            }
                        }
                        saving = false
                        onAnalyzeStarted(attemptId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Starting…" else "Analyze climb")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Progressive org → venue → zone → route chip picker, scoped to gyms the current user has
 * actually joined (you can only link a climb to a route at a gym you're a member of). Shows
 * nothing but a one-line hint when the user belongs to zero organizations — the common case for
 * a normal/outdoor climber — so this never adds visual weight to that flow.
 */
@Composable
private fun GymRoutePicker(
    currentUid: String,
    clubRepository: ClubRepository,
    selected: RouteContext?,
    onSelected: (RouteContext?) -> Unit,
) {
    val memberships by clubRepository.observeMembershipsForUser(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    if (memberships.isEmpty()) {
        Text(
            text = "Join a gym in Settings → Clubs to link climbs to real routes.",
            color = ClimbPalette.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        return
    }

    val organizations by clubRepository.observeAllOrganizations().collectAsStateWithLifecycle(initialValue = emptyList())
    val myOrgIds = remember(memberships) { memberships.map { it.organizationId }.toSet() }
    val myOrganizations = remember(organizations, myOrgIds) { organizations.filter { it.id in myOrgIds } }

    var selectedOrgId by remember { mutableStateOf(selected?.organizationId) }
    var selectedVenueId by remember { mutableStateOf(selected?.venueId) }
    var selectedZoneId by remember { mutableStateOf(selected?.zoneId) }
    val scope = rememberCoroutineScope()

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(myOrganizations) { org ->
            FilterChip(
                selected = selectedOrgId == org.id,
                onClick = {
                    if (selectedOrgId == org.id) {
                        selectedOrgId = null
                    } else {
                        selectedOrgId = org.id
                        selectedVenueId = null
                        selectedZoneId = null
                    }
                    onSelected(null)
                },
                label = { Text(org.name) },
            )
        }
    }

    val orgId = selectedOrgId
    if (orgId != null) {
        val venues by clubRepository.observeVenuesForOrganization(orgId).collectAsStateWithLifecycle(initialValue = emptyList())
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(venues) { venue ->
                FilterChip(
                    selected = selectedVenueId == venue.id,
                    onClick = {
                        if (selectedVenueId == venue.id) {
                            selectedVenueId = null
                        } else {
                            selectedVenueId = venue.id
                            selectedZoneId = null
                        }
                        onSelected(null)
                    },
                    label = { Text(venue.name) },
                )
            }
        }

        val venueId = selectedVenueId
        if (venueId != null) {
            val zones by clubRepository.observeZonesForVenue(venueId).collectAsStateWithLifecycle(initialValue = emptyList())
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(zones) { zone ->
                    FilterChip(
                        selected = selectedZoneId == zone.id,
                        onClick = {
                            selectedZoneId = if (selectedZoneId == zone.id) null else zone.id
                            onSelected(null)
                        },
                        label = { Text(zone.name) },
                    )
                }
            }

            val zoneId = selectedZoneId
            if (zoneId != null) {
                val routes by clubRepository.observeActiveRoutesForZone(zoneId).collectAsStateWithLifecycle(initialValue = emptyList())
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(routes) { route ->
                        FilterChip(
                            selected = selected?.routeId == route.id,
                            onClick = {
                                scope.launch {
                                    onSelected(clubRepository.buildRouteContext(orgId, venueId, zoneId, route))
                                }
                            },
                            label = { Text("${route.name}${route.vGrade?.let { " V$it" } ?: ""}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = ClimbPalette.textMuted,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}
