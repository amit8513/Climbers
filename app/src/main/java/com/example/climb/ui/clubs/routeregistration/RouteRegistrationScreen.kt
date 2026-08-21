package com.example.climb.ui.clubs.routeregistration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.HoldRole
import com.example.climb.clubs.StartPolicy
import com.example.climb.clubs.WallEntity
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.ReviewedHold
import com.example.climb.data.RouteColor
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import androidx.compose.foundation.Canvas as ComposeCanvas

private object RouteRegistrationRoutes {
    const val WALL_SELECTION = "wall_selection"
    const val REFERENCE_FRAME = "reference_frame"
    const val ROI_ANNOTATION = "roi_annotation"
    const val COLOR_AND_GRADE = "color_and_grade"
    const val START_HOLD = "start_hold"
    const val FINISH_HOLD = "finish_hold"
    const val HOLD_REVIEW = "hold_review"
    const val SUMMARY = "summary"
}

/**
 * Phase 2A wall-camera route-registration wizard — hardware-independent: reference frames and
 * detected holds come only from [RouteRegistrationFixtures], never a real Edge Capture Agent or
 * NFC/camera hardware. Its own small nested nav graph, per docs/ROUTE_ATTRIBUTION_PLAN.md §13,
 * rather than expanding [com.example.climb.ui.livesend.real.LiveSendClubExploreHost] inline.
 * Never activates anything it creates — see [RouteRegistrationViewModel.saveDraft]'s doc comment.
 */
