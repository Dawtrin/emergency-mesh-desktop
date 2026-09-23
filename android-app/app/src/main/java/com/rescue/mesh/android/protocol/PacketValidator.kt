package com.rescue.mesh.android.protocol

import java.util.UUID

data class ValidationResult(val valid: Boolean, val violations: List<String> = emptyList()) {
    val firstViolation: String get() = violations.firstOrNull() ?: ""
}

/** Structural and semantic validation at the Android socket boundary. */
object PacketValidator {
    const val MAX_FRAME_BYTES = 65_536
    const val MAX_MESSAGE_LENGTH = 500
    const val MAX_NODE_ID_LENGTH = 64
    const val MAX_TTL = 64

    private val validTypes = setOf(
        MeshPacket.TYPE_SOS_BROADCAST,
        MeshPacket.TYPE_DISPATCH_COMMAND,
        MeshPacket.TYPE_ROUTE_DISCOVERY,
        MeshPacket.TYPE_ACK,
        MeshPacket.TYPE_HEARTBEAT,
        MeshPacket.TYPE_SOS_DATA,
        MeshPacket.TYPE_DISPATCH_CMD
    )
    private val severities = setOf(
        MeshPacket.SEVERITY_CRITICAL,
        MeshPacket.SEVERITY_HIGH,
        MeshPacket.SEVERITY_MEDIUM
    )
    private val sosAlertTypes = setOf(MeshPacket.ALERT_MEDICAL, MeshPacket.ALERT_FLOOD, MeshPacket.ALERT_LANDSLIDE)

    fun validate(packet: MeshPacket?, referenceTimeMs: Long = System.currentTimeMillis()): ValidationResult {
        if (packet == null) return ValidationResult(false, listOf("MeshPacket cannot be null"))
        val violations = mutableListOf<String>()
        if (runCatching { UUID.fromString(packet.packetId ?: "") }.getOrNull() == null) violations += "packet_id must be a UUID"
        if (packet.protocolVersion != MeshPacket.PROTOCOL_VERSION_1) violations += "unsupported protocol_version"
        if (packet.packetType == null || packet.packetType !in validTypes) violations += "unknown packet_type"
        checkNode("source_node_id", packet.sourceNodeId, violations)
        checkNode("destination_node_id", packet.destinationNodeId, violations)
        checkNode("sender_hop_id", packet.senderHopId, violations)
        if (packet.ttl !in 0..MAX_TTL) violations += "ttl must be between 0 and $MAX_TTL"
        if (packet.hopCount < 0) violations += "hop_count cannot be negative"
        val history = packet.routeHistory
        if (history == null) violations += "route_history cannot be null"
        else {
            history.forEachIndexed { index, hop -> checkNode("route_history[$index]", hop, violations) }
            if (packet.hopCount < history.size) violations += "hop_count cannot be less than route_history size"
        }
        if (packet.timestamp <= 0) violations += "timestamp must be positive"
        else if (referenceTimeMs > 0 && (referenceTimeMs - packet.timestamp > 365L * 24 * 60 * 60 * 1000 || packet.timestamp - referenceTimeMs > 24L * 60 * 60 * 1000)) {
            violations += "timestamp outside accepted clock window"
        }

        val payload = packet.payload
        when (packet.packetType) {
            MeshPacket.TYPE_SOS_BROADCAST, MeshPacket.TYPE_SOS_DATA -> validateSosPayload(payload, violations)
            MeshPacket.TYPE_DISPATCH_COMMAND, MeshPacket.TYPE_DISPATCH_CMD -> validateDispatchPayload(payload, violations)
            MeshPacket.TYPE_ACK -> {
                val ackFor = payload?.ackForPacketId
                if (runCatching { UUID.fromString(ackFor ?: "") }.getOrNull() == null) violations += "ACK requires valid ack_for_packet_id"
            }
        }
        if (packet.checksum.isNullOrBlank() || packet.checksum!!.trim().length != 64) violations += "checksum must be 64 hex characters"
        return ValidationResult(violations.isEmpty(), violations)
    }

    private fun validateSosPayload(payload: Payload?, violations: MutableList<String>) {
        if (payload == null) {
            violations += "SOS payload is required"
            return
        }
        if (payload.senderName.isNullOrBlank() || payload.senderName!!.length > 100) violations += "SOS sender_name is invalid"
        if (payload.alertType !in sosAlertTypes) violations += "SOS alert_type is invalid"
        validateMessage(payload.message, violations)
        if (payload.victimCount < 0) violations += "victim_count cannot be negative"
        if (payload.severity !in severities) violations += "SOS severity is invalid"
        validateLocation(payload.location, violations)
    }

    private fun validateDispatchPayload(payload: Payload?, violations: MutableList<String>) {
        if (payload == null) {
            violations += "dispatch payload is required"
            return
        }
        validateMessage(payload.message, violations)
        if (payload.severity !in severities) violations += "dispatch severity is invalid"
    }

    private fun validateMessage(message: String?, violations: MutableList<String>) {
        if (message == null || message.length > MAX_MESSAGE_LENGTH) violations += "message must be 0..$MAX_MESSAGE_LENGTH characters"
    }

    private fun validateLocation(location: Location?, violations: MutableList<String>) {
        if (location == null) {
            violations += "SOS location is required"
            return
        }
        if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0) violations += "latitude is invalid"
        if (!location.longitude.isFinite() || location.longitude !in -180.0..180.0) violations += "longitude is invalid"
        if (!location.altitude.isFinite() || location.altitude !in -500.0..10_000.0) violations += "altitude is invalid"
        if (!location.accuracy.isFinite() || location.accuracy < 0.0) violations += "accuracy is invalid"
    }

    private fun checkNode(name: String, value: String?, violations: MutableList<String>) {
        if (value.isNullOrBlank() || value.length > MAX_NODE_ID_LENGTH) violations += "$name is blank or too long"
    }
}
