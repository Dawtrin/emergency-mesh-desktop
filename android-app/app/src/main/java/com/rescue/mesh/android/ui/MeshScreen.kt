package com.rescue.mesh.android.ui

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import com.rescue.mesh.android.location.LocationSample
import com.rescue.mesh.android.network.WifiDirectSnapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

private val EmergencyRed = Color(0xFFB3261E)

@Composable
fun MeshScreen(
    state: MeshUiState,
    onSubmitSos: (String, String, String, Int, String, Double?, Double?, (Boolean) -> Unit) -> Unit,
    onRegisterPeer: (String, String, Int) -> Unit,
    onConnectWifiPeer: (WifiP2pDevice) -> Unit
) {
    var senderName by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf("FLOOD_TRAPPED") }
    var message by remember { mutableStateOf("") }
    var victimCount by remember { mutableStateOf("1") }
    var severity by remember { mutableStateOf("CRITICAL") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var peerId by remember { mutableStateOf("") }
    var peerHost by remember { mutableStateOf("") }
    var peerPort by remember { mutableStateOf("8888") }
    var formError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101114)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Emergency Mesh Rescue", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Node ${state.nodeId.ifBlank { "chưa cấu hình" }} · ${state.transportState}", color = Color.LightGray)
            }
            item {
                WifiDirectCard(state.wifiDirect, onConnectWifiPeer)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gửi tín hiệu SOS", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(senderName, { senderName = it }, Modifier.fillMaxWidth(), label = { Text("Tên người báo") }, singleLine = true)
                        OutlinedTextField(alertType, { alertType = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Loại cảnh báo (MEDICAL/FLOOD_TRAPPED/LANDSLIDE)") }, singleLine = true)
                        OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Mô tả tình huống") }, minLines = 2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(victimCount, { victimCount = it }, Modifier.weight(1f), label = { Text("Số nạn nhân") }, singleLine = true)
                            OutlinedTextField(severity, { severity = it.uppercase() }, Modifier.weight(1f), label = { Text("Mức độ") }, singleLine = true)
                        }
                        Text("GPS tự động có timeout 10 giây. Nếu GPS không khả dụng, nhập tọa độ thủ công:", color = Color.LightGray)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text("Vĩ độ") }, singleLine = true)
                            OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text("Kinh độ") }, singleLine = true)
                        }
                        formError?.let { Text(it, color = Color(0xFFFFB4AB)) }
                        Button(
                            onClick = {
                                val count = victimCount.toIntOrNull()
                                val lat = latitude.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                                val lon = longitude.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                                formError = when {
                                    senderName.isBlank() -> "Tên người báo không được rỗng"
                                    message.length > 500 -> "Mô tả tối đa 500 ký tự"
                                    count == null || count < 0 -> "Số nạn nhân không hợp lệ"
                                    alertType !in setOf("MEDICAL", "FLOOD_TRAPPED", "LANDSLIDE") -> "alert type không hợp lệ"
                                    severity !in setOf("CRITICAL", "HIGH", "MEDIUM") -> "severity không hợp lệ"
                                    (latitude.isNotBlank() || longitude.isNotBlank()) && (lat == null || lon == null) -> "Tọa độ thủ công không hợp lệ"
                                    lat != null && lat !in -90.0..90.0 -> "Vĩ độ phải từ -90 đến 90"
                                    lon != null && lon !in -180.0..180.0 -> "Kinh độ phải từ -180 đến 180"
                                    else -> null
                                }
                                if (formError == null && !sending) {
                                    sending = true
                                    onSubmitSos(senderName, alertType, message, count!!, severity, lat, lon) { accepted ->
                                        sending = false
                                        if (!accepted) formError = "SOS chưa được chấp nhận; xem lỗi kết nối bên dưới."
                                    }
                                }
                            },
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "GỬI SOS KHẨN CẤP" },
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (sending) "ĐANG GỬI…" else "GỬI SOS KHẨN CẤP", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kết nối peer (IP/port do handshake hoặc cấu hình demo)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(peerId, { peerId = it }, Modifier.weight(1f), label = { Text("Node ID") }, singleLine = true)
                            OutlinedTextField(peerPort, { peerPort = it }, Modifier.width(100.dp), label = { Text("Port") }, singleLine = true)
                        }
                        OutlinedTextField(peerHost, { peerHost = it }, Modifier.fillMaxWidth(), label = { Text("IP/hostname") }, singleLine = true)
                        OutlinedButton(onClick = { peerPort.toIntOrNull()?.let { port -> if (peerId.isNotBlank() && peerHost.isNotBlank()) onRegisterPeer(peerId, peerHost, port) } }) {
                            Text("Lưu route peer")
                        }
                    }
                }
            }
            item { OfflineMapCard(state.lastLocation) }
            item { Text("Trạng thái delivery", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            items(state.statuses, key = { it.packetId }) { row -> DeliveryRow(row) }
            item { Text("Nhật ký mesh", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            items(state.eventLog.size) { index ->
                Text(state.eventLog[index], color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            item { Text("Lệnh điều phối nhận được", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            items(state.dispatches, key = { it.packetId }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.message.orEmpty(), color = Color.White)
                        Text("packet=${row.packetId.take(8)} · source=${row.sourceNodeId}", color = Color.LightGray)
                    }
                }
            }
            state.lastError?.let { error -> item { Text(error, color = Color(0xFFFFB4AB)) } }
        }
    }
}

@Composable
private fun WifiDirectCard(snapshot: WifiDirectSnapshot, onConnect: (WifiP2pDevice) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wi-Fi Direct", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Trạng thái: ${snapshot.state}${snapshot.error?.let { " · $it" }.orEmpty()}", color = Color.LightGray)
            if (snapshot.peers.isEmpty()) {
                Text("Chưa thấy peer. Bật Wi-Fi Direct trên các điện thoại rồi chờ quét.", color = Color.Gray)
            } else {
                snapshot.peers.forEach { device ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(device.deviceName.ifBlank { "Wi-Fi peer" }, color = Color.White)
                            Text(device.deviceAddress, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onConnect(device) }) { Text("Kết nối") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryRow(row: DeliveryStatusRow) {
    val statusColor = when (row.status) {
        com.rescue.mesh.android.routing.DeliveryStatus.FAILED,
        com.rescue.mesh.android.routing.DeliveryStatus.EXPIRED -> Color(0xFFFFB4AB)
        com.rescue.mesh.android.routing.DeliveryStatus.DELIVERED -> Color(0xFFB7F397)
        else -> Color.White
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
        Column(Modifier.padding(12.dp)) {
            Text("${statusLabel(row.status)} (${row.status.name})", color = statusColor, fontWeight = FontWeight.Bold)
            Text("packet=${row.packetId.take(8)} · hops=${row.hopCount} · route=${row.routeHistory.joinToString(" → ")}", color = Color.LightGray)
            row.detail?.let { Text(it, color = Color.Gray) }
        }
    }
}

@Composable
private fun OfflineMapCard(lastLocation: LocationSample?) {
    val context = LocalContext.current
    val tileDirectory = remember { File(context.filesDir, "osmdroid/tiles") }
    val hasOfflineTiles = remember { tileDirectory.exists() && tileDirectory.walkTopDown().any { it.isFile } }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D21))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Bản đồ hiện trường (OsmDroid offline)", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasOfflineTiles) "Đã tắt data connection · tile offline sẵn sàng"
                else "Chưa có tile offline trong files/osmdroid/tiles · SOS vẫn hoạt động",
                color = if (hasOfflineTiles) Color(0xFFB7F397) else Color(0xFFFFB4AB)
            )
            lastLocation?.let {
                Text("Điểm SOS: ${"%.6f".format(it.location.latitude)}, ${"%.6f".format(it.location.longitude)} · ${it.source}", color = Color.LightGray)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                factory = { createOfflineMap(it) },
                update = { map -> updateSosMarker(map, lastLocation) }
            )
        }
    }
}

