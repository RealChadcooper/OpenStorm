package com.openstorm.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Provides device location using Google Play Services FusedLocationProviderClient.
 *
 * Callers must ensure location permission is granted before calling [getLastLocation]
 * or [getCurrentLocation].
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Get the device's last known location (fast, may be null/stale).
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    cont.resume(location)
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Failed to get last location")
                    cont.resume(null)
                }
        }
    }

    /**
     * Actively request a fresh location fix. More accurate but slower.
     * Falls back to [getLastLocation] if the fresh request fails.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val cancellationSource = CancellationTokenSource()

        return try {
            suspendCancellableCoroutine { cont ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationSource.token,
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        // Fresh fix returned null — fall back
                        cont.resume(null)
                    }
                }.addOnFailureListener { e ->
                    Timber.w(e, "Failed to get current location")
                    cont.resume(null)
                }

                cont.invokeOnCancellation {
                    cancellationSource.cancel()
                }
            } ?: getLastLocation()
        } catch (e: Exception) {
            Timber.w(e, "getCurrentLocation exception, falling back to last")
            getLastLocation()
        }
    }

    companion object {
        // Fallback: geographic center of contiguous US
        const val DEFAULT_LAT = 39.0
        const val DEFAULT_LON = -98.0
    }
}
