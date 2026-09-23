package com.rescue.mesh.android.routing

import com.rescue.mesh.android.data.MessageStatusEntity
import com.rescue.mesh.android.data.MeshPacketEntity
import com.rescue.mesh.android.data.NodeEntity
import com.rescue.mesh.android.data.PacketStore
import com.rescue.mesh.android.network.MeshTransport
import com.rescue.mesh.android.network.PeerEndpoint
import com.rescue.mesh.android.network.SendResult
import com.rescue.mesh.android.network.TransportEvent
import com.rescue.mesh.android.protocol.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MobileRoutingEngineDeliveryTest {
    private val endpoint = PeerEndpoint("RELAY", "127.0.0.1", 8_888)

    @Test
    fun failedDeliverySendsExactlyMaxAttemptsAndNeverRetriesTerminalFailure() = runTest {
        val store = InMemoryPacketStore()
        val transport = ScriptedTransport { peer, packet ->
            SendResult.Failed(peer, packet.packetId, "connection refused")
        }
        val nowStart = System.currentTimeMillis()
        var now = nowStart
        val engine = newEngine(this, store, transport, { now }, maxAttempts = 3)
        engine.start()
        runCurrent()
        engine.registerRoute(endpoint.copy(nodeId = "VICTIM"))

        val packet = MeshPacket.newSos(
            sourceNodeId = "VICTIM",
            senderName = "Test victim",
            alertType = MeshPacket.ALERT_MEDICAL,
            message = "help",
            victimCount = 1,
            severity = MeshPacket.SEVERITY_CRITICAL,
            location = com.rescue.mesh.android.protocol.Location(16.0, 108.0)
        )
        assertTrue(engine.submit(packet))
        assertEquals(1, transport.sent.size)

        now += 10L
        engine.processOutboxOnce(now)
        assertEquals(2, transport.sent.size)
        now += 20L
        engine.processOutboxOnce(now)
        assertEquals(3, transport.sent.size)
        assertEquals(DeliveryStatus.FAILED, store.row(packet.packetId!!)?.deliveryStatus())
        assertEquals(3, store.row(packet.packetId!!)?.attempts)

        now += 1_000_000L
        engine.processOutboxOnce(now)
        assertEquals("FAILED rows must never be selected by due()", 3, transport.sent.size)
        assertEquals(DeliveryStatus.FAILED, store.row(packet.packetId!!)?.deliveryStatus())
        engine.close()
    }

    @Test
    fun successfulFinalSendWaitsForAckThenFailsOnceWithoutMoreSends() = runTest {
        val store = InMemoryPacketStore()
        val transport = ScriptedTransport { peer, packet -> SendResult.Sent(peer, packet.packetId) }
        val nowStart = System.currentTimeMillis()
        var now = nowStart
        val engine = newEngine(this, store, transport, { now }, maxAttempts = 3, ackWaitWindowMs = 50L)
        engine.start()
        runCurrent()
        engine.registerRoute(endpoint)

        val packet = MeshPacket.newSos(
            sourceNodeId = "VICTIM",
            senderName = "Test victim",
            alertType = MeshPacket.ALERT_MEDICAL,
            message = "help",
            victimCount = 1,
            severity = MeshPacket.SEVERITY_CRITICAL,
            location = com.rescue.mesh.android.protocol.Location(16.0, 108.0)
        )
        assertTrue(engine.submit(packet))
        now += 10L
        engine.processOutboxOnce(now)
        now += 20L
        engine.processOutboxOnce(now)
        assertEquals(3, transport.sent.size)
        assertEquals(DeliveryStatus.SENT_WAITING_ACK, store.row(packet.packetId!!)?.deliveryStatus())

        now += 50L
        engine.processOutboxOnce(now)
        assertEquals(DeliveryStatus.FAILED, store.row(packet.packetId!!)?.deliveryStatus())
        assertEquals("ACK_TIMEOUT after 3 delivery attempts; no automatic retry remains", store.row(packet.packetId!!)?.lastError)
        val sendsAfterFailure = transport.sent.size
        now += 1_000_000L
        engine.processOutboxOnce(now)
        assertEquals(sendsAfterFailure, transport.sent.size)
        engine.close()
    }

    @Test
    fun ackReceivedDuringFinalWaitMarksOriginalDelivered() = runTest {
        val store = InMemoryPacketStore()
        val transport = ScriptedTransport { peer, packet -> SendResult.Sent(peer, packet.packetId) }
        val nowStart = System.currentTimeMillis()
        var now = nowStart
        val engine = newEngine(this, store, transport, { now }, maxAttempts = 2, ackWaitWindowMs = 100L)
        engine.start()
        runCurrent()
        engine.registerRoute(endpoint.copy(nodeId = "VICTIM"))

        val packet = MeshPacket.newDispatch("VICTIM", "Evacuate now")
        assertTrue(engine.submit(packet))
        assertEquals(DeliveryStatus.SENT_WAITING_ACK, store.row(packet.packetId!!)?.deliveryStatus())

        val ack = MeshPacket.newAck("VICTIM", packet.packetId!!, MeshPacket.NODE_BASE_STATION)
        transport.emit(TransportEvent.PacketReceived(endpoint.copy(nodeId = "VICTIM"), ack.toJson()))
        runCurrent()

        assertEquals(DeliveryStatus.DELIVERED, store.row(packet.packetId!!)?.deliveryStatus())
        now += 1_000_000L
        engine.processOutboxOnce(now)
        assertEquals("ACK prevents every later retry", 1, transport.sent.size)
        engine.close()
    }

    @Test
    fun ackIsDurableAndAutomaticRetryDeliversItAfterTheFirstSendFails() = runTest {
        val store = InMemoryPacketStore()
        var ackAttempts = 0
        val transport = ScriptedTransport { peer, packet ->
            if (packet.packetType == MeshPacket.TYPE_ACK && ++ackAttempts == 1) {
                SendResult.Failed(peer, packet.packetId, "temporary link loss")
            } else {
                SendResult.Sent(peer, packet.packetId)
            }
        }
        val nowStart = System.currentTimeMillis()
        var now = nowStart
        val engine = newEngine(this, store, transport, { now }, maxAttempts = 3)
        engine.start()
        runCurrent()

        val sos = MeshPacket.newSos(
            sourceNodeId = "VICTIM",
            senderName = "Test victim",
            alertType = MeshPacket.ALERT_MEDICAL,
            message = "help",
            victimCount = 1,
            severity = MeshPacket.SEVERITY_CRITICAL,
            location = com.rescue.mesh.android.protocol.Location(16.0, 108.0)
        )
        transport.emit(TransportEvent.PacketReceived(endpoint, sos.toJson()))
        runCurrent()

        val ackRow = store.rows().single { it.packetType == MeshPacket.TYPE_ACK }
        assertEquals(DeliveryStatus.PENDING, ackRow.deliveryStatus())
        assertEquals(1, ackRow.attempts)

        now += 10L
        engine.processOutboxOnce(now)
        assertEquals(DeliveryStatus.DELIVERED, store.row(ackRow.packetId)?.deliveryStatus())
        assertEquals("the failed ACK must be retried from Room", 2, transport.sent.size)
        engine.close()
    }

    @Test
    fun duplicateSosAndDispatchReuseAckWithoutSecondUserNotification() = runTest {
        val scenarios = listOf(
            Triple(
                MeshPacket.NODE_BASE_STATION,
                MeshPacket.newSos(
                    "VICTIM", "Test victim", MeshPacket.ALERT_MEDICAL, "help", 1,
                    MeshPacket.SEVERITY_CRITICAL,
                    com.rescue.mesh.android.protocol.Location(16.0, 108.0)
                ),
                "SOS"
            ),
            Triple(
                "VICTIM",
                MeshPacket.newDispatch("VICTIM", "Evacuate now"),
                "DISPATCH"
            )
        )

        scenarios.forEach { (localNodeId, packet, kind) ->
            val store = InMemoryPacketStore()
            val transport = ScriptedTransport { peer, outgoing -> SendResult.Sent(peer, outgoing.packetId) }
            val engine = newEngine(this, store, transport, { System.currentTimeMillis() }, maxAttempts = 3, nodeId = localNodeId)
            val events = mutableListOf<RoutingEvent>()
            val collector = launch { engine.events.collect { events += it } }
            engine.start()
            runCurrent()

            transport.emit(TransportEvent.PacketReceived(endpoint, packet.toJson()))
            runCurrent()
            transport.emit(TransportEvent.PacketReceived(endpoint, packet.toJson()))
            runCurrent()

            assertEquals("$kind duplicate must reuse a delivered ACK", 1, transport.sent.size)
            assertEquals(1, store.rows().count { it.packetType == MeshPacket.TYPE_ACK })
            if (kind == "SOS") {
                assertEquals(1, events.count { it is RoutingEvent.Received && it.packetId == packet.packetId })
            } else {
                assertEquals(1, events.count { it is RoutingEvent.DispatchReceived })
            }
            collector.cancel()
            engine.close()
        }
    }

    private fun newEngine(
        scope: CoroutineScope,
        store: InMemoryPacketStore,
        transport: ScriptedTransport,
        now: () -> Long,
        maxAttempts: Int,
        ackWaitWindowMs: Long = 100L,
        nodeId: String = MeshPacket.NODE_BASE_STATION
    ) = MobileRoutingEngine(
        nodeId = nodeId,
        transport = transport,
        repository = store,
        scope = scope,
        nowMs = now,
        randomJitterMs = { 0L },
        maxAttempts = maxAttempts,
        initialBackoffMs = 10L,
        maxBackoffMs = 100L,
        outboxPollMs = 1_000_000L,
        ackWaitWindowMs = ackWaitWindowMs
    )
}

