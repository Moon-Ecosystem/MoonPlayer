package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.WarmIvory

/**
 * Turntable / Jewel Case album cover inspired by Image 3.
 * Features an acrylic jewel case housing a rotating CD/vinyl turntable platter
 * with center spindle, groove rings, and album artwork label.
 */
@Composable
fun TurntableAlbumCover(
    track: Track?,
    isPlaying: Boolean,
    accentColor: Color = WarmIvory,
    size: Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "turntable_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    val currentRotation = if (isPlaying) rotationAngle else 0f

    // OUTER JEWEL CASE
    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .shadow(16.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF22201E),
                        Color(0xFF161514),
                        Color(0xFF1B1918)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        TerminalBorder,
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(10.dp)
            .testTag("turntable_cover_container"),
        contentAlignment = Alignment.Center
    ) {
        // Acrylic Case Glass Reflection & Hinge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Left jewel case plastic hinge spine
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f)
                    )
                ),
                topLeft = Offset(0f, 0f),
                size = Size(18f, h)
            )

            // Diagonal acrylic reflection streak
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, h * 0.25f),
                end = Offset(w * 0.75f, 0f),
                strokeWidth = 32f,
                cap = StrokeCap.Round
            )
        }

        // ROTATING CD / VINYL TURNTABLE DISC
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(currentRotation)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF0F0E0D)),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl Groove rings & rainbow laser luster
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // Concentric grooves
                for (r in listOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = radius * r,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }

                // Laser sheen reflection cones (CD iridescent luster)
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accentColor.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius * 0.94f,
                    center = center
                )
            }

            // CENTER ALBUM ARTWORK LABEL
            val labelFraction = 0.46f
            Box(
                modifier = Modifier
                    .fillMaxSize(labelFraction)
                    .clip(CircleShape)
                    .background(TerminalBlack)
                    .border(2.dp, accentColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (track?.albumArtUri != null) {
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = "Cover label",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.moon_player_logo)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.moon_player_logo),
                        contentDescription = "Mascot Label",
                        modifier = Modifier
                            .fillMaxSize(0.75f)
                            .clip(CircleShape)
                    )
                }

                // CENTER SPINDLE HOLE
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0908))
                        .border(2.dp, Color(0xFF33302D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TerminalBlack)
                    )
                }
            }
        }
    }
}
