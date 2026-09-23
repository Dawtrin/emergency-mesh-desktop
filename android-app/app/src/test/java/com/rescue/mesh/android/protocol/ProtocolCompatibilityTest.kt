package com.rescue.mesh.android.protocol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCompatibilityTest {
    @Test
    fun javaSosFixtureHasTheSameCanonicalChecksum() {
        val packet = MeshPacket.fromJson(SOS_FIXTURE)
        assertNotNull(packet)
        assertEquals("cd6cbc65ac85b049082c25193cdf1e15269bea578fa59c6cd2e4d41f02bcc009", packet!!.checksum)
        assertTrue(PacketValidator.validate(packet, -1L).valid)
        assertTrue(packet.verifyChecksum())
    }

    @Test
    fun metadataTamperIsRejected() {
        val packet = MeshPacket.fromJson(SOS_FIXTURE)!!
        packet.ttl = packet.ttl - 1
        assertFalse(packet.verifyChecksum())
    }

    @Test
    fun discoveryNumbersRemainExactAndDeterministic() {
        val packet = MeshPacket(
            packetId = "7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811",
            packetType = MeshPacket.TYPE_ROUTE_DISCOVERY,
            sourceNodeId = "NODE_A",
            destinationNodeId = MeshPacket.NODE_BROADCAST,
            senderHopId = "NODE_A",
            ttl = 5,
            timestamp = 1L,
            payload = Payload(
                senderName = "NODE_A",
                alertType = MeshPacket.TYPE_ROUTE_DISCOVERY,
                message = "discover",
                discoveryInfo = JsonParser.parseString("{\"big\":9007199254740993,\"small\":0.10000000000000001}")
            )
        )
        val first = ChecksumUtil.buildCanonicalString(packet)
        val second = ChecksumUtil.buildCanonicalString(packet)
        assertEquals(first, second)
        assertTrue(first.contains("9007199254740993"))
        assertTrue(first.contains("0.10000000000000001"))
    }

    companion object {
        private val SOS_FIXTURE = """
            {
              "packet_id": "7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811",
              "protocol_version": "1.0",
              "packet_type": "SOS_BROADCAST",
              "source_node_id": "ANDROID_VICTIM_01",
              "destination_node_id": "BASE_STATION",
              "sender_hop_id": "ANDROID_RELAY_02",
              "ttl": 4,
              "hop_count": 2,
              "timestamp": 1771239999000,
              "payload": {
                "sender_name": "Người dân Đồi Cọ",
                "alert_type": "FLOOD_TRAPPED",
                "message": "Nước lũ dâng ngập mái nhà, có 2 trẻ nhỏ cần cứu hộ gấp!",
                "victim_count": 4,
                "severity": "CRITICAL",
                "location": {"latitude": 16.074512, "longitude": 108.150245, "altitude": 15.2, "accuracy": 3.5}
              },
              "route_history": ["ANDROID_VICTIM_01", "ANDROID_RELAY_01"],
              "checksum": "cd6cbc65ac85b049082c25193cdf1e15269bea578fa59c6cd2e4d41f02bcc009"
            }
        """.trimIndent()
    }
}