private fun createOfflineMap(context: Context): MapView {
    val configuration = Configuration.getInstance()
    val basePath = File(context.filesDir, "osmdroid")
    basePath.mkdirs()
    configuration.osmdroidBasePath = basePath
    configuration.osmdroidTileCache = File(basePath, "tiles")
    configuration.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setUseDataConnection(false)
        setMultiTouchControls(true)
        controller.setZoom(3.0)
        controller.setCenter(org.osmdroid.util.GeoPoint(16.074512, 108.150245))
        contentDescription = "Bản đồ ngoại tuyến; không sử dụng Internet"
    }
}

private fun updateSosMarker(map: MapView, sample: LocationSample?) {
    val location = sample?.location ?: return
    if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
    val point = org.osmdroid.util.GeoPoint(location.latitude, location.longitude)
    val marker = map.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "SOS location" }
        ?: Marker(map).also {
            it.title = "SOS location"
            it.snippet = "Emergency Mesh"
            map.overlays.add(it)
        }
    marker.position = point
    marker.relatedObject = sample
    map.controller.setCenter(point)
    map.invalidate()
}

private fun statusLabel(status: com.rescue.mesh.android.routing.DeliveryStatus): String = when (status) {
    com.rescue.mesh.android.routing.DeliveryStatus.PENDING -> "Đang chờ kết nối"
    com.rescue.mesh.android.routing.DeliveryStatus.IN_FLIGHT -> "Đang chuyển tiếp"
    com.rescue.mesh.android.routing.DeliveryStatus.SENT_WAITING_ACK -> "Chờ xác nhận"
    com.rescue.mesh.android.routing.DeliveryStatus.DELIVERED -> "Đã tới trạm"
    com.rescue.mesh.android.routing.DeliveryStatus.FAILED -> "Lỗi gửi"
    com.rescue.mesh.android.routing.DeliveryStatus.EXPIRED -> "Hết TTL"
}
