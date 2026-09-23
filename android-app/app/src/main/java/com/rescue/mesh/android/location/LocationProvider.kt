package com.rescue.mesh.android.location

import com.rescue.mesh.android.protocol.Location

data class LocationSample(
    val location: Location,
    val capturedAtMs: Long,
    val source: Source
) {
    enum class Source { GPS, MANUAL }
}

interface LocationProvider {
    suspend fun current(): Result<LocationSample>
}
