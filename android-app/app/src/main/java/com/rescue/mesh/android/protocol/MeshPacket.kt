package com.rescue.mesh.android.protocol

import com.google.gson.Gson
import com.google.gson.JsonElement
import java.nio.charset.StandardCharsets
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Protocol v1 packet shared with the Java desktop application.
 *
 * Wire names deliberately use snake_case and the checksum is calculated by
 * [ChecksumUtil] over the canonical representation, not over Gson's field
 * order.  The model is nullable at the wire boundary so malformed input can
 * be rejected by [PacketValidator] instead of crashing the socket service.
 */
data class MeshPacket(
    @SerializedName(value = "packet_id", alternate = ["packetId"])
    var packetId: String? = null,
    @SerializedName(value = "protocol_version", alternate = ["protocolVersion"])
    var protocolVersion: String? = PROTOCOL_VERSION_1,
    @SerializedName(value = "packet_type", alternate = ["packetType"])
    var packetType: String? = null,
    @SerializedName(value = "source_node_id", alternate = ["sourceNodeId"])
    var sourceNodeId: String? = null,
    @SerializedName(value = "destination_node_id", alternate = ["destinationNodeId"])
    var destinationNodeId: String? = null,
    @SerializedName(value = "sender_hop_id", alternate = ["senderHopId"])
    var senderHopId: String? = null,
    var ttl: Int = 0,
    @SerializedName(value = "hop_count", alternate = ["hopCount"])
    var hopCount: Int = 0,
    var timestamp: Long = 0L,
    var payload: Payload? = null,
    @SerializedName(value = "route_history", alternate = ["routeHistory"])
    var routeHistory: MutableList<String>? = mutableListOf(),
    var checksum: String? = null
) {
    fun normalize(): MeshPacket {
        packetType = when (packetType) {
            TYPE_SOS_DATA -> TYPE_SOS_BROADCAST
            TYPE_DISPATCH_CMD -> TYPE_DISPATCH_COMMAND
            else -> packetType
        }
        if (protocolVersion.isNullOrBlank()) protocolVersion = PROTOCOL_VERSION_1
        if (routeHistory == null) routeHistory = mutableListOf()
        return this
    }

    fun toJson(): String = GSON.toJson(this)

    fun computeAndSetChecksum(): String {
        checksum = ChecksumUtil.computePacketChecksum(this)
        return checksum!!
    }

    fun verifyChecksum(): Boolean = ChecksumUtil.verifyPacketChecksum(this)

    fun copyForForwarding(nodeId: String): MeshPacket {
        require(nodeId.isNotBlank()) { "forwarding node id must not be blank" }
        val forwarded = fromJson(toJson()) ?: error("cannot copy packet")
        forwarded.ttl -= 1
        forwarded.hopCount += 1
        val history = forwarded.routeHistory ?: mutableListOf()
        history += nodeId
        forwarded.routeHistory = history
        forwarded.senderHopId = nodeId
        forwarded.computeAndSetChecksum()
        return forwarded
    }

    companion object {
        const val PROTOCOL_VERSION_1 = "1.0"
        const val TYPE_SOS_BROADCAST = "SOS_BROADCAST"
        const val TYPE_DISPATCH_COMMAND = "DISPATCH_COMMAND"
        const val TYPE_ROUTE_DISCOVERY = "ROUTE_DISCOVERY"
        const val TYPE_ACK = "ACK"
        const val TYPE_HEARTBEAT = "HEARTBEAT"
        @Deprecated("Use TYPE_SOS_BROADCAST") const val TYPE_SOS_DATA = "SOS_DATA"
        @Deprecated("Use TYPE_DISPATCH_COMMAND") const val TYPE_DISPATCH_CMD = "DISPATCH_CMD"

        const val NODE_BASE_STATION = "BASE_STATION"
        const val NODE_BROADCAST = "BROADCAST"
        const val SEVERITY_CRITICAL = "CRITICAL"
        const val SEVERITY_HIGH = "HIGH"
        const val SEVERITY_MEDIUM = "MEDIUM"
        const val ALERT_MEDICAL = "MEDICAL"
        const val ALERT_FLOOD = "FLOOD_TRAPPED"
        const val ALERT_LANDSLIDE = "LANDSLIDE"
        const val DEFAULT_TTL = 5

        private val GSON = Gson()

        fun newSos(
            sourceNodeId: String,
            senderName: String,
            alertType: String,
            message: String,
            victimCount: Int,
            severity: String,
            location: Location
        ): MeshPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            protocolVersion = PROTOCOL_VERSION_1,
            packetType = TYPE_SOS_BROADCAST,
            sourceNodeId = sourceNodeId,
            destinationNodeId = NODE_BASE_STATION,
            senderHopId = sourceNodeId,
            ttl = DEFAULT_TTL,
            hopCount = 0,
            timestamp = System.currentTimeMillis(),
            payload = Payload(senderName, alertType, message, victimCount, severity, location),
            routeHistory = mutableListOf()
        ).also { it.computeAndSetChecksum() }

        fun newAck(myNodeId: String, originalPacketId: String, destinationNodeId: String): MeshPacket =
            MeshPacket(
                // ACK identity is stable for a (receiver, original packet)
                // pair.  A duplicate SOS can therefore reuse the durable ACK
                // outbox row instead of creating an unbounded stream of ACKs.
                packetId = UUID.nameUUIDFromBytes(
                    "ACK|$myNodeId|$originalPacketId".toByteArray(StandardCharsets.UTF_8)
                ).toString(),
                protocolVersion = PROTOCOL_VERSION_1,
                packetType = TYPE_ACK,
                sourceNodeId = myNodeId,
                destinationNodeId = destinationNodeId,
                senderHopId = myNodeId,
                ttl = DEFAULT_TTL,
                hopCount = 0,
                timestamp = System.currentTimeMillis(),
                payload = Payload(
                    senderName = myNodeId,
                    alertType = TYPE_ACK,
                    message = "ACK for packet: $originalPacketId",
                    victimCount = 0,
                    severity = SEVERITY_MEDIUM,
                    location = Location(),
                    ackForPacketId = originalPacketId
                ),
                routeHistory = mutableListOf()
            ).also { it.computeAndSetChecksum() }

        fun newDispatch(
            destinationNodeId: String,
            message: String,
            severity: String = SEVERITY_CRITICAL
        ): MeshPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            protocolVersion = PROTOCOL_VERSION_1,
            packetType = TYPE_DISPATCH_COMMAND,
            sourceNodeId = NODE_BASE_STATION,
            destinationNodeId = destinationNodeId,
            senderHopId = NODE_BASE_STATION,
            ttl = DEFAULT_TTL,
            hopCount = 0,
            timestamp = System.currentTimeMillis(),
            payload = Payload(
                senderName = "BASE_STATION_COMMANDER",
                alertType = "DISPATCH",
                message = message,
                victimCount = 0,
                severity = severity,
                location = Location()
            ),
            routeHistory = mutableListOf()
        ).also { it.computeAndSetChecksum() }

        fun fromJson(json: String): MeshPacket? = runCatching {
            if (json.isBlank()) null else GSON.fromJson(json, MeshPacket::class.java)?.normalize()
        }.getOrNull()

        fun fromJsonElement(element: JsonElement): MeshPacket? = runCatching {
            GSON.fromJson(element, MeshPacket::class.java)?.normalize()
        }.getOrNull()
    }
}

