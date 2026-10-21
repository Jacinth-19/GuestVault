package com.guestvault.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guestvault.R
import com.guestvault.core.AppVersion
import com.guestvault.ui.theme.Indigo800
import com.guestvault.ui.theme.LightSystemBarIcons

/**
 * Brand splash.
 *
 * Deliberately flat: one solid brand colour, one logo, the wordmark with a
 * version pill, the tagline, and a hairline progress bar. The previous version
 * used a gradient plus three decorative circles, which read as a generic
 * placeholder and made the README screenshot ambiguous with the onboarding
 * pages.
 *
 * The version comes from [AppVersion.display] rather than a literal, so it can
 * never drift from the build again — and never leaks the `-debug` suffix into a
 * screenshot.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    versionName: String = AppVersion.display
) {
    LightSystemBarIcons()

    val versionDescription = stringResource(R.string.splash_version_description, versionName)

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "logoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 520, delayMillis = 180),
        label = "textAlpha"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = 380),
        label = "pillAlpha"
    )
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 1_300, easing = LinearEasing),
        label = "progress"
    )

    // A very slow halo behind the mark, so the screen breathes without moving.
    val halo = rememberInfiniteTransition(label = "halo")
    val haloAlpha by halo.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Indigo800)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .alpha(haloAlpha)
                .clip(CircleShape)
                .background(Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The mark, the wordmark, the pill and the tagline form one block.
            // They used to be separated by a weighted spacer, which pushed the
            // wordmark 300 dp below the logo on a tall phone.
            Spacer(Modifier.weight(1f))

            ShieldMark(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
            )

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(textAlpha)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .alpha(pillAlpha)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.20f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                        .semantics {
                            contentDescription = versionDescription
                        }
                ) {
                    Text(
                        text = versionName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.app_tagline),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(Modifier.height(28.dp))

            ProgressBar(
                progress = progress,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(Modifier.weight(1.15f))
            Spacer(Modifier.height(46.dp))
        }
    }
}

/** White shield with an indigo keyhole, matching the launcher icon. */
@Composable
private fun ShieldMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(132.dp)
        )
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = Indigo800,
            modifier = Modifier
                .size(44.dp)
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(112.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .width(112.dp * progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.secondary)
        )
    }
}
