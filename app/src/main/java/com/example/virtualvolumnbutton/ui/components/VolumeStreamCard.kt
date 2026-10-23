package com.example.virtualvolumnbutton.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme

/**
 * UI Card component displaying controls for a single audio stream category (Media, Ring, Alarm, Notification).
 *
 * WHAT IT DOES:
 * - Displays category label and current volume level ratio text (e.g. "8 / 15").
 * - Displays mute/unmute toggle button and lock/unlock toggle button.
 * - Displays a Material 3 [Slider] for smooth touch volume level adjustments.
 */
@Composable
fun VolumeStreamCard(
    streamType: AudioStreamType,
    volumeState: VolumeStreamState?,
    isLocked: Boolean,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onLockToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVolume = volumeState?.currentVolume ?: 0
    val maxVolume = (volumeState?.maxVolume ?: 15).coerceAtLeast(1)
    val minVolume = volumeState?.minVolume ?: 0
    val isMuted = volumeState?.isMuted ?: false

    // Select stream category icon
    val icon: ImageVector = when (streamType) {
        AudioStreamType.MEDIA -> Icons.Rounded.MusicNote
        AudioStreamType.RING -> Icons.Rounded.Notifications
        AudioStreamType.ALARM -> Icons.Rounded.Alarm
        AudioStreamType.NOTIFICATION -> Icons.AutoMirrored.Rounded.Message
    }

    // Select volume level dynamic icon
    val volumeIcon: ImageVector = when {
        isMuted || currentVolume == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
        currentVolume < maxVolume / 2 -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    val steps = (maxVolume - minVolume - 1).coerceAtLeast(0)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = streamType.label,
                        tint = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = streamType.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMuted) "Muted" else "$currentVolume / $maxVolume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
                    IconButton(onClick = onMuteToggle) {
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = if (isMuted) "Unmute ${streamType.label}" else "Mute ${streamType.label}",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Lock / Unlock Stream Button
                    IconToggleButton(
                        checked = isLocked,
                        onCheckedChange = { onLockToggle() }
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (isLocked) "Unlock ${streamType.label}" else "Lock ${streamType.label}",
                            tint = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Volume Level Slider
            Slider(
                value = currentVolume.toFloat().coerceIn(minVolume.toFloat(), maxVolume.toFloat()),
                onValueChange = { newValue ->
                    onVolumeChange(newValue.toInt())
                },
                valueRange = minVolume.toFloat()..maxVolume.toFloat(),
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    activeTrackColor = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = if (isLocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Preview for [VolumeStreamCard].
 */
@Preview(showBackground = true)
@Composable
fun VolumeStreamCardPreview() {
    VirtualVolumnButtonTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VolumeStreamCard(
                streamType = AudioStreamType.MEDIA,
                volumeState = VolumeStreamState(AudioStreamType.MEDIA, 8, 15),
                isLocked = false,
                onVolumeChange = {},
                onMuteToggle = {},
                onLockToggle = {}
            )
            VolumeStreamCard(
                streamType = AudioStreamType.RING,
                volumeState = VolumeStreamState(AudioStreamType.RING, 12, 15),
                isLocked = true,
                onVolumeChange = {},
                onMuteToggle = {},
                onLockToggle = {}
            )
        }
    }
}
