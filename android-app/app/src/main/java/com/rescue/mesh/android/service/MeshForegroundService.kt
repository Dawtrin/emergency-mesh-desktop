package com.rescue.mesh.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import androidx.core.app.NotificationCompat
import com.rescue.mesh.android.MeshApplication
import com.rescue.mesh.android.network.PeerEndpoint
import com.rescue.mesh.android.network.SocketMeshTransport
import com.rescue.mesh.android.network.WifiDirectManager
import com.rescue.mesh.android.routing.MobileRoutingEngine
import com.rescue.mesh.android.routing.RoutingEvent
import com.rescue.mesh.android.data.MessageStatusEntity
import com.rescue.mesh.android.data.MeshPacketEntity
import com.rescue.mesh.android.protocol.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns Wi-Fi Direct discovery and socket lifetime independently from the UI.
 * A bound Activity can observe Flows, while the service remains alive when the
 * app is backgrounded for the demo window.
 */
class MeshForegroundService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "emergency_mesh_transport"
        const val SOS_NOTIFICATION_CHANNEL_ID = "emergency_mesh_sos"
        const val DISPATCH_NOTIFICATION_CHANNEL_ID = "emergency_mesh_dispatch"
        const val NOTIFICATION_ID = 1001
        const val SOS_NOTIFICATION_ID = 1003
        const val DISPATCH_NOTIFICATION_ID = 1002
        const val DEFAULT_LISTEN_PORT = 8_888
        const val ACTION_START = "com.rescue.mesh.android.START"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transport: SocketMeshTransport? = null
    private var engine: MobileRoutingEngine? = null
    private var wifiDirect: WifiDirectManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service(): MeshForegroundService = this@MeshForegroundService
        fun registerPeer(nodeId: String, host: String, port: Int) = engine?.registerRoute(PeerEndpoint(nodeId, host, port))
        fun connectWifiPeer(device: WifiP2pDevice) = requireWifiDirect().connect(device)
        suspend fun submit(packet: MeshPacket): Boolean = requireEngine().submit(packet)
        fun observePackets(): Flow<List<MeshPacketEntity>> = requireEngine().observePackets()
        fun observeStatuses(): Flow<List<MessageStatusEntity>> = requireEngine().observeStatuses()
        fun events(): SharedFlow<RoutingEvent> = requireEngine().events
        fun transportState() = requireEngine().transportState
        fun wifiDirectState(): StateFlow<com.rescue.mesh.android.network.WifiDirectSnapshot> = requireWifiDirect().snapshot
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val application = application as MeshApplication
        val configuredPort = getSharedPreferences("mesh", MODE_PRIVATE).getInt("listen_port", DEFAULT_LISTEN_PORT)
        val socketTransport = SocketMeshTransport(
            nodeId = application.nodeId,
            listenPort = configuredPort,
            parentScope = serviceScope
        )
        transport = socketTransport
        val meshEngine = MobileRoutingEngine(application.nodeId, socketTransport, application.repository, serviceScope)
        engine = meshEngine
        serviceScope.launch {
            meshEngine.events.collect { event ->
                when (event) {
                    is RoutingEvent.Received -> {
                        if (event.type == MeshPacket.TYPE_SOS_BROADCAST ||
                            event.type == MeshPacket.TYPE_SOS_DATA) {
                            // Received is emitted only after both duplicate
                            // gates, so a retransmitted SOS does not notify
                            // the relay operator a second time.
                            notifySos(event)
                        }
                    }
                    is RoutingEvent.DispatchReceived -> notifyDispatch(event.packet)
                    else -> Unit
                }
            }
        }
        val directManager = WifiDirectManager(this, serviceScope)
        wifiDirect = directManager
        serviceScope.launch {
            directManager.snapshot.collectLatest { snapshot ->
                // A Wi-Fi Direct client is given the group-owner address. The
                // outbound transport does not wait for a handshake response,
                // so this temporary route ID is replaced only after the peer
                // talks back (or the user saves an explicit route). Group
                // owners learn client routes from incoming handshakes instead
                // of guessing addresses.
                if (snapshot.state == com.rescue.mesh.android.network.WifiDirectState.Connected &&
                    !snapshot.groupOwner && snapshot.groupOwnerAddress != null) {
                    snapshot.groupOwnerAddress.hostAddress?.let { host ->
                        runCatching {
                            meshEngine.setDefaultRoute(PeerEndpoint("WIFI_DIRECT_GROUP_OWNER", host, configuredPort))
                        }
                    }
                }
            }
        }
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Transport đang khởi động"))
        if (!started) {
            started = true
            serviceScope.launch {
                runCatching { wifiDirect?.start() }
                    .onFailure { updateNotification("Wi-Fi Direct chưa sẵn sàng: ${it.message}") }
                runCatching { engine?.start() }
                    .onFailure { updateNotification("Lỗi transport: ${it.message}") }
                    .onSuccess { updateNotification("Transport đang chạy — ${(application as MeshApplication).nodeId}") }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        started = false
        engine?.close()
        engine = null
        wifiDirect?.stop()
        wifiDirect = null
        transport = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun requireEngine(): MobileRoutingEngine = requireNotNull(engine) { "mesh service is not initialized" }

    private fun requireWifiDirect(): WifiDirectManager = requireNotNull(wifiDirect) { "Wi-Fi Direct is not initialized" }

    private fun acquireLocks() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EmergencyMesh::Transport").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        val wifi = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EmergencyMesh::Transport").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Emergency Mesh transport",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the local rescue transport visible while running" }
            val dispatchChannel = NotificationChannel(
                DISPATCH_NOTIFICATION_CHANNEL_ID,
                "Emergency Mesh dispatch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for rescue commands received from the base station"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 300L, 150L, 500L)
                setShowBadge(true)
            }
            val sosChannel = NotificationChannel(
                SOS_NOTIFICATION_CHANNEL_ID,
                "Emergency Mesh SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a nearby field node reports an SOS"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 300L, 150L, 500L)
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java).apply {
                createNotificationChannel(channel)
                createNotificationChannel(sosChannel)
                createNotificationChannel(dispatchChannel)
            }
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Emergency Mesh Rescue")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notifySos(event: RoutingEvent.Received) {
        val from = event.fromNodeId?.takeIf { it.isNotBlank() } ?: "peer"
        val message = "SOS từ $from · packet ${event.packetId.take(8)}"
        val alert = NotificationCompat.Builder(this, SOS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Tín hiệu SOS gần đây")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        getSystemService(NotificationManager::class.java).notify(SOS_NOTIFICATION_ID, alert)
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 300L, 150L, 500L), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0L, 300L, 150L, 500L), -1)
        }
    }

    private fun notifyDispatch(packet: MeshPacket) {
        val message = packet.payload?.message?.takeIf { it.isNotBlank() } ?: "Bạn có lệnh điều phối mới"
        val alert = NotificationCompat.Builder(this, DISPATCH_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Lệnh cứu hộ mới")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        getSystemService(NotificationManager::class.java).notify(DISPATCH_NOTIFICATION_ID, alert)
        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 300L, 150L, 500L), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0L, 300L, 150L, 500L), -1)
        }
    }
}