data class Payload(
    @SerializedName(value = "sender_name", alternate = ["senderName"])
    var senderName: String? = null,
    @SerializedName(value = "alert_type", alternate = ["alertType"])
    var alertType: String? = null,
    var message: String? = null,
    @SerializedName(value = "victim_count", alternate = ["victimCount"])
    var victimCount: Int = 0,
    var severity: String? = null,
    var location: Location? = null,
    @SerializedName(value = "ack_for_packet_id", alternate = ["ackForPacketId"])
    var ackForPacketId: String? = null,
    @SerializedName(value = "discovery_info", alternate = ["discoveryInfo"])
    var discoveryInfo: JsonElement? = null
)

data class Location(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var altitude: Double = 0.0,
    var accuracy: Double = 0.0
)

enum class PacketType(val wireName: String) {
    SOS_BROADCAST(MeshPacket.TYPE_SOS_BROADCAST),
    DISPATCH_COMMAND(MeshPacket.TYPE_DISPATCH_COMMAND),
    ROUTE_DISCOVERY(MeshPacket.TYPE_ROUTE_DISCOVERY),
    ACK(MeshPacket.TYPE_ACK),
    HEARTBEAT(MeshPacket.TYPE_HEARTBEAT);

    companion object {
        fun fromWire(value: String?): PacketType? = entries.firstOrNull { it.wireName == value }
    }
}
