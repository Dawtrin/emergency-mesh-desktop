package com.rescue.mesh.android.network

import java.net.InetAddress

/** A peer address learned from configuration, Wi-Fi Direct, or a handshake. */
data class PeerEndpoint(val nodeId: String, val host: String, val port: Int) {
    init {
        require(nodeId.isNotBlank() && nodeId.length <= 64) { "peer nodeId must be 1..64 characters" }
        require(host.isNotBlank()) { "peer host must not be blank" }
        require(port in 1..65_535) { "peer port must be from 1 to 65535" }
    }

    /** Accepts an IPv4/IPv6 literal or a DNS/hosts name; resolution is deferred. */
    fun validateHostSyntax() {
        require(host.length <= 253) { "peer host is too long" }
        require(host.none { it.isWhitespace() }) { "peer host must not contain whitespace" }
        if (host.contains(':') && runCatching { InetAddress.getByName(host) }.isFailure) {
            throw IllegalArgumentException("invalid peer host: $host")
        }
    }
}

sealed interface TransportEvent {
    val atMs: Long

    data class PeerConnected(
        val endpoint: PeerEndpoint,
        override val atMs: Long = System.currentTimeMillis()
    ) : TransportEvent

    data class PacketReceived(
        val endpoint: PeerEndpoint,
        val packetJson: String,
        override val atMs: Long = System.currentTimeMillis()
    ) : TransportEvent

    data class PeerDisconnected(
        val endpoint: PeerEndpoint?,
        val reason: String,
        override val atMs: Long = System.currentTimeMillis()
    ) : TransportEvent

    data class Error(
        val endpoint: PeerEndpoint?,
        val message: String,
        override val atMs: Long = System.currentTimeMillis()
    ) : TransportEvent
}

sealed interface SendResult {
    data class Sent(val endpoint: PeerEndpoint, val packetId: String?) : SendResult
    data class Failed(val endpoint: PeerEndpoint, val packetId: String?, val reason: String) : SendResult
}
