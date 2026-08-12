package com.example.climb.ui.livesend

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.R
import com.example.climb.ui.livesend.components.LiveSendHeroImage
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Live Send (Alternative UI Concept 2) — LoginScreen, Figma node 5:302.
 *
 * A hero photo bleeds into the themed background, followed by a headline, email/password
 * fields, a "Forgot password?" link, the primary "Log In" CTA, a social-login row (Google /
 * Apple), and a "New here? Create account" footer. Purely presentational + input state; all
 * navigation/auth side effects are handed back to the caller via the lambda params below, per
 * this project's convention of keeping leaf screens ignorant of navigation/route types.
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleLogin: () -> Unit,
    onAppleLogin: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            LiveSendHeroImage(height = 280) {
                Image(
                    painter = painterResource(R.drawable.livesend_login_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 40.dp)
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
            }

            Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                Text(
                    text = "Welcome back",
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Log in to keep sending.",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
                )

                LiveSendTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    keyboardType = KeyboardType.Email,
                )
                Box(modifier = Modifier.height(14.dp))
                LiveSendTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = "Forgot password?",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onForgotPassword)
                            .padding(vertical = 15.dp),
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = ClimbPalette.fell,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                LiveSendPrimaryButton(
                    text = "Log In",
                    onClick = { onLogin(email, password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    loading = isLoading,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Text(
                    text = "or continue with",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    SocialLoginButton(
                        label = "Google",
                        onClick = onGoogleLogin,
                        modifier = Modifier.weight(1f),
                    )
                    SocialLoginButton(
                        label = "Apple",
                        onClick = onAppleLogin,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "New here? ",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "Create account",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onCreateAccount)
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * A single 48dp-tall, 16dp-rounded, [ClimbPalette.liveSendSurfaceRaised]-filled OAuth button
 * (GoogleBtn/AppleBtn in the spec). Screen-local: only this screen and Signup (a sibling
 * Live Send screen) need it, and it carries zero domain knowledge, so it stays private here
 * per this project's promote-on-second-shared-use convention rather than pre-emptively
 * living in ui/livesend/components.
 */
@Composable
private fun SocialLoginButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClimbPalette.liveSendSurfaceRaised)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}
