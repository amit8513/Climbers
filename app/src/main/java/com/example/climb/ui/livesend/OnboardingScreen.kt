package com.example.climb.ui.livesend

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.R
import com.example.climb.ui.livesend.components.LiveSendHeroImage
import com.example.climb.ui.livesend.components.LiveSendPageIndicator
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Live Send's Onboarding screen (Figma node 5:301) — the app's cold-start splash: a full-bleed
 * bouldering hero photo fading into the app background, a lime "highlighter" bar behind the
 * "SEND IT" wordmark, the tagline, the primary "Get Started" CTA, a "Log in" affordance for
 * returning users, and a 3-dot page indicator marking this as the first of the onboarding flow.
 *
 * [LiveSendHeroImage]'s built-in bottom scrim gradient stands in for the spec's separate `Scrim`
 * rectangle (both exist to darken the lower half of the same photo into [ClimbPalette.liveSendBg]), and the
 * content block below is bottom-aligned over the full screen so it naturally overlaps the last of
 * that fade, matching the spec's `Scrim` height reaching well past the hero's own bounds.
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        LiveSendHeroImage(
            modifier = Modifier.align(Alignment.TopCenter),
            height = 550,
        ) {
            Image(
                painter = painterResource(R.drawable.livesend_onboarding_hero),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 36.dp),
        ) {
            Box(modifier = Modifier.wrapContentHeight()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(210.dp)
                        .height(44.dp)
                        .background(ClimbPalette.liveSendAccent),
                )
                Text(
                    text = "SEND IT",
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Bouldering clubs. Real sends. Real video.",
                color = ClimbPalette.liveSendTextMuted,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(28.dp))

            LiveSendPrimaryButton(
                text = "Get Started",
                onClick = onGetStarted,
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = "Get Started"
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        .wrapContentHeight()
                        .padding(horizontal = 4.dp, vertical = 14.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Log in"
                        }
                        .clickable(onClick = onLogin),
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiveSendPageIndicator(pageCount = 3, currentPage = 0)
            }
        }
    }
}
