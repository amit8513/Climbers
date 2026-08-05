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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.climb.analysis.AnalysisStatus
import com.example.climb.analysis.PoseAnalysisWorker
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private val PHASES = listOf(
    AnalysisStatus.PREPARING to "Preparing video",
    AnalysisStatus.EXTRACTING_FRAMES to "Reading frames",
    AnalysisStatus.ESTIMATING_POSE to "Tracking body position",
    AnalysisStatus.CALCULATING_METRICS to "Calculating movement",
    AnalysisStatus.GENERATING_TIPS to "Generating feedback",
    AnalysisStatus.SAVING to "Saving results",
)

@Composable
fun AnalysisProgressScreen(
    attemptId: Long,
    onComplete: (analysisId: Long) -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val uniqueWorkName = remember(attemptId) { PoseAnalysisWorker.uniqueWorkName(attemptId) }

    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val workInfo = workInfos.firstOrNull()

    val currentStatus = workInfo?.progress?.getString(PoseAnalysisWorker.KEY_PHASE)
        ?.let { runCatching { AnalysisStatus.valueOf(it) }.getOrNull() }
    val fraction = workInfo?.progress?.getFloat(PoseAnalysisWorker.KEY_FRACTION, 0f) ?: 0f

    LaunchedEffect(workInfo?.state, workInfo?.outputData) {
        if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
            val analysisId = workInfo.outputData.getLong(PoseAnalysisWorker.KEY_ANALYSIS_ID, -1L)
            if (analysisId > 0L) onComplete(analysisId)
        }
    }

    val failureReason = if (workInfo?.state == WorkInfo.State.FAILED) {
        workInfo.outputData.getString(PoseAnalysisWorker.KEY_FAILURE_REASON) ?: "Analysis failed"
    } else {
        null
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Analyzing your climb",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                text = "You can leave this screen — the analysis keeps running.",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            if (failureReason != null) {
                Text(text = failureReason, color = ClimbPalette.fell, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onGiveUp, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            } else {
                PHASES.forEach { (status, label) ->
                    PhaseRow(label = label, state = phaseState(status, currentStatus))
                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = ClimbPalette.chalk,
                    trackColor = ClimbPalette.border,
                )

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        workManager.cancelUniqueWork(uniqueWorkName)
                        onGiveUp()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private enum class PhaseState { DONE, ACTIVE, UPCOMING }

private fun phaseState(status: AnalysisStatus, current: AnalysisStatus?): PhaseState {
    if (current == null) return PhaseState.UPCOMING
    val order = PHASES.map { it.first }
    val currentIndex = order.indexOf(current)
    val thisIndex = order.indexOf(status)
    return when {
        current == AnalysisStatus.COMPLETE || current == AnalysisStatus.SAVING && status != AnalysisStatus.SAVING -> PhaseState.DONE
        thisIndex < currentIndex -> PhaseState.DONE
        thisIndex == currentIndex -> PhaseState.ACTIVE
        else -> PhaseState.UPCOMING
    }
}

@Composable
private fun PhaseRow(label: String, state: PhaseState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            PhaseState.ACTIVE -> CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp, color = ClimbPalette.chalk)
            PhaseState.DONE -> Text("✓", color = ClimbPalette.sent, fontWeight = FontWeight.Bold)
            PhaseState.UPCOMING -> Box(modifier = Modifier.height(16.dp))
        }
        Text(
            text = label,
            color = if (state == PhaseState.UPCOMING) ClimbPalette.textMuted else ClimbPalette.textPrimary,
            fontSize = 14.sp,
        )
    }
}
