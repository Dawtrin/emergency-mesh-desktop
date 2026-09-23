package com.rescue.mesh.android.ui

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescue.mesh.android.location.LocationSample
import com.rescue.mesh.android.protocol.Location
import com.rescue.mesh.android.protocol.MeshPacket
import com.rescue.mesh.android.routing.DeliveryStatus
import com.rescue.mesh.android.routing.RoutingEvent
import com.rescue.mesh.android.service.MeshForegroundService
import com.rescue.mesh.android.network.WifiDirectSnapshot
import android.net.wifi.p2p.WifiP2pDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class MeshUiState(
    val nodeId: String = "",
    val transportState: String = "Transport chưa khởi động",
    val statuses: List<DeliveryStatusRow> = emptyList(),
    val dispatches: List<DispatchRow> = emptyList(),
    val eventLog: List<String> = emptyList(),
    val lastError: String? = null,
    val lastLocation: LocationSample? = null,
    val wifiDirect: WifiDirectSnapshot = WifiDirectSnapshot()
)

data class DeliveryStatusRow(
    val packetId: String,
    val status: DeliveryStatus,
    val hopCount: Int,
    val routeHistory: List<String>,
    val detail: String?
)

data class DispatchRow(val packetId: String, val sourceNodeId: String?, val message: String?, val timestamp: Long)

/** ViewModel keeps UI state in StateFlow; sockets and Room stay in the service/repository. */
class MeshViewModel : ViewModel() {
    private val gson = Gson()
    private val routeHistoryType = object : TypeToken<List<String>>() {}.type
    private val _state = MutableStateFlow(MeshUiState())
    val state: StateFlow<MeshUiState> = _state.asStateFlow()
    private var service: MeshForegroundService.LocalBinder? = null

    fun attach(binder: MeshForegroundService.LocalBinder, nodeId: String) {
        if (service === binder) return
        service = binder
        _state.value = _state.value.copy(nodeId = nodeId)
        viewModelScope.launch {
            binder.transportState().collectLatest { value -> _state.value = _state.value.copy(transportState = value) }
        }
        viewModelScope.launch {
            binder.wifiDirectState().collectLatest { value -> _state.value = _state.value.copy(wifiDirect = value) }
        }
        viewModelScope.launch {
            binder.observeStatuses().catch { error -> _state.value = _state.value.copy(lastError = error.message) }.collectLatest { rows ->
                _state.value = _state.value.copy(statuses = rows.map { row ->
                    DeliveryStatusRow(
                        packetId = row.packetId,
                        status = runCatching { DeliveryStatus.valueOf(row.status) }.getOrDefault(DeliveryStatus.FAILED),
                        hopCount = row.hopCount,
                        routeHistory = parseRouteHistory(row.routeHistoryJson),
                        detail = row.detail
                    )
                })
            }
        }
        viewModelScope.launch {
            binder.events().collectLatest { event ->
                val logLine = when (event) {
                    is RoutingEvent.Received -> "[RECV] ${event.packetId.take(8)} từ ${event.fromNodeId ?: "peer"} (${event.type})"
                    is RoutingEvent.Forwarded -> "[FORWARD] ${event.packetId.take(8)} → ${event.toNodeId} (hop=${event.hopCount})"
                    is RoutingEvent.Queued -> "[QUEUED] ${event.packetId.take(8)} — ${event.reason}"
                    is RoutingEvent.Delivered -> "[DELIVERED] ${event.packetId.take(8)}"
                    is RoutingEvent.Dropped -> "[DROP] ${event.packetId.take(8)} — ${event.reason}"
                    is RoutingEvent.Failed -> "[FAILED] ${event.packetId.take(8)} — ${event.reason}"
                    is RoutingEvent.DispatchReceived -> "[DISPATCH] ${event.packet.packetId?.take(8) ?: "unknown"} đã nhận"
                }
                _state.value = _state.value.copy(eventLog = (_state.value.eventLog + logLine).takeLast(100))
                if (event is RoutingEvent.DispatchReceived) {
                    val packet = event.packet
                    _state.value = _state.value.copy(
                        dispatches = (_state.value.dispatches + DispatchRow(packet.packetId!!, packet.sourceNodeId, packet.payload?.message, packet.timestamp)).takeLast(50)
                    )
                }
                if (event is RoutingEvent.Failed) _state.value = _state.value.copy(lastError = event.reason)
            }
        }
    }

    fun registerPeer(nodeId: String, host: String, port: Int) {
        runCatching { service?.registerPeer(nodeId, host, port) }
            .onFailure { reportError("Route peer không hợp lệ: ${it.message}") }
    }

    fun connectWifiPeer(device: WifiP2pDevice) {
        runCatching { service?.connectWifiPeer(device) }
            .onFailure { reportError("Không kết nối được Wi-Fi Direct: ${it.message}") }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(lastError = message)
    }

    fun submitSos(
        senderName: String,
        alertType: String,
        message: String,
        victimCount: Int,
        severity: String,
        location: Location,
        locationSample: LocationSample? = null,
        onSubmitted: (Boolean) -> Unit = {}
    ) {
        val binder = service ?: run { onSubmitted(false); return }
        _state.value = _state.value.copy(lastLocation = locationSample ?: LocationSample(location, System.currentTimeMillis(), LocationSample.Source.MANUAL))
        viewModelScope.launch {
            val nodeId = _state.value.nodeId
            val packet = MeshPacket.newSos(nodeId, senderName, alertType, message, victimCount, severity, location)
            // submit() performs validation and checksum verification before durable enqueue.
            val accepted = runCatching { binder.submit(packet) }
                .onFailure { _state.value = _state.value.copy(lastError = it.message ?: "Không gửi được SOS") }
                .getOrDefault(false)
            onSubmitted(accepted)
        }
    }

    private fun parseRouteHistory(json: String): List<String> =
        runCatching { gson.fromJson<List<String>>(json, routeHistoryType).orEmpty() }
            .getOrElse { emptyList() }
}
