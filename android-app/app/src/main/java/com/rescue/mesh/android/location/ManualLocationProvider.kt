package com.rescue.mesh.android.location

import com.rescue.mesh.android.protocol.Location

/** Explicit user-supplied fallback when GPS is unavailable; never silently reuses stale GPS. */
class ManualLocationProvider(
    private val latitude: Double,
    private val longitude: Double,
    private val altitude: Double = 0.0,
    private val accuracy: Double = 0.0
) : LocationProvider {
    override suspend fun current(): Result<LocationSample> = runCatching {
        val location = Location(latitude, longitude, altitude, accuracy)
        require(location.latitude.isFinite() && location.latitude in -90.0..90.0)
        require(location.longitude.isFinite() && location.longitude in -180.0..180.0)
        LocationSample(location, System.currentTimeMillis(), LocationSample.Source.MANUAL)
    }
}
