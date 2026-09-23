package com.rescue.mesh.android.protocol

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Canonical v1 SHA-256 implementation matching desktop ChecksumUtil. */
object ChecksumUtil {
    fun compute(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun computePacketChecksum(packet: MeshPacket): String = compute(buildCanonicalString(packet))

    fun verifyPacketChecksum(packet: MeshPacket): Boolean {
        val expected = packet.checksum?.trim()?.lowercase() ?: return false
        if (!Regex("^[0-9a-f]{64}$").matches(expected)) return false
        return runCatching {
            MessageDigest.isEqual(
                computePacketChecksum(packet).toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8)
            )
        }.getOrDefault(false)
    }

    fun buildCanonicalString(packet: MeshPacket): String = buildString {
        append('{')
        append("\"destination_node_id\":").appendJsonStringOrNull(packet.destinationNodeId).append(',')
        append("\"hop_count\":").append(packet.hopCount).append(',')
        append("\"packet_id\":").appendJsonStringOrNull(packet.packetId).append(',')
        append("\"packet_type\":").appendJsonStringOrNull(packet.packetType).append(',')
        append("\"payload\":").append(canonicalPayload(packet.payload)).append(',')
        append("\"protocol_version\":\"").append(escapeJson(packet.protocolVersion ?: MeshPacket.PROTOCOL_VERSION_1)).append("\",")
        append("\"route_history\":").append(canonicalRouteHistory(packet.routeHistory)).append(',')
        append("\"sender_hop_id\":").appendJsonStringOrNull(packet.senderHopId).append(',')
        append("\"source_node_id\":").appendJsonStringOrNull(packet.sourceNodeId).append(',')
        append("\"timestamp\":").append(packet.timestamp).append(',')
        append("\"ttl\":").append(packet.ttl)
        append('}')
    }

    private fun StringBuilder.appendJsonStringOrNull(value: String?): StringBuilder = apply {
        if (value == null) append("null") else append('"').append(escapeJson(value)).append('"')
    }

    fun canonicalRouteHistory(routeHistory: List<String>?): String = buildString {
        append('[')
        routeHistory.orEmpty().forEachIndexed { index, hop ->
            if (index > 0) append(',')
            append('"').append(escapeJson(hop)).append('"')
        }
        append(']')
    }

    fun canonicalPayload(payload: Payload?): String = buildString {
        if (payload == null) {
            append("{}")
            return@buildString
        }
        append('{')
        var first = true
        fun field(name: String, value: String) {
            if (!first) append(',') else first = false
            append('"').append(name).append("\":").append(value)
        }
        payload.ackForPacketId?.let { field("ack_for_packet_id", "\"${escapeJson(it)}\"") }
        payload.alertType?.let { field("alert_type", "\"${escapeJson(it)}\"") }
        payload.discoveryInfo?.takeUnless { it.isJsonNull }?.let { field("discovery_info", canonicalizeJsonElement(it)) }
        payload.location?.let {
            field(
                "location",
                "{\"accuracy\":${formatLocationDouble(it.accuracy)}," +
                    "\"altitude\":${formatLocationDouble(it.altitude)}," +
                    "\"latitude\":${formatLocationDouble(it.latitude)}," +
                    "\"longitude\":${formatLocationDouble(it.longitude)}}"
            )
        }
        payload.message?.let { field("message", "\"${escapeJson(it)}\"") }
        payload.senderName?.let { field("sender_name", "\"${escapeJson(it)}\"") }
        payload.severity?.let { field("severity", "\"${escapeJson(it)}\"") }
        field("victim_count", payload.victimCount.toString())
        append('}')
    }

    fun formatLocationDouble(value: Double): String {
        require(value.isFinite()) { "location contains NaN or Infinity" }
        return if (value == 0.0) "0.0" else value.toString()
    }

    fun canonicalizeNumericString(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 100) { "unsafe numeric value" }
        require(!trimmed.lowercase().contains("nan") && !trimmed.lowercase().contains("infinity")) {
            "non-finite numeric value"
        }
        val number = runCatching { BigDecimal(trimmed) }.getOrElse { throw IllegalArgumentException("invalid numeric value", it) }
        require(kotlin.math.abs(number.scale()) <= 100 && number.precision() <= 100) { "numeric value exceeds safety limits" }
        if (number.compareTo(BigDecimal.ZERO) == 0) return "0"
        return number.stripTrailingZeros().toPlainString()
    }

    private fun canonicalizeJsonElement(element: JsonElement): String = when {
        element.isJsonNull -> "null"
        element.isJsonPrimitive -> canonicalizePrimitive(element.asJsonPrimitive)
        element.isJsonArray -> element.asJsonArray.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalizeJsonElement(it)
        }
        element.isJsonObject -> element.asJsonObject.entrySet().sortedBy { it.key }.joinToString(
            prefix = "{", postfix = "}", separator = ","
        ) { "\"${escapeJson(it.key)}\":${canonicalizeJsonElement(it.value)}" }
        else -> error("unsupported JSON element")
    }

    private fun canonicalizePrimitive(primitive: JsonPrimitive): String = when {
        primitive.isBoolean -> primitive.asBoolean.toString()
        primitive.isNumber -> canonicalizeNumericString(primitive.asString)
        else -> "\"${escapeJson(primitive.asString)}\""
    }

    fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }
}
