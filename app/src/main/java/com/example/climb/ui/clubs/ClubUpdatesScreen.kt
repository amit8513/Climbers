package com.example.climb.ui.clubs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.ClubUpdateEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The "Updates" tab — staff-posted announcements, read by every member. Shared between Club Mode
 * (staff, [isStaff] = true — the post form shows) and the member club view (read-only).
 * [ClubRepository.postUpdate] re-checks staff access server-side regardless of [isStaff].
 */
@Composable
fun ClubUpdatesScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    modifier: Modifier = Modifier,
) {
    val updates by clubRepository.observeUpdatesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "Updates",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            if (isStaff) {
                SectionCard(title = "Post an update") {
                    var text by remember { mutableStateOf("") }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(value = text, onValueChange = { text = it; errorMessage = null }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    errorMessage?.let { Text(text = it, color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Button(
                        enabled = text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        onClick = {
                            scope.launch {
                                val result = clubRepository.postUpdate(organization.id, currentUid, text)
                                result.onSuccess { text = "" }
                                result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                            }
                        },
                    ) { Text("Post") }
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionCard(title = "Recent") {
                if (updates.isEmpty()) {
                    Text(text = "No updates yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
                } else {
                    Column {
                        updates.forEachIndexed { index, update ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            UpdateRow(update)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)

@Composable
private fun UpdateRow(update: ClubUpdateEntity) {
    Column {
        Text(text = update.text, color = ClimbPalette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
        Text(text = dateFormat.format(Date(update.createdAt)), color = ClimbPalette.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
