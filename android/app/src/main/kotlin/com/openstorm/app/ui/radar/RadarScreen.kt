package com.openstorm.app.ui.radar

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.openstorm.core.domain.model.RadarProduct

@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Default to Oklahoma City coordinates as fallback
    // Real implementation would request location permission and use GPS
    LaunchedEffect(Unit) {
        viewModel.loadNearestStation(35.22, -97.44)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map placeholder — MapLibre integration goes here
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading radar…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            } else {
                // Placeholder for MapLibre map view
                Text(
                    text = "🗺️ Map View\n${uiState.station?.name ?: "No Station"}\n" +
                        "${uiState.frames.size} frames loaded",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        // Top bar: station info
        AnimatedVisibility(
            visible = uiState.station != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            StationInfoBar(
                stationName = uiState.station?.name ?: "",
                stationId = uiState.station?.id ?: "",
                lastUpdated = uiState.lastUpdated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Product selector chips
        ProductSelector(
            selectedProduct = uiState.selectedProduct,
            onProductSelected = viewModel::selectProduct,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 8.dp),
        )

        // Bottom controls: playback
        AnimatedVisibility(
            visible = uiState.frames.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlaybackControls(
                frameCount = uiState.frames.size,
                currentFrame = uiState.currentFrameIndex,
                isLooping = uiState.isLooping,
                onToggleLoop = viewModel::toggleLoop,
                onSeek = viewModel::seekToFrame,
                onRefresh = viewModel::refresh,
                frameTimestamp = uiState.frames.getOrNull(uiState.currentFrameIndex)
                    ?.timestamp?.toString()?.substringAfter("T")?.substringBefore("Z") ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StationInfoBar(
    stationName: String,
    stationId: String,
    lastUpdated: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$stationName ($stationId)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (lastUpdated != null) {
                Text(
                    text = "Updated: $lastUpdated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = "NOAA/NWS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ProductSelector(
    selectedProduct: RadarProduct,
    onProductSelected: (RadarProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadarProduct.MVP.forEach { product ->
            FilterChip(
                selected = product.code == selectedProduct.code,
                onClick = { onProductSelected(product) },
                label = {
                    Text(
                        text = product.code,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    frameCount: Int,
    currentFrame: Int,
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    onSeek: (Int) -> Unit,
    onRefresh: () -> Unit,
    frameTimestamp: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onToggleLoop) {
                Icon(
                    imageVector = if (isLooping) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isLooping) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (frameCount > 1) {
                Slider(
                    value = currentFrame.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..(frameCount - 1).toFloat(),
                    steps = frameCount - 2,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = frameTimestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
