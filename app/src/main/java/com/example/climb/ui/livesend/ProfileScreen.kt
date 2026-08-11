package com.example.climb.ui.livesend

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendSegmentedToggle
import com.example.climb.ui.livesend.components.LiveSendThemeSwatch
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/** The 3 appearance swatches this Live Send concept screen previews. These are the concept's own
 * named looks ("Live Send" / "Chalk Stone" / "Volcanic") — deliberately fixed, literal preview
 * colors straight from the Figma spec (like [ClimbPalette.sent]/[ClimbPalette.liveSendCta]), not
 * the currently-active [com.example.climb.data.settings.ClimbThemeOption], since a swatch must
 * keep previewing its own look regardless of which theme is applied right now. */
private val AppearanceSwatchColors = listOf(
    Color(0xFF0B0E10), // Live Send
    Color(0xFF161B1F), // Chalk Stone
    Color(0xFF3A1E14), // Volcanic
)
private val AppearanceSwatchNames = listOf("Live Send", "Chalk Stone", "Volcanic")

/**
 * "Live Send" (Alternative UI Concept 2) — ProfileScreen (Figma node 5:310), the Settings screen:
 * a profile card (avatar + name + change-photo prompt), an App Mode Personal/Club toggle, an
 * Appearance theme-swatch picker, and — since this member also helps run a club — a card inviting
 * them into Club Mode.
 *
 * This is a self-contained design-exploration screen: it owns the toggle/swatch selection as
 * local UI state and simply reports selections upward via callbacks, the same way every other
 * leaf screen in this codebase hands navigation/state-changing intent to its caller instead of
 * reaching into a ViewModel or nav controller directly.
 */
@Composable
fun ProfileScreen(
    userName: String = "Amit",
    userPhotoUrl: String? = null,
    clubName: String = "Golomb Club",
    initialAppModeIndex: Int = 0,
    initialAppearanceIndex: Int = 0,
    onBack: () -> Unit,
    onChangePhoto: () -> Unit,
    onAppModeSelected: (Int) -> Unit,
    onAppearanceSelected: (Int) -> Unit,
    onEnterClubMode: () -> Unit,
) {
    var appModeIndex by remember { mutableStateOf(initialAppModeIndex) }
    var appearanceIndex by remember { mutableStateOf(initialAppearanceIndex) }

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .wrapContentWidth()
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .semantics { contentDescription = "Back" }
                    .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Settings",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ProfileCard
            LiveSendCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LiveSendAvatar(
                        initial = userName,
                        size = 80,
                        photoUrl = userPhotoUrl,
                        ringed = true,
                        modifier = Modifier
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Change profile photo",
                                onClick = onChangePhoto,
                            )
                            .semantics { contentDescription = "Change profile photo" },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = userName,
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap to change photo",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ModeCard
            LiveSendCard {
                LiveSendSectionLabel(text = "App Mode")
                Spacer(modifier = Modifier.height(12.dp))
                LiveSendSegmentedToggle(
                    options = listOf("Personal", "Club"),
                    selectedIndex = appModeIndex,
                    onSelect = { index ->
                        appModeIndex = index
                        onAppModeSelected(index)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ThemeCard
            LiveSendCard {
                LiveSendSectionLabel(text = "Appearance")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppearanceSwatchNames.forEachIndexed { index, name ->
                        LiveSendThemeSwatch(
                            name = name,
                            previewColor = AppearanceSwatchColors[index],
                            selected = index == appearanceIndex,
                            onClick = {
                                appearanceIndex = index
                                onAppearanceSelected(index)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Appearance: $name"
                                role = Role.Button
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ClubCard
            LiveSendCard {
                Text(
                    text = "You help run $clubName",
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LiveSendPrimaryButton(
                    text = "Enter Club Mode →",
                    onClick = onEnterClubMode,
                    height = 44,
                    modifier = Modifier.semantics { contentDescription = "Enter Club Mode" },
                )
            }
        }
    }
}
