package com.openstorm.app.ui.radar

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.openstorm.app.ui.map.AlertOverlayController
import com.openstorm.app.ui.map.CameraState
import com.openstorm.app.ui.map.OpenStormMapView
import com.openstorm.app.ui.map.RadarOverlayController
import com.openstorm.app.ui.map.StationMarkerController
import com.openstorm.core.domain.model.RadarProduct
import org.maplibre.android.maps.MapLibreMap

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    // Controllers survive recompositions but not lifecycle destruction
    val radarOverlay = remember { RadarOverlayController() }
    val alertOverlay = remember { AlertOverlayController() }
    val stationMarkers = remember { StationMarkerController() }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // ── Location permission handling ──
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )

    // On first composition: request permission if not yet determined
    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted &&
            !locationPermissions.permissions.any { it.status.shouldShowRationale }
        ) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // React to permission changes — tell the ViewModel
    val anyGranted = locationPermissions.permissions.any { it.status.isGranted }
    LaunchedEffect(anyGranted, locationPermissions.allPermissionsGranted) {
        // Either fine or coarse is sufficient
        val granted = locationPermissions.permissions.any { it.status.isGranted }
        viewModel.onLocationPermissionResult(granted)
    }

    // If permission was denied without rationale shown, still initialize with defaults
    LaunchedEffect(locationPermissions.shouldShowRationale) {
        if (!uiState.locationInitialized &&
            !locationPermissions.allPermissionsGranted &&
            !locationPermissions.shouldShowRationale
        ) {
            // All permissions permanently denied — use fallback
            viewModel.onLocationPermissionResult(false)
        }
    }

    // React to frame changes — update radar overlay
    LaunchedEffect(uiState.currentTileUrl) {
        val map = mapInstance ?: return@LaunchedEffect
        val tileUrl = uiState.currentTileUrl ?: return@LaunchedEffect
        radarOverlay.showFrame(map, tileUrl)
    }

    // React to radar opacity changes
    LaunchedEffect(uiState.radarOpacity) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        radarOverlay.setOpacity(style, uiState.radarOpacity)
    }

    // React to alert data changes
    LaunchedEffect(uiState.alerts, uiState.showAlertOverlay) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (uiState.showAlertOverlay) {
            alertOverlay.updateAlerts(style, uiState.alerts)
        } else {
            alertOverlay.updateAlerts(style, emptyList())
        }
    }

    // React to station changes
    LaunchedEffect(uiState.nearbyStations, uiState.station) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        stationMarkers.updateStations(style, uiState.nearbyStations)
        stationMarkers.updateActiveStation(style, uiState.station)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Map fills the entire screen ──
        OpenStormMapView(
            modifier = Modifier.fillMaxSize(),
            cameraState = uiState.cameraState,
            isDarkTheme = isDark,
            onMapReady = { map ->
                mapInstance = map
                val style = map.style ?: return@OpenStormMapView
                radarOverlay.initialize(map, style)
                alertOverlay.initialize(style)
                stationMarkers.initialize(style)

                // If we already have data, apply it immediately
                uiState.currentTileUrl?.let { radarOverlay.showFrame(map, it) }
                stationMarkers.updateStations(style, uiState.nearbyStations)
                stationMarkers.updateActiveStation(style, uiState.station)
                if (uiState.showAlertOverlay) {
                    alertOverlay.updateAlerts(style, uiState.alerts)
                }
            },
            onCameraIdle = viewModel::onCameraIdle,
        )

        // ── Loading overlay ──
        AnimatedVisibility(
            visible = uiState.isLoading && uiState.frames.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(32.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading radar…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        // ── Error overlay ──
        AnimatedVisibility(
            visible = uiState.error != null && uiState.frames.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = uiState.error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(24.dp),
            )
        }

        // ── Location permission rationale ──
        AnimatedVisibility(
            visible = locationPermissions.shouldShowRationale &&
                !locationPermissions.permissions.any { it.status.isGranted },
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Location access shows your nearest radar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { locationPermissions.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Top bar: station info ──
        AnimatedVisibility(
            visible = uiState.station != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            StationInfoBar(
                stationName = uiState.station?.name ?: "",
                stationId = uiState.station?.id ?: "",
                productName = uiState.selectedProduct.name,
                lastUpdated = uiState.lastUpdated
                    ?.substringAfter("T")
                    ?.substringBefore(".")
                    ?.let { "${it}Z" },
                alertCount = uiState.alerts.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ── Right side: product selector + layer toggles ──
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ProductSelector(
                selectedProduct = uiState.selectedProduct,
                onProductSelected = viewModel::selectProduct,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Alert overlay toggle
            MapActionButton(
                icon = Icons.Default.Warning,
                contentDescription = "Toggle alerts",
                isActive = uiState.showAlertOverlay,
                onClick = viewModel::toggleAlertOverlay,
            )

            // Recenter on device location
            MapActionButton(
                icon = if (uiState.locationPermission == LocationPermissionState.GRANTED)
                    Icons.Default.MyLocation else Icons.Default.LocationOff,
                contentDescription = "Center on my location",
                isActive = false,
                onClick = {
                    if (uiState.locationPermission == LocationPermissionState.GRANTED) {
                        viewModel.recenterOnDeviceLocation()
                    } else {
                        locationPermissions.launchMultiplePermissionRequest()
                    }
                },
            )
        }

        // ── Bottom: playback controls ──
        AnimatedVisibility(
            visible = uiState.frames.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
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
                    ?.timestamp?.toString()
                    ?.substringAfter("T")
                    ?.substringBefore(".")
                    ?.let { "${it}Z" } ?: "",
                radarOpacity = uiState.radarOpacity,
                onOpacityChange = viewModel::setRadarOpacity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ── Data attribution (bottom-left, above MapLibre logo) ──
        Text(
            text = "Data: NOAA/NWS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 100.dp),
        )
    }
}

@Composable
private fun StationInfoBar(
    stationName: String,
    stationId: String,
    productName: String,
    lastUpdated: String?,
    alertCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stationId,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (lastUpdated != null) {
                    Text(
                        text = "  ·  $lastUpdated",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (alertCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$alertCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
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
        horizontalAlignment = Alignment.End,
    ) {
        RadarProduct.MVP.forEach { product ->
            FilterChip(
                selected = product.code == selectedProduct.code,
                onClick = { onProductSelected(product) },
                label = {
                    Text(
                        text = product.code,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

@Composable
private fun MapActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
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
    radarOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Frame scrubber row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = onToggleLoop,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isLooping) "Pause loop" else "Play loop",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (frameCount > 1) {
                Slider(
                    value = currentFrame.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..(frameCount - 1).toFloat(),
                    steps = (frameCount - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = frameTimestamp,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh radar",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Opacity slider row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Radar opacity",
                modifier = Modifier
                    .size(16.dp)
                    .padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Opacity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Slider(
                value = radarOpacity,
                onValueChange = onOpacityChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                ),
            )
            Text(
                text = "${(radarOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
