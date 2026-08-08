package com.example.climb.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import com.example.climb.R
import com.example.climb.data.social.AuthRepository
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.ClimbTheme
import com.example.climb.ui.theme.wallTexture
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isSignUp by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build(),
        )
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Distinguishing a real user cancellation from every other failure matters here: this
        // is also how a device-management/work-profile policy blocking Google sign-in shows up
        // (Play Services fails the token exchange before an account is ever returned), and that
        // used to get mislabeled as "cancelled" even though the user never touched cancel.
        var idToken: String? = null
        try {
            idToken = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
        } catch (e: ApiException) {
            loading = false
            errorMessage = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                "Google sign-in was cancelled"
            } else {
                "Google sign-in didn't complete (code ${e.statusCode}). If this phone has a work profile or " +
                    "device-management policy, it may be blocking sign-in for this account — try a personal " +
                    "Google account, or check with whoever manages this device."
            }
        }

        if (idToken != null) {
            scope.launch {
                val signInResult = authRepository.signInWithGoogle(idToken)
                loading = false
                signInResult.onFailure { errorMessage = it.message ?: "Google sign-in failed" }
            }
        } else if (errorMessage == null) {
            loading = false
            errorMessage = "Google sign-in was cancelled"
        }
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            // Centres the form when it fits and still scrolls when the keyboard shrinks the
            // viewport, so the screen never sits top-heavy with dead space underneath.
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "CLIMBERS",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(22.dp))

            SegmentedTabs(
                isSignUp = isSignUp,
                onSelect = { signUp -> isSignUp = signUp; errorMessage = null },
            )

            Spacer(Modifier.height(22.dp))

            GoogleButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    errorMessage = null
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
            )

            Spacer(Modifier.height(18.dp))
            OrDivider()
            Spacer(Modifier.height(18.dp))

            FieldLabel("Email")
            AuthTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                placeholder = "you@email.com",
                keyboardType = KeyboardType.Email,
            )

            Spacer(Modifier.height(16.dp))

            FieldLabel("Password")
            AuthTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                placeholder = if (isSignUp) "8+ characters" else "Your password",
                keyboardType = KeyboardType.Password,
                isPassword = true,
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(text = errorMessage.orEmpty(), color = ClimbPalette.fell, fontSize = 12.sp)
            }

            Spacer(Modifier.height(26.dp))

            ChalkSubmitButton(
                label = if (isSignUp) "Sign up" else "Log in",
                loading = loading,
                enabled = !loading && email.isNotBlank() && password.length >= 6,
                onClick = {
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val result = if (isSignUp) {
                            authRepository.signUp(email.trim(), password)
                        } else {
                            authRepository.signIn(email.trim(), password)
                        }
                        loading = false
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
            )

            if (onSkip != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Just logging climbs? ",
                        color = ClimbPalette.textMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Skip for now",
                        color = ClimbPalette.textSecondary,
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onSkip),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SegmentedTabs(isSignUp: Boolean, onSelect: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ClimbPalette.border, shape),
    ) {
        SegmentedTab(text = "Log in", selected = !isSignUp, onClick = { onSelect(false) }, modifier = Modifier.weight(1f))
        SegmentedTab(text = "Sign up", selected = isSignUp, onClick = { onSelect(true) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SegmentedTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(if (selected) ClimbPalette.surfaceRaised else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) ClimbPalette.textPrimary else ClimbPalette.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, ClimbPalette.borderStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark()
        Spacer(Modifier.size(10.dp))
        Text(
            text = "Continue with Google",
            color = ClimbPalette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Google's four brand colours as quadrants — enough to read as the G mark at 18dp. */
@Composable
private fun GoogleMark() {
    Canvas(modifier = Modifier.size(18.dp).clip(CircleShape)) {
        val quadrants = listOf(
            Color(0xFFEA4335) to -90f,
            Color(0xFFFBBC05) to 0f,
            Color(0xFF34A853) to 90f,
            Color(0xFF4285F4) to 180f,
        )
        quadrants.forEach { (color, startAngle) ->
            drawArc(color = color, startAngle = startAngle, sweepAngle = 90f, useCenter = true)
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = ClimbPalette.border, modifier = Modifier.weight(1f))
        Text(
            text = "OR",
            color = ClimbPalette.textMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(color = ClimbPalette.border, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = ClimbPalette.textMuted,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ClimbPalette.textMuted, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ClimbPalette.surface,
            unfocusedContainerColor = ClimbPalette.surface,
            focusedTextColor = ClimbPalette.textPrimary,
            unfocusedTextColor = ClimbPalette.textPrimary,
            cursorColor = ClimbPalette.chalk,
            focusedIndicatorColor = ClimbPalette.borderStrong,
            unfocusedIndicatorColor = ClimbPalette.border,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The CTA is a dark rock face with a chalk border and chalk label, so it reads as the primary
 * action against the near-black background without borrowing the chalk-white fill used by the
 * record button elsewhere.
 */
@Composable
private fun ChalkSubmitButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val contentAlpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(ClimbPalette.rockFace)
            .border(1.dp, ClimbPalette.chalk.copy(alpha = 0.55f * contentAlpha), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = ClimbPalette.chalk,
            )
        } else {
            Text(
                text = label,
                color = ClimbPalette.chalk.copy(alpha = contentAlpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 220)
@Composable
private fun ChalkSubmitButtonPreview() {
    ClimbTheme {
        Column(
            modifier = Modifier.fillMaxSize().wallTexture().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ChalkSubmitButton(label = "Sign up", loading = false, enabled = true, onClick = {})
            Spacer(Modifier.height(20.dp))
            ChalkSubmitButton(label = "Sign up", loading = false, enabled = false, onClick = {})
        }
    }
}
