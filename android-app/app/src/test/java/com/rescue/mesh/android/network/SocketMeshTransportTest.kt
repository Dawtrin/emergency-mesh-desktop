package com.rescue.mesh.android.network

import com.rescue.mesh.android.protocol.Location
import com.rescue.mesh.android.protocol.MeshPacket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketMeshTransportTest {
    @Test
    fun transportsExchangeHandshakeAndUtf8PacketOnLoopback() = runBlocking {
        val sender = SocketMeshTransport("ANDROID_SENDER", listenPort = 0)
        val receiver = SocketMeshTransport("ANDROID_RECEIVER", listenPort = 0)
        val received = CompletableDeferred<TransportEvent.PacketReceived>()
        val collector = launch {
            receiver.events.collect { event ->
                if (event is TransportEvent.PacketReceived) received.complete(event)
            }
        }
        yield() // subscribe to the replay=0 transport event stream before sending
        delay(100)
        try {
            sender.start()
            receiver.start()
            assertTrue(sender.localPort > 0)
            assertTrue(receiver.localPort > 0)

            val packet = MeshPacket.newSos(
                sourceNodeId = "ANDROID_SENDER",
                senderName = "Người báo",
                alertType = MeshPacket.ALERT_FLOOD,
                message = "Cần cứu hộ khẩn cấp",
                victimCount = 2,
                severity = MeshPacket.SEVERITY_CRITICAL,
                location = Location(16.074512, 108.150245, accuracy = 3.5)
            )
            val result = sender.send(
                PeerEndpoint("ANDROID_RECEIVER", "127.0.0.1", receiver.localPort),
                packet
            )
            assertTrue("send should complete", result is SendResult.Sent)

            val event = withTimeout(5_000L) { received.await() }
            assertEquals("ANDROID_SENDER", event.endpoint.nodeId)
            assertEquals(packet.packetId, MeshPacket.fromJson(event.packetJson)?.packetId)
            assertEquals(packet.payload?.message, MeshPacket.fromJson(event.packetJson)?.payload?.message)
        } finally {
            sender.close()
            receiver.close()
            collector.cancel()
        }
    }
}
