package com.rescue.mesh.android.routing

enum class DeliveryStatus {
    PENDING,
    IN_FLIGHT,
    SENT_WAITING_ACK,
    DELIVERED,
    FAILED,
    EXPIRED
}

sealed interface RoutingEvent {
    data class Received(val packetId: String, val type: String, val fromNodeId: String?) : RoutingEvent
    data class Forwarded(val packetId: String, val toNodeId: String, val hopCount: Int) : RoutingEvent
    data class DispatchReceived(val packet: com.rescue.mesh.android.protocol.MeshPacket) : RoutingEvent
    data class Delivered(val packetId: String) : RoutingEvent
    data class Queued(val packetId: String, val reason: String) : RoutingEvent
    data class Dropped(val packetId: String, val reason: String) : RoutingEvent
    data class Failed(val packetId: String, val reason: String) : RoutingEvent
}
