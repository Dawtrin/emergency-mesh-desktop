package com.rescue.mesh.android.network

import com.rescue.mesh.android.protocol.MeshPacket
import kotlinx.coroutines.flow.SharedFlow

interface MeshTransport : AutoCloseable {
    val events: SharedFlow<TransportEvent>
    val isRunning: Boolean

    suspend fun start()
    suspend fun send(endpoint: PeerEndpoint, packet: MeshPacket): SendResult
    override fun close()
}
