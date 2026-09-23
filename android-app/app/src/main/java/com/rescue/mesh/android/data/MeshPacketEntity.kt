package com.rescue.mesh.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rescue.mesh.android.protocol.MeshPacket
import com.rescue.mesh.android.routing.DeliveryStatus

@Entity(
    tableName = "mesh_packets",
    indices = [Index(value = ["destinationNodeId"]), Index(value = ["status", "nextAttemptAt"])]
)
data class MeshPacketEntity(
    @PrimaryKey val packetId: String,
    val packetJson: String,
    val packetType: String,
    val sourceNodeId: String,
    val destinationNodeId: String,
    val status: String = DeliveryStatus.PENDING.name,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null
) {
    companion object {
        fun fromPacket(packet: MeshPacket, status: DeliveryStatus = DeliveryStatus.PENDING, now: Long = System.currentTimeMillis()) =
            MeshPacketEntity(
                packetId = requireNotNull(packet.packetId),
                packetJson = packet.toJson(),
                packetType = requireNotNull(packet.packetType),
                sourceNodeId = requireNotNull(packet.sourceNodeId),
                destinationNodeId = requireNotNull(packet.destinationNodeId),
                status = status.name,
                createdAt = now,
                updatedAt = now
            )
    }

    fun packet(): MeshPacket? = MeshPacket.fromJson(packetJson)
    fun deliveryStatus(): DeliveryStatus = runCatching { DeliveryStatus.valueOf(status) }.getOrDefault(DeliveryStatus.FAILED)
}
