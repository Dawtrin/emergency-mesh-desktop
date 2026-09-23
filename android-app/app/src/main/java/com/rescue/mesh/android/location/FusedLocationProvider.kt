package com.rescue.mesh.android.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.rescue.mesh.android.protocol.Location as MeshLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Real GPS provider with a bounded request and an explicit failure result. */
class FusedLocationProvider(context: Context) : LocationProvider {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun current(): Result<LocationSample> = runCatching {
        check(hasPermission()) { "Location permission is not granted" }
        val androidLocation: AndroidLocation = withTimeout(10_000L) {
            suspendCancellableCoroutine<AndroidLocation> { continuation ->
                val cancellation = CancellationTokenSource()
                val task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                task.addOnSuccessListener { value: AndroidLocation? ->
                    if (continuation.isActive) {
                        if (value != null) continuation.resume(value)
                        else continuation.resumeWith(Result.failure(IllegalStateException("GPS returned no fix")))
                    }
                }
                task.addOnFailureListener { error: Exception ->
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }
                continuation.invokeOnCancellation { cancellation.cancel() }
            }
        }
        LocationSample(
            location = MeshLocation(
                latitude = androidLocation.latitude,
                longitude = androidLocation.longitude,
                altitude = androidLocation.altitude,
                accuracy = androidLocation.accuracy.toDouble()
            ),
            capturedAtMs = androidLocation.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            source = LocationSample.Source.GPS
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
