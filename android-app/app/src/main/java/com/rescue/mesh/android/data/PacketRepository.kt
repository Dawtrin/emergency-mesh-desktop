package com.rescue.mesh.android.data

import com.google.gson.Gson
import com.rescue.mesh.android.protocol.MeshPacket
import com.rescue.mesh.android.routing.DeliveryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary used by the routing engine.  Keeping this contract
 * separate from the Room adapter lets the delivery state machine be tested
 * deterministically without pretending an in-memory test is a device test.
 */
interface PacketStore {
    suspend fun enqueue(packet: MeshPacket, now: Long = System.currentTimeMillis()): Boolean
    suspend fun saveReceived(
        packet: MeshPacket,
        status: DeliveryStatus = DeliveryStatus.DELIVERED,
        now: Long = System.currentTimeMillis()
    )
    suspend fun find(packetId: String): MeshPacketEntity?
    suspend fun due(now: Long, limit: Int = 16): List<MeshPacketEntity>
    suspend fun prune(before: Long): Int
    suspend fun updateStatus(
        packetId: String,
        status: DeliveryStatus,
        attempts: Int,
        nextAttemptAt: Long = 0L,
        detail: String? = null,
        now: Long = System.currentTimeMillis()
    )
    suspend fun upsertNode(node: NodeEntity)
    suspend fun storedNodes(): List<NodeEntity>
    fun observePackets(): Flow<List<MeshPacketEntity>>
    fun observeStatuses(): Flow<List<MessageStatusEntity>>
    fun observeNodes(): Flow<List<NodeEntity>>
}

/** Coroutine/Flow repository; callers never touch Room on the main thread. */
class PacketRepository(private val database: MeshDatabase) : PacketStore {
    private val packets = database.packetDao()
    private val nodes = database.nodeDao()
    private val statuses = database.messageStatusDao()
    private val gson = Gson()

    override suspend fun enqueue(packet: MeshPacket, now: Long): Boolean {
        val entity = MeshPacketEntity.fromPacket(packet, DeliveryStatus.PENDING, now)
        val inserted = packets.insertIfAbsent(entity) != -1L
        if (inserted) {
            statuses.upsert(MessageStatusEntity(
                packetId = entity.packetId,
                status = DeliveryStatus.PENDING.name,
                hopCount = packet.hopCount,
                routeHistoryJson = gson.toJson(packet.routeHistory.orEmpty()),
                updatedAt = now
            ))
        }
        return inserted
    }

    override suspend fun saveReceived(packet: MeshPacket, status: DeliveryStatus, now: Long) {
        val entity = MeshPacketEntity.fromPacket(packet, status, now)
        packets.upsert(entity)
        statuses.upsert(MessageStatusEntity(
            packetId = entity.packetId,
            status = status.name,
            hopCount = packet.hopCount,
            routeHistoryJson = gson.toJson(packet.routeHistory.orEmpty()),
            updatedAt = now
        ))
    }

    override suspend fun find(packetId: String): MeshPacketEntity? = packets.find(packetId)
    override suspend fun due(now: Long, limit: Int): List<MeshPacketEntity> = packets.due(now, limit)

    /** Keep the on-device inbox/outbox bounded without deleting pending work. */
    override suspend fun prune(before: Long): Int {
        val removedPackets = packets.prune(before)
        statuses.prune(before)
        return removedPackets
    }

    override suspend fun updateStatus(
        packetId: String,
        status: DeliveryStatus,
        attempts: Int,
        nextAttemptAt: Long,
        detail: String?,
        now: Long
    ) {
        packets.updateStatus(packetId, status.name, attempts, nextAttemptAt, now, detail)
        val old = statuses.find(packetId)
        statuses.upsert((old ?: MessageStatusEntity(packetId)).copy(status = status.name, updatedAt = now, detail = detail))
    }

    override suspend fun upsertNode(node: NodeEntity) = nodes.upsert(node)
    override suspend fun storedNodes(): List<NodeEntity> = nodes.findAll()
    override fun observePackets(): Flow<List<MeshPacketEntity>> = packets.observeAll()
    override fun observeStatuses(): Flow<List<MessageStatusEntity>> = statuses.observeAll()
    override fun observeNodes(): Flow<List<NodeEntity>> = nodes.observeAll()
}
