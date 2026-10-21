package com.guestvault.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guestvault.R
import com.guestvault.core.AppVersion
import com.guestvault.ui.theme.Indigo800
import com.guestvault.ui.theme.LightSystemBarIcons

/**
 * Brand splash, modelled on the flat VidMate layout.
 *
 * One solid brand colour, one mark at ~30% of the screen width, the wordmark
 * with a version pill beside it, the tagline, and a hairline progress line.
 * No gradients and no background decoration: the earlier version scattered
 * circles behind the logo, which made the README screenshot ambiguous with the
 * onboarding art.
 *
 * The version comes from [AppVersion.display] (a projection of
 * `BuildConfig.VERSION_NAME`), so the pill can never drift from the build again
 * — and never leaks the debug `-debug` suffix into a screenshot.
 *
 * `launch_background.xml` and the `values-v31` theme paint the same colour
 * before the first Compose frame, so Android 12+ has no white flash here.
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

    // 0.94 -> 1 over 600 ms: a settle, not a bounce.
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "logoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = 120),
        label = "textAlpha"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = 300),
        label = "pillAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Indigo800)
    ) {
        // Measured on the *screen*, not on the padded column: "~30% of the
        // width" means 30% of the phone, and the column's 28 dp gutters would
        // otherwise shrink the mark to ~26%.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val markSize = (maxWidth * 0.30f).coerceIn(104.dp, 168.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))

                ShieldMark(
                    size = markSize,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        }
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(textAlpha)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.1).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .alpha(pillAlpha)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = versionDescription }
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

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.app_tagline),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(textAlpha)
                )

                Spacer(Modifier.height(30.dp))

                IndeterminateLine(modifier = Modifier.alpha(textAlpha))

                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The brand mark: the same vector the launcher and the Android 12+ system splash
 * use ([R.drawable.ic_splash_logo]), not two Material icons.
 *
 * Using the real icon matters: the OS splash hands over to this screen, and a
 * Material shield is a different silhouette from ours, so the swap was visible.
 *
 * `ic_splash_logo` is a 96-unit canvas: the shield spans 62.5% of its width and
 * 85% of its height. So the image is drawn at 1.6x [size] (for the shield's
 * *width* to measure [size], i.e. ~30% of the screen) inside a box that reserves
 * the shield's real height, 1.36x [size]. Without that box the layout would
 * reserve only the image's square and the wordmark would ride up under the
 * shield's point on a tall screen.
 */
@Composable
private fun ShieldMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(size)
            .height(size * 1.36f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(size * 1.6f)
        )
    }
}

/**
 * A thin, *indeterminate* progress line: a short highlight sweeping across a
 * dim track. It says "working" without pretending to know how far along the
 * start-up is — and unlike a bar that fills, it never sits full and static on a
 * slow device.
 */
@Composable
private fun IndeterminateLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "progress")
    val sweep by transition.animateFloat(
        initialValue = -0.40f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .width(112.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .fillMaxHeight()
                .graphicsLayer {
                    // -0.34 puts the highlight just off the left edge; 1.0 puts
                    // it just off the right edge, so the loop never "pops".
                    translationX = sweep * 112.dp.toPx()
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.secondary)
        )
    }
}
