package com.example.climb.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.R
import com.example.climb.data.social.AuthRepository
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(authRepository: AuthRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isSignUp by remember { mutableStateOf(false) }
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
        val idToken = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
        }.getOrNull()

        if (idToken == null) {
            loading = false
            errorMessage = "Google sign-in was cancelled"
        } else {
            scope.launch {
                val signInResult = authRepository.signInWithGoogle(idToken)
                loading = false
                signInResult.onFailure { errorMessage = it.message ?: "Google sign-in failed" }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "CLIMB",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = if (isSignUp) "Create an account to add friends" else "Log in to see your friends",
                color = ClimbPalette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Box(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = ClimbPalette.fell,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Button(
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
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isSignUp) "Sign up" else "Log in")
                }
            }

            TextButton(
                onClick = { isSignUp = !isSignUp; errorMessage = null },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = if (isSignUp) "Already have an account? Log in" else "New here? Create an account",
                    color = ClimbPalette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                HorizontalDivider(color = ClimbPalette.border, modifier = Modifier.weight(1f))
                Text(text = "or", color = ClimbPalette.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(color = ClimbPalette.border, modifier = Modifier.weight(1f))
            }

            OutlinedButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    errorMessage = null
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue with Google")
            }
        }
    }
}
