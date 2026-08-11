package com.example.climb.ui.livesend.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The borderless, filled rounded-16dp text field used for every Login/Signup field (EmailField,
 * PasswordField, NameField). Mirrors [com.example.climb.ui.auth.AuthScreen]'s private
 * `AuthTextField` (same [TextFieldDefaults.colors] wiring into [ClimbPalette]) but with no
 * indicator line at all — the spec's fields are flat filled pills with no border in either state —
 * and Live Send's larger 16dp corner radius instead of the shipped auth screen's 8dp.
 */
@Composable
fun LiveSendTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ClimbPalette.liveSendTextMuted, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ClimbPalette.liveSendSurfaceRaised,
            unfocusedContainerColor = ClimbPalette.liveSendSurfaceRaised,
            focusedTextColor = ClimbPalette.liveSendTextPrimary,
            unfocusedTextColor = ClimbPalette.liveSendTextPrimary,
            cursorColor = ClimbPalette.liveSendAccent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
