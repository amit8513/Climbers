package com.example.climb.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import com.example.climb.data.social.SocialRepository
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

private val usernamePattern = Regex("^[a-zA-Z0-9_]{3,20}$")

@Composable
fun ProfileSetupScreen(
    uid: String,
    socialRepository: SocialRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var username by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) selectedPhotoUri = uri }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Pick a username",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                text = "This is how friends will find you. 3-20 letters, numbers, or underscores.",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ProfilePhotoPicker(
                    photoUri = selectedPhotoUri,
                    onClick = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                Text(
                    text = if (selectedPhotoUri == null) "Add a profile photo (optional)" else "Tap to change photo",
                    color = ClimbPalette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = ClimbPalette.fell,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Button(
                enabled = !loading && usernamePattern.matches(username),
                onClick = {
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val photoUri = selectedPhotoUri
                        var photoUrl: String? = null
                        if (photoUri != null) {
                            val contentType = context.contentResolver.getType(photoUri)
                            val uploadResult = socialRepository.uploadProfilePhoto(uid, photoUri, contentType)
                            if (uploadResult.isFailure) {
                                loading = false
                                errorMessage = "Couldn't upload your photo — try again"
                                return@launch
                            }
                            photoUrl = uploadResult.getOrNull()
                        }
                        val result = socialRepository.createProfile(uid, username, photoUrl)
                        loading = false
                        result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun ProfilePhotoPicker(photoUri: Uri?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(ClimbPalette.surfaceRaised)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (photoUri == null) "Add a profile photo" else "Change profile photo" },
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        } else {
            Text(text = "+", color = ClimbPalette.textMuted, fontWeight = FontWeight.Black, fontSize = 32.sp)
        }
    }
}