private class ScriptedTransport(
    private val result: (PeerEndpoint, MeshPacket) -> SendResult
) : MeshTransport {
    private val eventFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    val sent = mutableListOf<MeshPacket>()
    override val events: SharedFlow<TransportEvent> = eventFlow.asSharedFlow()
    override var isRunning: Boolean = false
        private set

    override suspend fun start() {
        isRunning = true
    }

    override suspend fun send(endpoint: PeerEndpoint, packet: MeshPacket): SendResult {
        sent += packet
        return result(endpoint, packet)
    }

    fun emit(event: TransportEvent) {
        eventFlow.tryEmit(event)
    }

    override fun close() {
        isRunning = false
    }
}

private class InMemoryPacketStore : PacketStore {
    private val packets = linkedMapOf<String, MeshPacketEntity>()
    private val statuses = linkedMapOf<String, MessageStatusEntity>()
    private val nodes = linkedMapOf<String, NodeEntity>()

    override suspend fun enqueue(packet: MeshPacket, now: Long): Boolean {
        val packetId = requireNotNull(packet.packetId)
        if (packets.containsKey(packetId)) return false
        val entity = MeshPacketEntity.fromPacket(packet, DeliveryStatus.PENDING, now)
        packets[packetId] = entity
        statuses[packetId] = MessageStatusEntity(packetId, DeliveryStatus.PENDING.name, packet.hopCount, updatedAt = now)
        return true
    }

