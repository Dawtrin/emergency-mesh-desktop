package com.rescue.mesh.android.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketValidatorTest {
    @Test
    fun sosLocationAndBoundsAreValidated() {
        val packet = MeshPacket.newSos(
            sourceNodeId = "ANDROID_VICTIM_01",
            senderName = "Victim",
            alertType = MeshPacket.ALERT_FLOOD,
            message = "Need rescue",
            victimCount = 2,
            severity = MeshPacket.SEVERITY_CRITICAL,
            location = Location(16.0, 108.0, 15.0, 3.5)
        )
        assertTrue(PacketValidator.validate(packet, System.currentTimeMillis()).valid)
        packet.payload!!.location!!.latitude = 91.0
        assertFalse(PacketValidator.validate(packet, System.currentTimeMillis()).valid)
    }

    @Test
    fun unsupportedVersionAndMissingChecksumFail() {
        val packet = MeshPacket.newSos("NODE_A", "Victim", MeshPacket.ALERT_MEDICAL, "", 0, MeshPacket.SEVERITY_MEDIUM, Location())
        packet.protocolVersion = "2.0"
        packet.checksum = null
        assertFalse(PacketValidator.validate(packet, System.currentTimeMillis()).valid)
    }
}
