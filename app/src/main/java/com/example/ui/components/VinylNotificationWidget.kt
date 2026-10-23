package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalRaisedPanel
import com.example.ui.theme.WarmIvory

/**
 * Vinyl Sleeve Notification Card matching Image 1 (NotificationWidget_297093c5.jpeg).
 * Renders a horizontal card with metadata, controls, vinyl disc sliding from the sleeve,
 * and cover art with heart icon.
 */
@Composable
fun VinylSleeveNotificationCard(
    track: Track?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF7F5F2), // Light sleek sleeve as in Image 1
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag("vinyl_notification_widget")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE: Track Title, Artist, and Circular Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = (track?.title ?: "MOON PLAYER").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1B1918),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = (track?.artist ?: "STUDIO ALBUM").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B655F),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls row: Play circular button + Next circular button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onTogglePlay() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onNext() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // CENTER / RIGHT: Vinyl Disc sliding out from the sleeve + Cover Art
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                // Vinyl Record sliding out to the left
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .offset(x = (-38).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF11100F))
                        .border(1.dp, Color(0xFF33302C), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl grooves
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    // Vinyl center hole
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8D0AE))
                    )
                }

                // Album Cover Square with rounded right corners (Sleeve)
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                        .background(Color(0xFF221F1D))
                ) {
                    if (track?.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.moon_player_logo)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.moon_player_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        )
                    }

                    // Heart icon on top right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
