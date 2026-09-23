package com.rescue.mesh.android.network

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress

enum class WifiDirectState { Idle, Discovering, PeersFound, Connecting, Connected, Error }

data class WifiDirectSnapshot(
    val state: WifiDirectState = WifiDirectState.Idle,
    val peers: List<WifiP2pDevice> = emptyList(),
    val groupOwner: Boolean = false,
    val groupOwnerAddress: InetAddress? = null,
    val error: String? = null
)

/**
 * Lifecycle-safe Wi-Fi Direct facade.  Discovery and connection are real
 * platform calls; endpoint IP is only published when Android provides it.
 * Group-owner/client topology differences are intentionally surfaced instead
 * of guessing a peer address or claiming multi-hop support without a device
 * test.
 */
class WifiDirectManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val reconnect: Boolean = true
) {
    private val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(context.applicationContext, context.mainLooper, null)
    private val _snapshot = MutableStateFlow(WifiDirectSnapshot())
    val snapshot: StateFlow<WifiDirectSnapshot> = _snapshot.asStateFlow()
    private var receiver: WifiP2pReceiver? = null
    private var retryJob: Job? = null
    private var registered = false
    /**
     * The last explicitly selected peer.  Keeping only its device address
     * lets a reconnect target the same peer after it reappears, without
     * retaining an Activity, Context, or a stale WifiP2pDevice instance.
     */
    private var reconnectDeviceAddress: String? = null

    private val filter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun start() {
        if (registered) return
        require(manager != null && channel != null) { "Wi-Fi Direct is not available on this device" }
        require(hasDiscoveryPermission()) { "Nearby Wi-Fi/location permission is required" }
        receiver = WifiP2pReceiver(::handleBroadcast)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        discoverPeers()
    }

    fun stop() {
        retryJob?.cancel()
        retryJob = null
        reconnectDeviceAddress = null
        if (registered) {
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            receiver = null
            registered = false
        }
        _snapshot.value = WifiDirectSnapshot()
    }

    fun discoverPeers() {
        val p2p = manager ?: return fail("Wi-Fi Direct manager unavailable")
        val p2pChannel = channel ?: return fail("Wi-Fi Direct channel unavailable")
        if (!hasDiscoveryPermission()) return fail("Nearby Wi-Fi/location permission is required")
        _snapshot.value = _snapshot.value.copy(state = WifiDirectState.Discovering, error = null)
        p2p.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                fail("peer discovery failed: ${reasonName(reason)}")
                scheduleReconnect()
            }
        })
    }

    fun connect(device: WifiP2pDevice) {
        require(device.deviceAddress.isNotBlank()) { "peer has no device address" }
        val p2p = manager ?: return fail("Wi-Fi Direct manager unavailable")
        val p2pChannel = channel ?: return fail("Wi-Fi Direct channel unavailable")
        if (!hasDiscoveryPermission()) return fail("Nearby Wi-Fi/location permission is required")
        // Remember the user's choice, not the framework object.  The address
        // is stable across peer-list callbacks and allows bounded reconnect.
        reconnectDeviceAddress = device.deviceAddress.trim().uppercase()
        _snapshot.value = _snapshot.value.copy(state = WifiDirectState.Connecting, error = null)
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        p2p.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                fail("peer connection failed: ${reasonName(reason)}")
                scheduleReconnect()
            }
        })
    }

    /** Returns the endpoint currently known for a connected group-owner/client. */
    fun connectedEndpoint(nodeId: String, port: Int): PeerEndpoint? {
        // When this phone is group owner, groupOwnerAddress is this phone's
        // address, not a client's address.  Returning it as the peer endpoint
        // would silently route packets back to ourselves; a group-owner peer
        // address must come from handshake/configuration instead.
        if (_snapshot.value.groupOwner) return null
        val address = _snapshot.value.groupOwnerAddress ?: return null
        return PeerEndpoint(nodeId, address.hostAddress ?: return null, port)
    }

    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                if (!enabled) fail("Wi-Fi Direct is disabled")
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val network = intent.getParcelableExtraCompat<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (network?.isConnected == true) requestConnectionInfo() else {
                    _snapshot.value = _snapshot.value.copy(state = WifiDirectState.Idle, groupOwnerAddress = null)
                    scheduleReconnect()
                }
            }
        }
    }

    private fun requestPeers() {
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasDiscoveryPermission()) return fail("Nearby Wi-Fi/location permission is required")
        p2p.requestPeers(p2pChannel) { list: WifiP2pDeviceList ->
            val previousState = _snapshot.value.state
            val targetAddress = reconnectDeviceAddress
            val reconnectTarget = targetAddress?.let { address ->
                list.deviceList.firstOrNull { it.deviceAddress.equals(address, ignoreCase = true) }
            }
            _snapshot.value = _snapshot.value.copy(
                state = if (list.deviceList.isEmpty()) WifiDirectState.Discovering else WifiDirectState.PeersFound,
                peers = list.deviceList.toList(),
                error = null
            )
            if (reconnectTarget != null &&
                previousState != WifiDirectState.Connecting &&
                previousState != WifiDirectState.Connected) {
                // Stop the scan loop before issuing connect so repeated peer
                // broadcasts cannot enqueue duplicate connect requests.
                cancelReconnect()
                connect(reconnectTarget)
            } else if (targetAddress == null && list.deviceList.isNotEmpty()) {
                // With no prior user choice, discovery is complete and the
                // UI can let the user choose a peer explicitly.
                cancelReconnect()
            }
        }
    }

    private fun requestConnectionInfo() {
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasDiscoveryPermission()) return fail("Nearby Wi-Fi/location permission is required")
        p2p.requestConnectionInfo(p2pChannel) { info: WifiP2pInfo ->
            _snapshot.value = _snapshot.value.copy(
                state = WifiDirectState.Connected,
                groupOwner = info.isGroupOwner,
                groupOwnerAddress = info.groupOwnerAddress,
                error = null
            )
            cancelReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!reconnect || retryJob?.isActive == true || !registered) return
        retryJob = scope.launch {
            var delayMs = 1_000L
            while (isActive && registered) {
                delay(delayMs)
                discoverPeers()
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun cancelReconnect() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun hasDiscoveryPermission(): Boolean {
        val nearbyGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        val locationGranted = Build.VERSION.SDK_INT >= 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return nearbyGranted && locationGranted
    }

    private fun fail(message: String) { _snapshot.value = _snapshot.value.copy(state = WifiDirectState.Error, error = message) }

    private fun reasonName(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.BUSY -> "BUSY"
        WifiP2pManager.ERROR -> "ERROR"
        else -> reason.toString()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)
}
