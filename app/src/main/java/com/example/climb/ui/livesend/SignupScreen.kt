package com.example.climb.ui.livesend

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.LiveSendHeroImage
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendSegmentedToggle
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Live Send's Signup screen (Figma node 5:303) — a hero photo with an overlaid back affordance
 * and "Join the club" headline, three filled fields (name/email/password), the "I'm joining as"
 * Climber-vs-Gym/Club role toggle, the red "Create Account" CTA, and a "Already have an account?
 * Log in" footer link. No bundled photo asset ships with this design exploration package, so the
 * hero uses a themed gradient placeholder in [LiveSendHeroImage]'s image slot rather than a real
 * photo — swap in a real `AsyncImage`/`Image` there once an asset pipeline exists.
 */
@Composable
fun SignupScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onCreateAccount: (name: String, email: String, password: String, joiningAsGym: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var joiningIndex by remember { mutableStateOf(0) } // 0 = Climber, 1 = Gym / Club

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LiveSendHeroImage(height = 220) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ClimbPalette.liveSendSurfaceRaised, ClimbPalette.liveSendBg),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, top = 20.dp)
                        .size(44.dp)
                        .clickable(onClick = onBack)
                        .semantics {
                            contentDescription = "Back"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Join the club",
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Track sends, film beta, join your gym.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LiveSendTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Full name",
                )
                LiveSendTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    keyboardType = KeyboardType.Email,
                )
                LiveSendTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveSendSectionLabel(text = "I'm joining as", forceUppercase = false)
                    LiveSendSegmentedToggle(
                        options = listOf("Climber", "Gym / Club"),
                        selectedIndex = joiningIndex,
                        onSelect = { joiningIndex = it },
                        modifier = Modifier.semantics {
                            contentDescription = "I'm joining as: " +
                                if (joiningIndex == 0) "Climber" else "Gym / Club"
                        },
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LiveSendPrimaryButton(
                    text = "Create Account",
                    onClick = { onCreateAccount(name, email, password, joiningIndex == 1) },
                    modifier = Modifier.semantics { role = Role.Button },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "Log in",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(onClick = onLogin)
                            .semantics { role = Role.Button },
                    )
                }
            }
        }
    }
}
