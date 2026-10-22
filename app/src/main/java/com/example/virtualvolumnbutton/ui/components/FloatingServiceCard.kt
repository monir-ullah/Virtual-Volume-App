package com.example.virtualvolumnbutton.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme

@Composable
fun FloatingServiceCard(
    isServiceEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onToggleService: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isServiceEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isServiceEnabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartButton,
                    contentDescription = "Floating Button Service",
                    tint = if (isServiceEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Floating Volume Button",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = when {
                            !hasOverlayPermission -> "Requires Overlay Permission"
                            isServiceEnabled -> "Floating virtual button is active"
                            else -> "Display virtual button overlay on screen"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!hasOverlayPermission) MaterialTheme.colorScheme.error else contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isServiceEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !hasOverlayPermission) {
                        onRequestOverlayPermission()
                    } else {
                        onToggleService(enabled)
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingServiceCardPreview() {
    VirtualVolumnButtonTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            FloatingServiceCard(
                isServiceEnabled = true,
                hasOverlayPermission = true,
                onToggleService = {},
                onRequestOverlayPermission = {}
            )
            Spacer(modifier = Modifier.padding(8.dp))
            FloatingServiceCard(
                isServiceEnabled = false,
                hasOverlayPermission = false,
                onToggleService = {},
                onRequestOverlayPermission = {}
            )
        }
    }
}
