package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Shown right after sign-in, but only to a user who is STAFF/ADMIN somewhere — a normal user with
 * zero such memberships never sees this and lands straight on Home exactly as before this screen
 * existed. This is the app's only "sign in as a club" surface: there's no separate club
 * credential, just a mode switch for organizations this same account already helps run.
 */
@Composable
fun ClubModeSwitchScreen(
    staffOrganizations: List<OrganizationEntity>,
    onContinueAsSelf: () -> Unit,
    onContinueAsClub: (OrganizationEntity) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().wallTexture(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            Text(
                text = "Choose how to continue",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            Text(
                text = "You help run a club — pick which way to use the app right now.",
                color = ClimbPalette.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            )

            Button(
                onClick = onContinueAsSelf,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Continue as yourself") }

            Spacer(Modifier.height(20.dp))

            Column {
                staffOrganizations.forEachIndexed { index, organization ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ClimbPalette.surfaceRaised)
                            .border(1.dp, ClimbPalette.borderStrong, RoundedCornerShape(14.dp))
                            .clickable { onContinueAsClub(organization) }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "Continue as \"${organization.name}\" (Club)",
                            color = ClimbPalette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