    override suspend fun saveReceived(packet: MeshPacket, status: DeliveryStatus, now: Long) {
        val entity = MeshPacketEntity.fromPacket(packet, status, now)
        packets[entity.packetId] = entity
        statuses[entity.packetId] = MessageStatusEntity(entity.packetId, status.name, packet.hopCount, updatedAt = now)
    }

    override suspend fun find(packetId: String): MeshPacketEntity? = packets[packetId]

    override suspend fun due(now: Long, limit: Int): List<MeshPacketEntity> = packets.values
        .filter { (it.status == DeliveryStatus.PENDING.name || it.status == DeliveryStatus.SENT_WAITING_ACK.name) && it.nextAttemptAt <= now }
        .sortedBy { it.createdAt }
        .take(limit)

    override suspend fun prune(before: Long): Int {
        val removable = packets.values.filter {
            (it.status == DeliveryStatus.DELIVERED.name || it.status == DeliveryStatus.EXPIRED.name) && it.updatedAt < before
        }.map { it.packetId }
        removable.forEach { packets.remove(it); statuses.remove(it) }
        return removable.size
    }

    override suspend fun updateStatus(
        packetId: String,
        status: DeliveryStatus,
        attempts: Int,
        nextAttemptAt: Long,
        detail: String?,
        now: Long
    ) {
        val old = packets[packetId] ?: return
        packets[packetId] = old.copy(
            status = status.name,
            attempts = attempts,
            nextAttemptAt = nextAttemptAt,
            updatedAt = now,
            lastError = detail
        )
        statuses[packetId] = (statuses[packetId] ?: MessageStatusEntity(packetId)).copy(
            status = status.name,
            updatedAt = now,
            detail = detail
        )
    }

    override suspend fun upsertNode(node: NodeEntity) {
        nodes[node.nodeId] = node
    }

    override suspend fun storedNodes(): List<NodeEntity> = nodes.values.toList()
    override fun observePackets(): Flow<List<MeshPacketEntity>> = emptyFlow()
    override fun observeStatuses(): Flow<List<MessageStatusEntity>> = emptyFlow()
    override fun observeNodes(): Flow<List<NodeEntity>> = emptyFlow()

    fun row(packetId: String): MeshPacketEntity? = packets[packetId]
    fun rows(): List<MeshPacketEntity> = packets.values.toList()
}
