package com.rescue.mesh.android.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rescue.mesh.android.protocol.MeshPacket
import com.rescue.mesh.android.protocol.PacketValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Newline-framed UTF-8 TCP transport used by Android nodes.
 *
 * Every connection starts with a small handshake containing node ID, protocol
 * version, and listening port.  The packet frame is the same compact JSON line
 * accepted by the desktop SocketServer (maximum 64 KiB including the newline).
 * The transport owns only bounded coroutine jobs; routing and persistence live
 * above it.
 */
class SocketMeshTransport(
    private val nodeId: String,
    private val bindHost: String = "0.0.0.0",
    private val listenPort: Int = 8_888,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
    private val maxFrameBytes: Int = PacketValidator.MAX_FRAME_BYTES,
    private val legacyPeerPort: Int = 8_888,
    private val maxConcurrentConnections: Int = 20,
    parentScope: CoroutineScope? = null
) : MeshTransport {
    init {
        require(nodeId.isNotBlank() && nodeId.length <= 64) { "nodeId must be 1..64 characters" }
        require(bindHost.isNotBlank()) { "bindHost must not be blank" }
        // Port 0 is supported for deterministic loopback tests; production
        // defaults to 8888 and advertises the bound port after start().
        require(listenPort in 0..65_535) { "listenPort must be from 0 to 65535" }
        require(connectTimeoutMs > 0 && readTimeoutMs > 0) { "socket timeouts must be positive" }
        require(maxFrameBytes in 256..PacketValidator.MAX_FRAME_BYTES) { "maxFrameBytes must be bounded" }
        require(legacyPeerPort in 1..65_535) { "legacyPeerPort must be from 1 to 65535" }
        require(maxConcurrentConnections in 1..64) { "maxConcurrentConnections must be from 1 to 64" }
    }
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ownsScope = parentScope == null
    private val gson = Gson()
    private val _events = MutableSharedFlow<TransportEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    private val started = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    @Volatile private var actualPort: Int = -1
    private var acceptJob: Job? = null
    private val clientJobs = mutableSetOf<Job>()
    private val jobsLock = Any()
    private val connectionPermits = Semaphore(maxConcurrentConnections)

    override val isRunning: Boolean get() = started.get()

    /** Actual bound port, or -1 before start/after close. */
    val localPort: Int get() = actualPort

    override suspend fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bindHost, listenPort))
            serverSocket = socket
            actualPort = socket.localPort
            acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        } catch (error: Throwable) {
            started.set(false)
            serverSocket?.closeQuietly()
            serverSocket = null
            actualPort = -1
            throw IllegalStateException("Cannot bind mesh socket at $bindHost:$listenPort", error)
        }
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (started.get()) {
            val client = try {
                withContext(Dispatchers.IO) { socket.accept() }
            } catch (error: SocketException) {
                if (started.get()) emitError(null, "accept failed: ${safeMessage(error)}")
                break
            } catch (error: IOException) {
                if (started.get()) emitError(null, "accept failed: ${safeMessage(error)}")
                break
            }
            if (!connectionPermits.tryAcquire()) {
                emitError(null, "connection rejected: maximum concurrent connections reached")
                client.closeQuietly()
                continue
            }
            val job = scope.launch(Dispatchers.IO) {
                try {
                    handleIncoming(client)
                } finally {
                    connectionPermits.release()
                }
            }
            synchronized(jobsLock) {
                clientJobs += job
                job.invokeOnCompletion { synchronized(jobsLock) { clientJobs -= job } }
            }
        }
    }

    private suspend fun handleIncoming(socket: Socket) {
        socket.use { client ->
            val remoteHost = client.inetAddress?.hostAddress ?: return
            val endpoint: PeerEndpoint?
            try {
                client.soTimeout = readTimeoutMs
                val input = BufferedInputStream(client.getInputStream())
                val firstLine = readBoundedLine(input) ?: throw IOException("missing handshake or packet")
                val handshake = runCatching { parseHandshake(firstLine) }.getOrNull()
                if (handshake != null) {
                    if (handshake.protocolVersion != MeshPacket.PROTOCOL_VERSION_1) {
                        throw IOException("unsupported protocol version ${handshake.protocolVersion}")
                    }
                    endpoint = PeerEndpoint(handshake.nodeId, remoteHost, handshake.listenPort)
                    sendHandshake(client)
                    _events.tryEmit(TransportEvent.PeerConnected(endpoint))
                } else {
                    // Desktop SocketClient v1 predates the optional handshake
                    // frame.  Accept its first MeshPacket so Android remains
                    // wire-compatible; reverse ACK uses the documented legacy
                    // listening port rather than the ephemeral source port.
                    val firstPacket = MeshPacket.fromJson(firstLine) ?: throw IOException("invalid handshake or packet")
                    val legacyId = firstPacket.senderHopId ?: firstPacket.sourceNodeId ?: "LEGACY_PEER"
                    endpoint = PeerEndpoint(legacyId, remoteHost, legacyPeerPort)
                    _events.tryEmit(TransportEvent.PeerConnected(endpoint))
                    if (firstLine.isNotBlank()) _events.tryEmit(TransportEvent.PacketReceived(endpoint, firstLine))
                }
                while (started.get() && !client.isClosed) {
                    val line = readBoundedLine(input) ?: break
                    if (line.isBlank()) continue
                    _events.tryEmit(TransportEvent.PacketReceived(endpoint, line))
                }
                _events.tryEmit(TransportEvent.PeerDisconnected(endpoint, "peer closed connection"))
            } catch (error: FrameTooLargeException) {
                emitError(null, "frame rejected: ${error.message}")
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    emitError(null, "incoming connection from $remoteHost failed: ${safeMessage(error)}")
                }
            }
        }
    }

    override suspend fun send(endpoint: PeerEndpoint, packet: MeshPacket): SendResult = withContext(Dispatchers.IO) {
        val packetId = packet.packetId
        if (!started.get()) return@withContext SendResult.Failed(endpoint, packetId, "transport is stopped")
        endpoint.validateHostSyntax()
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            writer.println(handshakeJson())
            writer.flush()
            val json = packet.toJson()
            require(json.toByteArray(StandardCharsets.UTF_8).size + 1 <= maxFrameBytes) { "packet frame exceeds $maxFrameBytes bytes" }
            // Send the handshake and packet back-to-back.  Android peers read
            // and validate the handshake first; the desktop SocketServer can
            // consume the optional handshake as an unknown frame and then
            // process the packet, preserving compatibility with its v1 API.
            writer.println(json)
            writer.flush()
            if (writer.checkError()) throw IOException("socket write failed")
            _events.tryEmit(TransportEvent.PeerConnected(endpoint))
            SendResult.Sent(endpoint, packetId)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val reason = safeMessage(error)
            _events.tryEmit(TransportEvent.Error(endpoint, "send failed: $reason"))
            SendResult.Failed(endpoint, packetId, reason)
        } finally {
            socket.closeQuietly()
        }
    }

    private fun handshakeJson(): String = JsonObject().apply {
        addProperty("frame_type", "HANDSHAKE")
        addProperty("node_id", nodeId)
        addProperty("protocol_version", MeshPacket.PROTOCOL_VERSION_1)
        addProperty("listen_port", actualPort.takeIf { it > 0 } ?: listenPort)
    }.toString()

    private fun sendHandshake(socket: Socket) {
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        writer.println(handshakeJson())
        writer.flush()
        if (writer.checkError()) throw IOException("handshake write failed")
    }

    private fun parseHandshake(json: String): Handshake {
        val objectValue = gson.fromJson(json, JsonObject::class.java) ?: throw IOException("invalid handshake")
        if (objectValue.get("frame_type")?.asString != "HANDSHAKE") throw IOException("missing HANDSHAKE frame")
        val peerId = objectValue.get("node_id")?.asString?.trim().orEmpty()
        val version = objectValue.get("protocol_version")?.asString?.trim().orEmpty()
        val port = objectValue.get("listen_port")?.asInt ?: 0
        require(peerId.isNotBlank() && peerId.length <= 64) { "invalid peer node_id" }
        require(port in 1..65_535) { "invalid peer listening port" }
        return Handshake(peerId, version, port)
    }

    private suspend fun readBoundedLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        var consumed = 0
        while (true) {
            val value = withContext(Dispatchers.IO) { input.read() }
            if (value == -1) {
                if (consumed == 0) return null
                break
            }
            consumed++
            if (consumed > maxFrameBytes) throw FrameTooLargeException("raw frame exceeds $maxFrameBytes bytes")
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }

    private fun emitError(endpoint: PeerEndpoint?, message: String) {
        _events.tryEmit(TransportEvent.Error(endpoint, message))
    }

    override fun close() {
        if (!started.compareAndSet(true, false)) return
        serverSocket.closeQuietly()
        synchronized(jobsLock) { clientJobs.toList().forEach { it.cancel() }; clientJobs.clear() }
        acceptJob?.cancel()
        acceptJob = null
        serverSocket = null
        actualPort = -1
        if (ownsScope) scope.cancel()
    }

    private data class Handshake(val nodeId: String, val protocolVersion: String, val listenPort: Int)
    private class FrameTooLargeException(message: String) : IOException(message)

    private fun Closeable?.closeQuietly() {
        try { this?.close() } catch (_: IOException) { }
    }

    private fun safeMessage(error: Throwable): String = error.message ?: error::class.simpleName.orEmpty()
}
