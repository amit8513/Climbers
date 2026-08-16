package com.example.climb.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.data.settings.ClimbThemeOption
import com.example.climb.data.settings.HomeVideoMontageStyle
import com.example.climb.data.settings.SettingsStore
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.data.social.UserProfile
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.palette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val usernamePattern = Regex("^[a-zA-Z0-9_]{3,20}$")

@Composable
fun SettingsScreen(
    uid: String,
    profile: UserProfile,
    socialRepository: SocialRepository,
    authRepository: AuthRepository,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onOpenClubs: () -> Unit,
    onOpenLiveSendPreview: () -> Unit = {},
    staffOrganizations: List<OrganizationEntity> = emptyList(),
    onEnterClubMode: (OrganizationEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Settings",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Profile")
                    Spacer(Modifier.height(10.dp))
                    ProfileSection(uid = uid, profile = profile, socialRepository = socialRepository)
                }
            }
            Spacer(Modifier.height(16.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Password")
                    Spacer(Modifier.height(10.dp))
                    PasswordSection(authRepository = authRepository)
                }
            }
            Spacer(Modifier.height(16.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Clubs")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Follow a gym to select routes for your climbs and get evidence-based analysis enhanced with route context.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = "Open Clubs →",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onOpenClubs),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (staffOrganizations.isNotEmpty()) {
                LiveSendCard {
                    Column {
                        LiveSendSectionLabel(text = "Club Mode")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "You help run these clubs. Switch into Club Mode to manage routes, venues, and members.",
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        staffOrganizations.forEachIndexed { index, organization ->
                            if (index > 0) Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Enter as \"${organization.name}\" →",
                                color = ClimbPalette.liveSendAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { onEnterClubMode(organization) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Appearance")
                    Spacer(Modifier.height(10.dp))
                    AppearanceSection(settingsStore = settingsStore)
                }
            }
            Spacer(Modifier.height(16.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Home background")
                    Spacer(Modifier.height(10.dp))
                    HomeBackgroundSection(settingsStore = settingsStore)
                }
            }
            Spacer(Modifier.height(16.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "UI Concepts")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Design exploration: \"Live Send\" — an energetic sport-style alternative UI with its own auth flow and gym-mode dashboard.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = "Preview: Live Send →",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onOpenLiveSendPreview),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Account")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Sign out",
                        color = ClimbPalette.liveSendCta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { authRepository.signOut() },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileSection(uid: String, profile: UserProfile, socialRepository: SocialRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var username by remember(profile.username) { mutableStateOf(profile.username) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) { selectedPhotoUri = uri; successMessage = null } }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        EditableAvatar(
            photoUri = selectedPhotoUri,
            photoUrl = profile.photoUrl,
            name = profile.username,
            onClick = { pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        Text(
            text = "Tap to change photo",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }

    LiveSendTextField(
        value = username,
        onValueChange = { username = it; errorMessage = null; successMessage = null },
        placeholder = "Username",
        modifier = Modifier.fillMaxWidth(),
    )

    if (errorMessage != null) {
        Text(text = errorMessage.orEmpty(), color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    if (successMessage != null) {
        Text(text = successMessage.orEmpty(), color = ClimbPalette.sent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    val usernameChanged = username != profile.username
    val photoChanged = selectedPhotoUri != null
    LiveSendPrimaryButton(
        text = "Save changes",
        enabled = !loading && (usernameChanged || photoChanged) && usernamePattern.matches(username),
        loading = loading,
        onClick = {
            loading = true
            errorMessage = null
            successMessage = null
            scope.launch {
                val pickedUri = selectedPhotoUri
                if (pickedUri != null) {
                    val contentType = context.contentResolver.getType(pickedUri)
                    val uploadResult = socialRepository.uploadProfilePhoto(uid, pickedUri, contentType)
                    if (uploadResult.isFailure) {
                        loading = false
                        errorMessage = "Couldn't upload photo — try again"
                        return@launch
                    }
                    val photoUpdateResult = socialRepository.updateProfilePhoto(uid, uploadResult.getOrNull())
                    if (photoUpdateResult.isFailure) {
                        loading = false
                        errorMessage = "Photo uploaded, but saving it failed — try again"
                        return@launch
                    }
                }
                if (usernameChanged) {
                    val result = socialRepository.updateUsername(uid, profile.username, username)
                    if (result.isFailure) {
                        loading = false
                        errorMessage = result.exceptionOrNull()?.message ?: "Something went wrong"
                        return@launch
                    }
                }
                loading = false
                successMessage = "Saved"
                selectedPhotoUri = null
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    )
}

@Composable
private fun EditableAvatar(photoUri: Uri?, photoUrl: String?, name: String, onClick: () -> Unit) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Change profile photo" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Black, fontSize = 32.sp)
        val model = photoUri ?: photoUrl?.takeIf { it.isNotBlank() }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun PasswordSection(authRepository: AuthRepository) {
    if (!authRepository.hasPasswordProvider) {
        Text(
            text = "Your account signs in with Google, so there's no password to change here.",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        return
    }

    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    LiveSendTextField(
        value = currentPassword,
        onValueChange = { currentPassword = it; errorMessage = null; successMessage = null },
        placeholder = "Current password",
        isPassword = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    LiveSendTextField(
        value = newPassword,
        onValueChange = { newPassword = it; errorMessage = null; successMessage = null },
        placeholder = "New password",
        isPassword = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    LiveSendTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it; errorMessage = null; successMessage = null },
        placeholder = "Confirm new password",
        isPassword = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (newPassword.isNotEmpty() && newPassword.length < 6) {
        Text(text = "New password must be at least 6 characters", color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
        Text(text = "Passwords don't match", color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (errorMessage != null) {
        Text(text = errorMessage.orEmpty(), color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (successMessage != null) {
        Text(text = successMessage.orEmpty(), color = ClimbPalette.sent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    LiveSendPrimaryButton(
        text = "Update password",
        enabled = !loading && currentPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword,
        loading = loading,
        onClick = {
            loading = true
            errorMessage = null
            successMessage = null
            scope.launch {
                val result = authRepository.changePassword(currentPassword, newPassword)
                loading = false
                result.onSuccess {
                    successMessage = "Password updated"
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
                result.onFailure { errorMessage = it.message ?: "Something went wrong" }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    )
}

@Composable
private fun AppearanceSection(settingsStore: SettingsStore) {
    Text(text = "Theme", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ClimbThemeOption.entries.forEach { option ->
            ThemeCard(
                option = option,
                selected = settingsStore.themeOption == option,
                onClick = { settingsStore.selectTheme(option) },
            )
        }
    }
}

@Composable
private fun HomeBackgroundSection(settingsStore: SettingsStore) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "Video montage", color = ClimbPalette.liveSendTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Cycle through your own climb videos behind the Home screen. Turn off to use the plain background instead.",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Switch(
                checked = settingsStore.homeVideoBackgroundEnabled,
                onCheckedChange = { settingsStore.updateHomeVideoBackgroundEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ClimbPalette.liveSendAccentText,
                    checkedTrackColor = ClimbPalette.liveSendAccent,
                    uncheckedThumbColor = ClimbPalette.liveSendTextMuted,
                    uncheckedTrackColor = ClimbPalette.liveSendSurfaceRaised,
                    uncheckedBorderColor = ClimbPalette.liveSendBorder,
                ),
            )
        }

        if (settingsStore.homeVideoBackgroundEnabled) {
            Spacer(Modifier.height(18.dp))
            Text(text = "Style", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeVideoMontageStyle.entries.forEach { option ->
                    MontageStyleRow(
                        option = option,
                        selected = settingsStore.homeVideoMontageStyle == option,
                        onClick = { settingsStore.selectHomeVideoMontageStyle(option) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Video visibility", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
                Text(text = "${(settingsStore.homeVideoOpacity * 100).roundToInt()}%", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
            }
            Slider(
                value = settingsStore.homeVideoOpacity,
                onValueChange = { settingsStore.updateHomeVideoOpacity(it) },
                colors = SliderDefaults.colors(
                    thumbColor = ClimbPalette.liveSendAccent,
                    activeTrackColor = ClimbPalette.liveSendAccent,
                    inactiveTrackColor = ClimbPalette.liveSendSurfaceRaised,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Video background visibility" },
            )
        }
    }
}

@Composable
private fun MontageStyleRow(option: HomeVideoMontageStyle, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ClimbPalette.liveSendSurfaceRaised else ClimbPalette.liveSendSurface)
            .border(if (selected) 2.dp else 1.dp, if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${option.label}${if (selected) ", selected" else ""}" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = option.label, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(text = option.description, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (selected) {
            Text(text = "✓", color = ClimbPalette.sent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Each card previews the theme's own background, surface, and accent colors directly — rather
 * than a single dot — since the whole point of a full theme (vs. the old single-accent picker)
 * is that several colors change together. */
@Composable
private fun ThemeCard(option: ClimbThemeOption, selected: Boolean, onClick: () -> Unit) {
    val palette = option.palette()
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 64.dp)
                .clip(shape)
                .background(palette.bg)
                .border(if (selected) 2.dp else 1.dp, if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "${option.label} theme${if (selected) ", selected" else ""}" },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(palette.surface),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(palette.chalk),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = option.label, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
    }
}