@Composable
fun RouteRegistrationScreen(organizationId: Long, setterUserId: String, onExit: () -> Unit) {
    val viewModel: RouteRegistrationViewModel = viewModel(factory = RouteRegistrationViewModel.factory(organizationId, setterUserId))
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = RouteRegistrationRoutes.WALL_SELECTION) {
        composable(RouteRegistrationRoutes.WALL_SELECTION) {
            WallSelectionStep(
                walls = viewModel.availableWalls,
                onWallChosen = { wall -> viewModel.selectWall(wall); navController.navigate(RouteRegistrationRoutes.REFERENCE_FRAME) },
                onBack = onExit,
            )
        }
        composable(RouteRegistrationRoutes.REFERENCE_FRAME) {
            val state by viewModel.state.collectAsState()
            ReferenceFrameStep(
                hasFrame = state.capturedFrame != null,
                onRequestFrame = { viewModel.requestReferenceFrame() },
                onNext = { navController.navigate(RouteRegistrationRoutes.ROI_ANNOTATION) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(RouteRegistrationRoutes.ROI_ANNOTATION) {
            val state by viewModel.state.collectAsState()
            RoiAnnotationStep(
                roi = state.wallRoiNormalized ?: NormalizedRect(0.1f, 0.1f, 0.9f, 0.95f),
                onRoiChanged = { viewModel.updateWallRoi(it) },
                onNext = { navController.navigate(RouteRegistrationRoutes.COLOR_AND_GRADE) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(RouteRegistrationRoutes.COLOR_AND_GRADE) {
            val state by viewModel.state.collectAsState()
            ColorAndGradeStep(
                selectedColorHex = state.candidateColorHex,
                grade = state.grade,
                publicNumberOrName = state.publicNumberOrName,
                startPolicy = state.startPolicy,
                finishPolicy = state.finishPolicy,
                conflict = viewModel.currentColorConflict(),
                onColorSelected = { viewModel.selectColor(it) },
                onGradeChanged = { viewModel.updateGrade(it) },
                onPublicNumberOrNameChanged = { viewModel.updatePublicNumberOrName(it) },
                onStartPolicyChanged = { viewModel.updateStartPolicy(it) },
                onFinishPolicyChanged = { viewModel.updateFinishPolicy(it) },
                onNext = { navController.navigate(RouteRegistrationRoutes.START_HOLD) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(RouteRegistrationRoutes.START_HOLD) {
            val state by viewModel.state.collectAsState()
            HoldTapStep(
                title = "Tap the Start Hold",
                holds = state.holds,
                roi = state.wallRoiNormalized,
                onHoldTap = { hold -> viewModel.selectStartHold(hold.id) },
                onNext = { navController.navigate(RouteRegistrationRoutes.FINISH_HOLD) },
                onBack = { navController.popBackStack() },
                nextEnabled = state.startHold != null,
            )
        }
        composable(RouteRegistrationRoutes.FINISH_HOLD) {
            val state by viewModel.state.collectAsState()
            HoldTapStep(
                title = "Tap the Finish Hold",
                holds = state.holds,
                roi = state.wallRoiNormalized,
                onHoldTap = { hold -> viewModel.selectFinishHold(hold.id) },
                onNext = { navController.navigate(RouteRegistrationRoutes.HOLD_REVIEW) },
                onBack = { navController.popBackStack() },
                nextEnabled = state.finishHold != null,
            )
        }
        composable(RouteRegistrationRoutes.HOLD_REVIEW) {
            val state by viewModel.state.collectAsState()
            HoldReviewStep(
                holds = state.holds,
                onCycleRole = { hold -> viewModel.setHoldRole(hold.id, nextRole(hold.role)) },
                onRemoveHold = { hold -> viewModel.removeHold(hold.id) },
                onNext = { navController.navigate(RouteRegistrationRoutes.SUMMARY) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(RouteRegistrationRoutes.SUMMARY) {
            SummaryStep(
                buildAndValidate = { viewModel.buildAndValidateDraft() },
                onSaveDraft = { result -> viewModel.saveDraft(result) },
                onDone = onExit,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun nextRole(current: HoldRole): HoldRole = when (current) {
    HoldRole.BODY -> HoldRole.START
    HoldRole.START -> HoldRole.FINISH
    HoldRole.FINISH -> HoldRole.BODY
}

@Composable
private fun RegistrationStepScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            Text(text = title, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun WallSelectionStep(walls: List<WallEntity>, onWallChosen: (WallEntity) -> Unit, onBack: () -> Unit) {
    RegistrationStepScaffold("Select a Wall", onBack) {
        walls.forEachIndexed { index, wall ->
            if (index > 0) androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            LiveSendCard(onClick = { onWallChosen(wall) }) {
                Text(text = wall.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ReferenceFrameStep(hasFrame: Boolean, onRequestFrame: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    RegistrationStepScaffold("Reference Frame", onBack) {
        Text(
            text = "Request the authoritative wall reference. This is a TEST FIXTURE, not a real capture — " +
                "no Edge Capture Agent/camera hardware is used in this phase.",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 13.sp,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        ReferenceFrameCanvas(holds = emptyList(), roi = null, modifier = Modifier.fillMaxWidth().height(220.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        LiveSendPrimaryButton(text = if (hasFrame) "Re-request Reference Frame" else "Request Reference Frame", onClick = onRequestFrame)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        LiveSendPrimaryButton(text = "Next", onClick = onNext, enabled = hasFrame, containerColor = ClimbPalette.liveSendInfo)
    }
}

@Composable
private fun RoiAnnotationStep(roi: NormalizedRect, onRoiChanged: (NormalizedRect) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    RegistrationStepScaffold("Wall ROI Annotation", onBack) {
        ReferenceFrameCanvas(holds = emptyList(), roi = roi, modifier = Modifier.fillMaxWidth().height(220.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        RoiSlider("Left", roi.left) { onRoiChanged(roi.copy(left = it)) }
        RoiSlider("Top", roi.top) { onRoiChanged(roi.copy(top = it)) }
        RoiSlider("Right", roi.right) { onRoiChanged(roi.copy(right = it)) }
        RoiSlider("Bottom", roi.bottom) { onRoiChanged(roi.copy(bottom = it)) }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        LiveSendPrimaryButton(text = "Next", onClick = onNext)
    }
}

@Composable
private fun RoiSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Text(text = "$label: ${(value * 100).toInt()}%", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp)
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
}

@Composable
private fun ColorAndGradeStep(
    selectedColorHex: Long?,
    grade: Int?,
    publicNumberOrName: String?,
    startPolicy: StartPolicy?,
    finishPolicy: FinishPolicy?,
    conflict: com.example.climb.clubs.RouteColorConflictChecker.ConflictCheckResult,
    onColorSelected: (RouteColor) -> Unit,
    onGradeChanged: (Int?) -> Unit,
    onPublicNumberOrNameChanged: (String?) -> Unit,
    onStartPolicyChanged: (StartPolicy?) -> Unit,
    onFinishPolicyChanged: (FinishPolicy?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    RegistrationStepScaffold("Color, Grade & Policies", onBack) {
        Text(text = "Route Color", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteColor.entries.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color.hex))
                        .then(
                            if (color.hex == selectedColorHex) Modifier.border(2.dp, Color.White, CircleShape) else Modifier,
                        )
                        .clickable { onColorSelected(color) },
                )
            }
        }
        if (conflict.hasConflict) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                text = "This color is too close to an existing active route on this wall - blocked.",
                color = ClimbPalette.liveSendCta,
                fontSize = 12.sp,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        Text(text = "Grade (V-scale)", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        LiveSendTextField(
            value = grade?.toString() ?: "",
            onValueChange = { onGradeChanged(it.toIntOrNull()) },
            placeholder = "e.g. 5",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        Text(text = "Public Number/Name (optional)", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        LiveSendTextField(
            value = publicNumberOrName.orEmpty(),
            onValueChange = { onPublicNumberOrNameChanged(it) },
            placeholder = "Optional",
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        Text(text = "Start Policy", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        ChipRow(options = StartPolicy.entries, selected = startPolicy, label = { it.name }, onSelected = onStartPolicyChanged)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        Text(text = "Finish Policy", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        ChipRow(options = FinishPolicy.entries, selected = finishPolicy, label = { it.name }, onSelected = onFinishPolicyChanged)
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        LiveSendPrimaryButton(text = "Next", onClick = onNext, enabled = selectedColorHex != null && grade != null && !conflict.hasConflict)
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T?, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (isSelected) ClimbPalette.liveSendInfo else ClimbPalette.liveSendSurfaceRaised)
                    .clickable { onSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = label(option), color = ClimbPalette.liveSendTextPrimary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HoldTapStep(
    title: String,
    holds: List<ReviewedHold>,
    roi: NormalizedRect?,
    onHoldTap: (ReviewedHold) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    nextEnabled: Boolean,
) {
    RegistrationStepScaffold(title, onBack) {
        ReferenceFrameCanvas(holds = holds, roi = roi, onHoldTap = onHoldTap, modifier = Modifier.fillMaxWidth().height(320.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        LiveSendPrimaryButton(text = "Next", onClick = onNext, enabled = nextEnabled)
    }
}

@Composable
private fun HoldReviewStep(
    holds: List<ReviewedHold>,
    onCycleRole: (ReviewedHold) -> Unit,
    onRemoveHold: (ReviewedHold) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    RegistrationStepScaffold("Review Detected Holds", onBack) {
        Text(
            text = "Tap a hold to cycle its role (Body → Start → Finish → Body). Long-tap not supported in this " +
                "phase — use the button below to remove a false-positive.",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 12.sp,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        holds.forEach { hold ->
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            LiveSendCard(onClick = { onCycleRole(hold) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Hold #${hold.id} — ${hold.role.name}", color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp)
                    Text(
                        text = "Remove",
                        color = ClimbPalette.liveSendCta,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onRemoveHold(hold) },
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        LiveSendPrimaryButton(text = "Next", onClick = onNext)
    }
}

@Composable
private fun SummaryStep(
    buildAndValidate: () -> Pair<RouteRegistrationDraftResult, RouteRegistrationValidationSummary>?,
    onSaveDraft: (RouteRegistrationDraftResult) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val built = buildAndValidate()
    RegistrationStepScaffold("Save Draft", onBack) {
        if (built == null) {
            Text(text = "Missing wall or reference frame — go back and complete those steps first.", color = ClimbPalette.liveSendCta, fontSize = 13.sp)
            return@RegistrationStepScaffold
        }
        val (result, summary) = built
        Text(text = "Status: DRAFT (never active, never eligible for attribution)", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        if (summary.snapshotValidation.missingFields.isNotEmpty()) {
            Text(text = "Missing: ${summary.snapshotValidation.missingFields.joinToString(", ")}", color = ClimbPalette.liveSendCta, fontSize = 13.sp)
        }
        if (summary.colorConflict.hasConflict) {
            Text(text = "Color conflict — blocked.", color = ClimbPalette.liveSendCta, fontSize = 13.sp)
        }
        if (summary.canSaveDraft) {
            Text(text = "Ready to save as a draft.", color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        LiveSendPrimaryButton(
            text = "Save Draft",
            enabled = summary.canSaveDraft,
            onClick = { onSaveDraft(result); onDone() },
        )
    }
}

/** Shared "authoritative reference" visualization for every step from [ROI_ANNOTATION] onward —
 * a placeholder wall panel (no real image decode in this phase, see [RouteRegistrationFixtures])
 * with the ROI overlay and hold markers drawn/positioned on top of it. */
@Composable
private fun ReferenceFrameCanvas(
    holds: List<ReviewedHold>,
    roi: NormalizedRect?,
    onHoldTap: ((ReviewedHold) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A))) {
        ComposeCanvas(Modifier.fillMaxSize()) {
            if (roi != null) {
                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(roi.left * size.width, roi.top * size.height),
                    size = Size((roi.right - roi.left) * size.width, (roi.bottom - roi.top) * size.height),
                    style = Stroke(width = 3f),
                )
            }
        }
        holds.forEach { hold ->
            val color = when (hold.role) {
                HoldRole.START -> Color(0xFF43A047)
                HoldRole.FINISH -> Color(0xFFE53935)
                HoldRole.BODY -> Color.White
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = maxWidth * hold.centroidNormalized.x - 12.dp,
                        top = maxHeight * hold.centroidNormalized.y - 12.dp,
                    )
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (onHoldTap != null) Modifier.clickable { onHoldTap(hold) } else Modifier),
            )
        }
    }
}
