package com.example.climb.ui.livesend.real

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.climb.R
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.ui.livesend.LoginScreen
import com.example.climb.ui.livesend.OnboardingScreen
import com.example.climb.ui.livesend.SignupScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

private object LiveSendAuthRoutes {
    const val ONBOARDING = "live_send_real_onboarding"
    const val LOGIN = "live_send_real_login"
    const val SIGNUP = "live_send_real_signup"
}

private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")

/**
 * The real, production entry point for logged-out users — same visual flow as the "Live Send"
 * design-exploration screens ([OnboardingScreen]/[LoginScreen]/[SignupScreen]), but wired to
 * [AuthRepository]/[SocialRepository] instead of no-op callbacks. Replaces
 * [com.example.climb.ui.auth.AuthScreen] as [com.example.climb.navigation.ClimbNavHost]'s
 * logged-out branch. Once [AuthRepository.currentUid] becomes non-null (sign-in/sign-up
 * succeeds), `ClimbNavHost`'s own top-level dispatch recomposes past this host automatically —
 * this host has no "authenticated" callback of its own. If a brand-new account's profile document
 * hasn't been created yet (e.g. a first-time Google sign-in), `ClimbNavHost`'s existing
 * `profile == null -> ProfileSetupScreen` fallback still applies unchanged.
 */
@Composable
fun LiveSendAuthHost(authRepository: AuthRepository, socialRepository: SocialRepository) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build(),
        )
    }
    // Same idToken-extraction and cancellation/policy-block disambiguation as
    // com.example.climb.ui.auth.AuthScreen's googleSignInLauncher — kept identical rather than
    // reimplemented, since it already handles the Play-Services/work-profile edge case correctly.
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        var idToken: String? = null
        try {
            idToken = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
        } catch (e: ApiException) {
            loading = false
            errorMessage = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                "Google sign-in was cancelled"
            } else {
                "Google sign-in didn't complete (code ${e.statusCode}). If this phone has a work profile or " +
                    "device-management policy, it may be blocking sign-in for this account."
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

    fun goTo(navController: NavHostController, route: String) {
        errorMessage = null
        navController.navigate(route)
    }

    NavHost(navController = navController, startDestination = LiveSendAuthRoutes.ONBOARDING) {
        composable(LiveSendAuthRoutes.ONBOARDING) {
            OnboardingScreen(
                onGetStarted = { goTo(navController, LiveSendAuthRoutes.SIGNUP) },
                onLogin = { goTo(navController, LiveSendAuthRoutes.LOGIN) },
            )
        }

        composable(LiveSendAuthRoutes.LOGIN) {
            LoginScreen(
                onBack = { errorMessage = null; navController.popBackStack() },
                onLogin = { email, password ->
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val result = authRepository.signIn(email.trim(), password)
                        loading = false
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
                // TODO(live-send-real): AuthRepository has no password-reset flow (no
                // sendPasswordResetEmail) — leaving this a no-op rather than fabricating one.
                onForgotPassword = { errorMessage = "Password reset isn't available yet." },
                onGoogleLogin = {
                    loading = true
                    errorMessage = null
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                // TODO(live-send-real): no Apple sign-in provider is wired up anywhere in this
                // app (Firebase project has no Apple OAuth config) — surfaced honestly, not faked.
                onAppleLogin = { errorMessage = "Apple sign-in isn't available yet." },
                onCreateAccount = { goTo(navController, LiveSendAuthRoutes.SIGNUP) },
                isLoading = loading,
                errorMessage = errorMessage,
            )
        }

        composable(LiveSendAuthRoutes.SIGNUP) {
            SignupScreen(
                onBack = { errorMessage = null; navController.popBackStack() },
                onLogin = { goTo(navController, LiveSendAuthRoutes.LOGIN) },
                onCreateAccount = { name, email, password, joiningAsGym ->
                    val username = name.trim()
                    if (!USERNAME_PATTERN.matches(username)) {
                        errorMessage = "Username must be 3-20 characters: letters, numbers, or underscores only."
                        return@SignupScreen
                    }
                    // "Gym / Club" vs "Climber" has no real account-type distinction anywhere in
                    // this app today — there's no self-serve club-creation flow (see
                    // ClubRepository.ensureSeedOrganization's doc comment), so this flag is
                    // intentionally not persisted rather than fabricating a role that doesn't
                    // exist server-side.
                    // TODO(live-send-real): [joiningAsGym] is discarded here for that reason.
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val signUpResult = authRepository.signUp(email.trim(), password)
                        if (signUpResult.isFailure) {
                            loading = false
                            errorMessage = signUpResult.exceptionOrNull()?.message ?: "Something went wrong"
                            return@launch
                        }
                        val uid = authRepository.currentUid
                        if (uid == null) {
                            loading = false
                            errorMessage = "Something went wrong finishing sign-up"
                            return@launch
                        }
                        val profileResult = socialRepository.createProfile(uid, username)
                        loading = false
                        // On failure (e.g. username taken), the account is still created and
                        // signed in — ClimbNavHost's existing profile == null -> ProfileSetupScreen
                        // fallback catches this and lets the user pick a different username there,
                        // so there's nothing further to do here besides surfacing the error while
                        // still on this screen in case sign-up itself is retried.
                        profileResult.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
                isLoading = loading,
                errorMessage = errorMessage,
            )
        }
    }
}
