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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.data.settings.ClimbThemeOption
import com.example.climb.data.settings.SettingsStore
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.data.social.UserProfile
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.palette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

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
    staffOrganizations: List<OrganizationEntity> = emptyList(),
    onEnterClubMode: (OrganizationEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Settings",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            SectionCard(title = "Profile") {
                ProfileSection(uid = uid, profile = profile, socialRepository = socialRepository)
            }
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Password") {
                PasswordSection(authRepository = authRepository)
            }
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Clubs") {
                Column {
                    Text(
                        text = "Follow a gym to select routes for your climbs and get evidence-based analysis enhanced with route context.",
                        color = ClimbPalette.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = "Open Clubs →",
                        color = ClimbPalette.chalk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onOpenClubs),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (staffOrganizations.isNotEmpty()) {
                SectionCard(title = "Club Mode") {
                    Column {
                        Text(
                            text = "You help run these clubs. Switch into Club Mode to manage routes, venues, and members.",
                            color = ClimbPalette.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        staffOrganizations.forEachIndexed { index, organization ->
                            if (index > 0) Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Enter as \"${organization.name}\" →",
                                color = ClimbPalette.chalk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { onEnterClubMode(organization) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionCard(title = "Appearance") {
                AppearanceSection(settingsStore = settingsStore)
            }
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Home background") {
                HomeBackgroundSection(settingsStore = settingsStore)
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
            color = ClimbPalette.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }

    OutlinedTextField(
        value = username,
        onValueChange = { username = it; errorMessage = null; successMessage = null },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (errorMessage != null) {
        Text(text = errorMessage.orEmpty(), color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    if (successMessage != null) {
        Text(text = successMessage.orEmpty(), color = ClimbPalette.sent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    val usernameChanged = username != profile.username
    val photoChanged = selectedPhotoUri != null
    Button(
        enabled = !loading && (usernameChanged || photoChanged) && usernamePattern.matches(username),
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
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Save changes")
        }
    }
}

@Composable
private fun EditableAvatar(photoUri: Uri?, photoUrl: String?, name: String, onClick: () -> Unit) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(ClimbPalette.surfaceRaised)
            .border(1.dp, ClimbPalette.border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Change profile photo" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 32.sp)
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
            color = ClimbPalette.textSecondary,
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

    OutlinedTextField(
        value = currentPassword,
        onValueChange = { currentPassword = it; errorMessage = null; successMessage = null },
        label = { Text("Current password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it; errorMessage = null; successMessage = null },
        label = { Text("New password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it; errorMessage = null; successMessage = null },
        label = { Text("Confirm new password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    if (newPassword.isNotEmpty() && newPassword.length < 6) {
        Text(text = "New password must be at least 6 characters", color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
        Text(text = "Passwords don't match", color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (errorMessage != null) {
        Text(text = errorMessage.orEmpty(), color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    } else if (successMessage != null) {
        Text(text = successMessage.orEmpty(), color = ClimbPalette.sent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    Button(
        enabled = !loading && currentPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword,
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
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Update password")
        }
    }
}

@Composable
private fun AppearanceSection(settingsStore: SettingsStore) {
    Text(text = "Theme", color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = "Video montage", color = ClimbPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Cycle through your own climb videos, with cut transitions, behind the Home screen. Turn off to use the plain background instead.",
                color = ClimbPalette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Switch(
            checked = settingsStore.homeVideoBackgroundEnabled,
            onCheckedChange = { settingsStore.updateHomeVideoBackgroundEnabled(it) },
        )
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
                .border(if (selected) 2.dp else 1.dp, if (selected) ClimbPalette.textPrimary else ClimbPalette.border, shape)
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
        Text(text = option.label, color = ClimbPalette.textMuted, fontSize = 11.sp)
    }
}
