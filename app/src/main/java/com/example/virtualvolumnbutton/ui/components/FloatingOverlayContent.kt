package com.example.virtualvolumnbutton.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.repository.VolumeRepository
import com.example.virtualvolumnbutton.engine.VolumeLockEngine

@Composable
fun FloatingOverlayContent(
    volumeRepository: VolumeRepository,
    volumeLockEngine: VolumeLockEngine,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onCloseService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val volumeStates by volumeRepository.volumeStates.collectAsState()
    val isMasterLock by volumeLockEngine.isLockActive.collectAsState()
    val lockedStreams by volumeLockEngine.lockedStreams.collectAsState()

    var activeStream by remember { mutableStateOf(AudioStreamType.MEDIA) }
    var isExpanded by remember { mutableStateOf(false) }

    val activeState = volumeStates[activeStream] ?: volumeRepository.getVolumeState(activeStream)
    val isStreamLocked = isMasterLock || lockedStreams.contains(activeStream)

    Surface(
        modifier = modifier
            .padding(4.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header / Collapsed Pill Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Drag handle icon
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Drag to move",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                // Current Volume Icon
                Icon(
                    imageVector = getStreamIcon(activeStream, activeState.currentVolume, activeState.isMuted),
                    contentDescription = activeStream.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                // RED CIRCLE LOCK ICON BADGE when locked!
                LockIconBadge(
                    isLocked = isStreamLocked,
                    onClick = {
                        if (isStreamLocked) {
                            if (isMasterLock) {
                                volumeLockEngine.disableLock()
                            }
                            volumeLockEngine.unlockStream(activeStream)
                        } else {
                            volumeLockEngine.lockStream(activeStream)
                        }
                    }
                )

                // Touch Volume Down (-) Button
                IconButton(
                    onClick = {
                        val prevLevel = (activeState.currentVolume - 1).coerceAtLeast(activeState.minVolume)
                        if (isStreamLocked) {
                            volumeLockEngine.setLockedVolume(activeStream, prevLevel)
                        } else {
                            volumeRepository.setVolume(activeStream, prevLevel)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Volume Down",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Volume Level Text
                Text(
                    text = "${activeState.currentVolume}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // Touch Volume Up (+) Button
                IconButton(
                    onClick = {
                        val nextLevel = (activeState.currentVolume + 1).coerceAtMost(activeState.maxVolume)
                        if (isStreamLocked) {
                            volumeLockEngine.setLockedVolume(activeStream, nextLevel)
                        } else {
                            volumeRepository.setVolume(activeStream, nextLevel)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Volume Up",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Expand / Collapse Arrow Button
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Expanded Controls Panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stream Selector Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AudioStreamType.entries.forEach { stream ->
                            val isSelected = activeStream == stream
                            val isLocked = isMasterLock || lockedStreams.contains(stream)

                            FilterChip(
                                selected = isSelected,
                                onClick = { activeStream = stream },
                                label = {
                                    Text(
                                        text = stream.label.take(3),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(0xFFDC2626), CircleShape)
                                        )
                                    }
                                },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    // Progress Bar Indicator
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${activeStream.label} Volume",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${activeState.currentVolume} / ${activeState.maxVolume}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (activeState.maxVolume > 0) {
                                    activeState.currentVolume.toFloat() / activeState.maxVolume.toFloat()
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isStreamLocked) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Bottom Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Mute
                        IconButton(
                            onClick = {
                                volumeRepository.setMute(activeStream, !activeState.isMuted)
                            }
                        ) {
                            Icon(
                                imageVector = if (activeState.isMuted) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeDown,
                                contentDescription = "Mute Toggle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Toggle Stream / Master Lock
                        ElevatedCard(
                            onClick = {
                                if (isStreamLocked) {
                                    if (isMasterLock) {
                                        volumeLockEngine.disableLock()
                                    }
                                    volumeLockEngine.unlockStream(activeStream)
                                } else {
                                    volumeLockEngine.lockStream(activeStream)
                                }
                            },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isStreamLocked) Color(0xFFDC2626) else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isStreamLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = if (isStreamLocked) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isStreamLocked) "Locked" else "Lock Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStreamLocked) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Close Overlay Service Button
                        IconButton(onClick = onCloseService) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Overlay",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lock Icon Badge Component
 * Requirement: Red circle lock icon when locked!
 */
@Composable
fun LockIconBadge(
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isLocked) {
        Color(0xFFDC2626) // Prominent Red Circle
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val iconColor = if (isLocked) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            contentDescription = if (isLocked) "Volume Locked" else "Volume Unlocked",
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun getStreamIcon(streamType: AudioStreamType, level: Int, isMuted: Boolean): ImageVector {
    if (isMuted || level == 0) return Icons.AutoMirrored.Rounded.VolumeMute
    return when (streamType) {
        AudioStreamType.MEDIA -> if (level > 8) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown
        AudioStreamType.RING -> Icons.Rounded.Notifications
        AudioStreamType.ALARM -> Icons.Rounded.Alarm
        AudioStreamType.NOTIFICATION -> Icons.Rounded.Notifications
    }
}
