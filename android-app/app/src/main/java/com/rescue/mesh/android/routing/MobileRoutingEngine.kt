package com.rescue.mesh.android.routing

import com.rescue.mesh.android.data.NodeEntity
import com.rescue.mesh.android.data.PacketStore
import com.rescue.mesh.android.network.MeshTransport
import com.rescue.mesh.android.network.PeerEndpoint
import com.rescue.mesh.android.network.SendResult
import com.rescue.mesh.android.network.TransportEvent
import com.rescue.mesh.android.protocol.MeshPacket
import com.rescue.mesh.android.protocol.PacketValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Android routing/store-and-forward layer.  The socket transport stays dumb:
 * this class owns validation, duplicate suppression, route selection, one-time
 * forwarding metadata mutation, ACK correlation, and the bounded Room outbox.
 */
class MobileRoutingEngine(
    private val nodeId: String,
    private val transport: MeshTransport,
    private val repository: PacketStore,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val randomJitterMs: () -> Long = { (0L..250L).random() },
    private val maxAttempts: Int = 5,
    private val initialBackoffMs: Long = 2_000L,
    private val maxBackoffMs: Long = 60_000L,
    private val outboxPollMs: Long = 1_000L,
    private val ackWaitWindowMs: Long = 30_000L
) {
    private val _events = MutableSharedFlow<RoutingEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RoutingEvent> = _events.asSharedFlow()
    private val _transportState = MutableStateFlow("STOPPED")
    val transportState: StateFlow<String> = _transportState.asStateFlow()

    private val seen = SeenPacketLruCache()
    private val routes = ConcurrentHashMap<String, PeerEndpoint>()
    private val activeAttempts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var defaultRoute: PeerEndpoint? = null
    private var eventJob: Job? = null
    private var outboxJob: Job? = null
    private var lastPruneAt = 0L
    @Volatile private var started = false

    init {
        require(nodeId.isNotBlank() && nodeId.length <= PacketValidator.MAX_NODE_ID_LENGTH) { "invalid local node id" }
        require(maxAttempts in 1..20) { "maxAttempts must be between 1 and 20" }
        require(initialBackoffMs >= 0 && maxBackoffMs >= initialBackoffMs) { "invalid backoff policy" }
        require(ackWaitWindowMs > 0) { "ackWaitWindowMs must be positive" }
    }

    suspend fun start() {
        if (started) return
        transport.start()
        repository.storedNodes().forEach { node ->
            val host = node.host
            val port = node.port
            if (host != null && port != null) {
                runCatching { PeerEndpoint(node.nodeId, host, port) }
                    .onSuccess { endpoint ->
                        routes[node.nodeId] = endpoint
                        if (defaultRoute == null) defaultRoute = endpoint
                    }
            }
        }
        started = true
        _transportState.value = "RUNNING"
        eventJob = scope.launch {
            transport.events.collect { event -> handleTransportEvent(event) }
        }
        outboxJob = scope.launch { outboxLoop() }
    }

    fun registerRoute(endpoint: PeerEndpoint) {
        endpoint.validateHostSyntax()
        routes[endpoint.nodeId] = endpoint
        if (defaultRoute == null) defaultRoute = endpoint
        scope.launch { repository.upsertNode(NodeEntity(endpoint.nodeId, endpoint.host, endpoint.port, connected = true)) }
    }

    /** Select the preferred one-hop path used for SOS/ACK when no exact route exists. */
    fun setDefaultRoute(endpoint: PeerEndpoint) {
        endpoint.validateHostSyntax()
        routes[endpoint.nodeId] = endpoint
        defaultRoute = endpoint
        scope.launch { repository.upsertNode(NodeEntity(endpoint.nodeId, endpoint.host, endpoint.port, connected = true)) }
    }

    fun removeRoute(nodeId: String) {
        routes.remove(nodeId)
        if (defaultRoute?.nodeId == nodeId) defaultRoute = routes.values.firstOrNull()
        scope.launch { repository.find(nodeId)?.let { /* packet data remains durable */ } }
    }

    fun routeFor(destinationNodeId: String): PeerEndpoint? = routes[destinationNodeId] ?: defaultRoute

    /** Enqueue a locally-created packet, then attempt it immediately if routed. */
    suspend fun submit(packet: MeshPacket): Boolean {
        val validation = PacketValidator.validate(packet, nowMs())
        if (!validation.valid || !packet.verifyChecksum()) {
            _events.emit(RoutingEvent.Dropped(packet.packetId ?: "UNKNOWN", "invalid packet or checksum"))
            return false
        }
        val inserted = repository.enqueue(packet, nowMs())
        if (!inserted) return false // same packet_id is idempotent
        val route = resolveRoute(packet)
        if (route == null) {
            _events.emit(RoutingEvent.Queued(packet.packetId!!, "no route to ${packet.destinationNodeId}"))
            return true
        }
        attemptEntity(repository.find(packet.packetId!!), route)
        return true
    }

    fun observePackets() = repository.observePackets()
    fun observeStatuses() = repository.observeStatuses()
    fun observeNodes() = repository.observeNodes()

    private suspend fun handleTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.PeerConnected -> {
                registerRoute(event.endpoint)
                _transportState.value = "CONNECTED:${event.endpoint.nodeId}"
            }
            is TransportEvent.PeerDisconnected -> {
                event.endpoint?.let {
                    routes[it.nodeId] = it
                    scope.launch { repository.upsertNode(NodeEntity(it.nodeId, it.host, it.port, connected = false)) }
                }
                if (event.endpoint == null) _transportState.value = "DISCONNECTED"
            }
            is TransportEvent.Error -> _transportState.value = "ERROR:${event.message}"
            is TransportEvent.PacketReceived -> processInbound(event.endpoint, event.packetJson)
        }
    }

    private suspend fun processInbound(endpoint: PeerEndpoint, json: String) {
        val packet = MeshPacket.fromJson(json)
        if (packet == null) {
            _events.emit(RoutingEvent.Dropped("UNKNOWN", "malformed JSON"))
            return
        }
        val packetId = packet.packetId ?: "UNKNOWN"
        val validation = PacketValidator.validate(packet, nowMs())
        if (!validation.valid) {
            _events.emit(RoutingEvent.Dropped(packetId, validation.firstViolation))
            return
        }
        if (!packet.verifyChecksum()) {
            _events.emit(RoutingEvent.Dropped(packetId, "CHECKSUM_FAIL"))
            return
        }

        // Learn the reverse path before either duplicate gate.  A resent SOS
        // is precisely the signal that may recover a previously lost ACK, and
        // the current endpoint is the best next hop for that ACK even when the
        // packet itself was already persisted before a process restart.
        packet.sourceNodeId?.takeIf { it != nodeId }?.let { source ->
            rememberRoute(source, endpoint)
        }

        if (seen.checkAndMark(packetId)) {
            if (isAckableForLocalNode(packet)) sendAck(endpoint, packet)
            _events.emit(RoutingEvent.Dropped(packetId, "DUPLICATE"))
            return
        }
        // The in-memory cache is intentionally bounded and is lost on process
        // restart.  Room is the second idempotency gate so a relay restart or
        // a resent dispatch cannot notify the operator twice or forward the
        // same packet a second time.
        if (repository.find(packetId) != null) {
            if (isAckableForLocalNode(packet)) sendAck(endpoint, packet)
            _events.emit(RoutingEvent.Dropped(packetId, "DUPLICATE_PERSISTED"))
            return
        }
        _events.emit(RoutingEvent.Received(packetId, packet.packetType!!, packet.senderHopId))

        if (packet.destinationNodeId == nodeId) {
            repository.saveReceived(packet, DeliveryStatus.DELIVERED, nowMs())
            when (packet.packetType) {
                MeshPacket.TYPE_DISPATCH_COMMAND, MeshPacket.TYPE_DISPATCH_CMD -> {
                    _events.emit(RoutingEvent.DispatchReceived(packet))
                    sendAck(endpoint, packet)
                }
                MeshPacket.TYPE_SOS_BROADCAST, MeshPacket.TYPE_SOS_DATA -> sendAck(endpoint, packet)
                MeshPacket.TYPE_ACK -> {
                    packet.payload?.ackForPacketId?.let { ackFor ->
                        repository.find(ackFor)?.let { existing ->
                            // A late ACK must not resurrect a terminal failure;
                            // ACKs received during the final wait window do.
                            if (existing.deliveryStatus() != DeliveryStatus.FAILED &&
                                existing.deliveryStatus() != DeliveryStatus.EXPIRED) {
                                repository.updateStatus(
                                    ackFor,
                                    DeliveryStatus.DELIVERED,
                                    existing.attempts,
                                    detail = "ACK received"
                                )
                                _events.emit(RoutingEvent.Delivered(ackFor))
                            }
                        }
                    }
                }
            }
            return
        }

        val route = resolveRoute(packet)
        if (route == null || packet.ttl <= 1) {
            val reason = if (packet.ttl <= 1) "TTL_EXPIRED" else "NO_ROUTE:${packet.destinationNodeId}"
            if (packet.ttl <= 1) {
                _events.emit(RoutingEvent.Dropped(packetId, reason))
                repository.enqueue(packet, nowMs())
                repository.updateStatus(packetId, DeliveryStatus.EXPIRED, 0, detail = reason)
                return
            }
            // Persist the forwarding copy, not the incoming copy.  This makes
            // a later reconnect retry the same one-time TTL/hop mutation.
            val queued = packet.copyForForwarding(nodeId)
            repository.enqueue(queued, nowMs())
            _events.emit(RoutingEvent.Queued(packetId, reason))
            return
        }

        val forwarded = try { packet.copyForForwarding(nodeId) } catch (error: IllegalArgumentException) {
            _events.emit(RoutingEvent.Dropped(packetId, "forward copy failed: ${error.message}"))
            return
        }
        repository.enqueue(forwarded, nowMs())
        attemptEntity(repository.find(forwarded.packetId!!), route)
    }

    private fun isAckableForLocalNode(packet: MeshPacket): Boolean =
        packet.destinationNodeId == nodeId &&
            (packet.packetType == MeshPacket.TYPE_SOS_BROADCAST ||
                packet.packetType == MeshPacket.TYPE_SOS_DATA ||
                packet.packetType == MeshPacket.TYPE_DISPATCH_COMMAND ||
                packet.packetType == MeshPacket.TYPE_DISPATCH_CMD)

    private suspend fun sendAck(endpoint: PeerEndpoint, original: MeshPacket) {
        val ack = MeshPacket.newAck(nodeId, original.packetId!!, original.sourceNodeId!!)
        val ackEntity = try {
            // ACKs are ordinary durable outbox packets.  Never fall back to a
            // direct socket send after this write fails: that would recreate
            // the lost-ACK window this layer is meant to close.
            repository.enqueue(ack, nowMs())
            repository.find(ack.packetId!!)
        } catch (error: Throwable) {
            _events.emit(RoutingEvent.Failed(ack.packetId!!, "ACK enqueue failed: ${safeMessage(error)}"))
            return
        }

        if (ackEntity == null) {
            _events.emit(RoutingEvent.Failed(ack.packetId!!, "ACK enqueue returned no durable row"))
            return
        }

        // A duplicate inbound SOS/DISPATCH can arrive after the ACK exhausted
        // its delivery attempts.  Re-queueing here is an explicit duplicate
        // recovery action; FAILED is still excluded from the automatic due()
        // query and therefore cannot spin by itself.
        val current = ackEntity.deliveryStatus()
        if (current == DeliveryStatus.DELIVERED) return
        val ready = if (current == DeliveryStatus.FAILED || current == DeliveryStatus.EXPIRED) {
            repository.updateStatus(
                ack.packetId!!,
                DeliveryStatus.PENDING,
                attempts = 0,
                nextAttemptAt = nowMs(),
                detail = "duplicate inbound packet re-triggered ACK"
            )
            repository.find(ack.packetId!!)
        } else {
            ackEntity
        }
        if (ready != null) attemptEntity(ready, endpoint)
    }

    private fun safeMessage(error: Throwable): String =
        error.message ?: error::class.simpleName.orEmpty()

    private fun isAckRequired(packet: MeshPacket): Boolean =
        packet.packetType == MeshPacket.TYPE_SOS_BROADCAST ||
            packet.packetType == MeshPacket.TYPE_SOS_DATA ||
            packet.packetType == MeshPacket.TYPE_DISPATCH_COMMAND ||
            packet.packetType == MeshPacket.TYPE_DISPATCH_CMD

    private fun safeAdd(base: Long, increment: Long): Long {
        if (increment <= 0L) return base
        if (base >= Long.MAX_VALUE - increment) return Long.MAX_VALUE
        return base + increment
    }

    /** Capped exponential backoff without a shift/multiply overflow. */
    private fun backoffDelay(attempt: Int): Long {
        var delayMs = initialBackoffMs.coerceAtMost(maxBackoffMs)
        var remainingDoublings = (attempt - 1).coerceAtLeast(0).coerceAtMost(63)
        while (remainingDoublings > 0 && delayMs < maxBackoffMs) {
            delayMs = if (delayMs > maxBackoffMs / 2L) {
                maxBackoffMs
            } else {
                (delayMs * 2L).coerceAtMost(maxBackoffMs)
            }
            remainingDoublings--
        }
        return delayMs
    }

    private suspend fun outboxLoop() {
        while (started) {
            processOutboxOnce()
            delay(outboxPollMs)
        }
    }

    /**
     * Process one deterministic outbox tick.  The service calls this from its
     * polling coroutine; tests can call it directly with a fake clock without
     * running an unbounded virtual-time loop.
     */
    suspend fun processOutboxOnce(now: Long = nowMs()) {
        runCatching {
            if (lastPruneAt == 0L || now - lastPruneAt >= PRUNE_INTERVAL_MS) {
                repository.prune(now - RETAINED_DELIVERY_MS)
                lastPruneAt = now
            }
            repository.due(now, limit = 16).forEach { entity ->
                val packet = entity.packet() ?: run {
                    repository.updateStatus(
                        entity.packetId,
                        DeliveryStatus.FAILED,
                        entity.attempts.coerceAtMost(maxAttempts),
                        detail = "stored JSON is malformed; manual inspection required",
                        now = now
                    )
                    return@forEach
                }

                // A successful final send gets one last ACK wait window.  Do
                // not send it again once that window expires.
                if (entity.deliveryStatus() == DeliveryStatus.SENT_WAITING_ACK &&
                    entity.attempts >= maxAttempts) {
                    failAckTimeout(entity, now)
                    return@forEach
                }

                // Defensive guard for rows written by an older build or a
                // manually repaired database.  No packet can be sent beyond
                // the configured finite attempt budget.
                if (entity.attempts >= maxAttempts) {
                    val reason = if (isAckRequired(packet)) {
                        "ACK_TIMEOUT after $maxAttempts delivery attempts"
                    } else {
                        "maximum delivery attempts ($maxAttempts) reached"
                    }
                    repository.updateStatus(
                        entity.packetId,
                        DeliveryStatus.FAILED,
                        maxAttempts,
                        detail = reason,
                        now = now
                    )
                    _events.tryEmit(RoutingEvent.Failed(entity.packetId, reason))
                    return@forEach
                }

                // Resolve the current exact or default route at retry time. A
                // default Wi-Fi Direct route may be learned after a packet is
                // persisted in the outbox.
                val route = resolveRoute(packet)
                if (route == null) {
                    _events.tryEmit(RoutingEvent.Queued(entity.packetId, "waiting for route to ${packet.destinationNodeId}"))
                } else {
                    attemptEntity(entity, route)
                }
            }
        }.onFailure { error ->
            _events.tryEmit(RoutingEvent.Dropped("OUTBOX", "outbox tick failed: ${safeMessage(error)}"))
        }
    }

    private suspend fun failAckTimeout(entity: com.rescue.mesh.android.data.MeshPacketEntity, now: Long) {
        val reason = "ACK_TIMEOUT after $maxAttempts delivery attempts; no automatic retry remains"
        repository.updateStatus(
            entity.packetId,
            DeliveryStatus.FAILED,
            maxAttempts,
            detail = reason,
            now = now
        )
        _events.emit(RoutingEvent.Failed(entity.packetId, reason))
    }

    private fun resolveRoute(packet: MeshPacket): PeerEndpoint? {
        val destination = packet.destinationNodeId ?: return null
        routes[destination]?.let { return it }
        return when (packet.packetType) {
            MeshPacket.TYPE_SOS_BROADCAST, MeshPacket.TYPE_SOS_DATA,
            MeshPacket.TYPE_ACK, MeshPacket.TYPE_HEARTBEAT,
            MeshPacket.TYPE_ROUTE_DISCOVERY -> defaultRoute
            else -> null
        }
    }

    private fun rememberRoute(destinationNodeId: String, endpoint: PeerEndpoint) {
        routes[destinationNodeId] = endpoint
        scope.launch {
            repository.upsertNode(NodeEntity(destinationNodeId, endpoint.host, endpoint.port, connected = true))
        }
    }

    private suspend fun attemptEntity(entity: com.rescue.mesh.android.data.MeshPacketEntity?, endpoint: PeerEndpoint) {
        val packetId = entity?.packetId ?: return
        if (!activeAttempts.add(packetId)) return
        try {
            val packet = entity.packet() ?: return
            if (entity.deliveryStatus() == DeliveryStatus.FAILED ||
                entity.deliveryStatus() == DeliveryStatus.DELIVERED ||
                entity.deliveryStatus() == DeliveryStatus.EXPIRED) return
            if (entity.attempts >= maxAttempts) {
                val reason = if (isAckRequired(packet)) {
                    "ACK_TIMEOUT after $maxAttempts delivery attempts; no automatic retry remains"
                } else {
                    "maximum delivery attempts ($maxAttempts) reached"
                }
                repository.updateStatus(entity.packetId, DeliveryStatus.FAILED, maxAttempts, detail = reason, now = nowMs())
                _events.emit(RoutingEvent.Failed(entity.packetId, reason))
                return
            }

            // The guard above keeps this addition within the configured finite
            // range even if a hand-edited/legacy row contains a huge counter.
            val attempt = entity.attempts + 1
            repository.updateStatus(entity.packetId, DeliveryStatus.IN_FLIGHT, attempt, detail = "sending to ${endpoint.nodeId}", now = nowMs())
            when (val result = transport.send(endpoint, packet)) {
                is SendResult.Sent -> {
                    val waitingAck = isAckRequired(packet)
                    val sentAt = nowMs()
                    val waitMs = if (attempt >= maxAttempts) {
                        // The final successful send receives one bounded grace
                        // period for its ACK, but is not resent after it.
                        ackWaitWindowMs
                    } else {
                        safeAdd(backoffDelay(attempt), randomJitterMs())
                    }
                    repository.updateStatus(
                        entity.packetId,
                        if (waitingAck) DeliveryStatus.SENT_WAITING_ACK else DeliveryStatus.DELIVERED,
                        attempt,
                        nextAttemptAt = if (waitingAck) safeAdd(sentAt, waitMs) else 0L,
                        detail = if (waitingAck && attempt >= maxAttempts) {
                            "final TCP frame sent; waiting for ACK until ${safeAdd(sentAt, waitMs)}"
                        } else {
                            "TCP frame sent"
                        },
                        now = sentAt
                    )
                    _events.emit(RoutingEvent.Forwarded(entity.packetId, endpoint.nodeId, packet.hopCount))
                    if (!waitingAck) _events.emit(RoutingEvent.Delivered(entity.packetId))
                }
                is SendResult.Failed -> {
                    val failedAt = nowMs()
                    if (attempt >= maxAttempts) {
                        val reason = "SEND_FAILED after $maxAttempts attempts: ${result.reason}"
                        repository.updateStatus(entity.packetId, DeliveryStatus.FAILED, attempt, detail = reason, now = failedAt)
                        _events.emit(RoutingEvent.Failed(entity.packetId, reason))
                    } else {
                        val delayMs = safeAdd(backoffDelay(attempt), randomJitterMs())
                        repository.updateStatus(entity.packetId, DeliveryStatus.PENDING, attempt, nextAttemptAt = safeAdd(failedAt, delayMs), detail = result.reason, now = failedAt)
                        _events.emit(RoutingEvent.Queued(entity.packetId, "retry in ${delayMs}ms: ${result.reason}"))
                    }
                }
            }
        } finally {
            activeAttempts.remove(packetId)
        }
    }

    fun close() {
        started = false
        eventJob?.cancel()
        outboxJob?.cancel()
        eventJob = null
        outboxJob = null
        lastPruneAt = 0L
        activeAttempts.clear()
        defaultRoute = null
        transport.close()
        _transportState.value = "STOPPED"
    }

    private companion object {
        const val PRUNE_INTERVAL_MS = 60_000L
        const val RETAINED_DELIVERY_MS = 7L * 24 * 60 * 60 * 1_000L
    }
}
