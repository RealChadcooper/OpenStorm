package com.openstorm.app.ui.map

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Compose wrapper around MapLibre GL Native MapView.
 *
 * Handles lifecycle, style loading, camera, and exposes the
 * MapLibreMap instance via [onMapReady] for overlay configuration.
 */
@Composable
fun OpenStormMapView(
    modifier: Modifier = Modifier,
    cameraState: CameraState = CameraState(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onMapReady: (MapLibreMap) -> Unit = {},
    onCameraIdle: (CameraState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre once
    remember { MapLibre.getInstance(context) }

    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

    val styleUrl = remember(isDarkTheme) {
        MapStyleBuilder.getStyleUrl(isDarkTheme)
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // React to dark/light style change
    LaunchedEffect(isDarkTheme) {
        mapLibreMap?.let { map ->
            val newStyleUrl = MapStyleBuilder.getStyleUrl(isDarkTheme)
            map.setStyle(Style.Builder().fromUri(newStyleUrl)) { style ->
                // Re-apply radar overlay after style change
                currentOnMapReady(map)
            }
        }
    }

    // React to external camera state changes
    LaunchedEffect(cameraState) {
        mapLibreMap?.let { map ->
            val target = LatLng(cameraState.latitude, cameraState.longitude)
            val currentPos = map.cameraPosition
            if (currentPos.target?.distanceTo(target)?.let { it > 100 } != false ||
                currentPos.zoom != cameraState.zoom
            ) {
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(target)
                            .zoom(cameraState.zoom)
                            .build()
                    ),
                    500
                )
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                mapView = this

                // Attribution in bottom-left, smaller
                getMapAsync { map ->
                    mapLibreMap = map

                    map.uiSettings.apply {
                        isCompassEnabled = true
                        setCompassMargins(0, 200, 32, 0)
                        isLogoEnabled = true
                        isAttributionEnabled = true
                        setAttributionGravity(Gravity.BOTTOM or Gravity.START)
                    }

                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        // Set initial camera
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(cameraState.latitude, cameraState.longitude))
                            .zoom(cameraState.zoom)
                            .build()

                        currentOnMapReady(map)
                    }

                    map.addOnCameraIdleListener {
                        val pos = map.cameraPosition
                        currentOnCameraIdle(
                            CameraState(
                                latitude = pos.target?.latitude ?: 0.0,
                                longitude = pos.target?.longitude ?: 0.0,
                                zoom = pos.zoom,
                                bearing = pos.bearing,
                                tilt = pos.tilt,
                            )
                        )
                    }
                }
            }
        },
        update = { _ ->
            // Updates handled via LaunchedEffects above
        },
    )
}

data class CameraState(
    val latitude: Double = 39.0,
    val longitude: Double = -98.0,
    val zoom: Double = 4.0,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
)
