# Android field-node implementation

The repository now contains an independent Android module in `android-app/`.
It implements the field-node responsibilities from the technical specification:

| Specification item | Implementation |
|---|---|
| Kotlin protocol model and checksum | `android-app/app/src/main/java/com/rescue/mesh/android/protocol/` |
| Wi-Fi Direct discovery/reconnect | `network/WifiDirectManager.kt` and `WifiP2pReceiver.kt` |
| Foreground transport service | `service/MeshForegroundService.kt` |
| UTF-8 framed sockets and handshake | `network/SocketMeshTransport.kt` |
| LRU duplicate suppression and routing | `routing/SeenPacketLruCache.kt`, `MobileRoutingEngine.kt` |
| Room inbox/outbox and status | `data/` |
| Compose SOS/status UI | `MainActivity.kt`, `ui/MeshScreen.kt`, `ui/MeshViewModel.kt` |
| GPS and manual fallback | `location/` |
| Offline map | OsmDroid `MapView` with data connection disabled |

The service exposes the discovered peer list to Compose, so a user can select a
real `WifiP2pDevice` and invoke `WifiP2pManager.connect`. On the client side,
the group-owner address is installed as a temporary default next hop. The
outbound transport sends a handshake but deliberately does not wait for a
handshake response, so that the legacy desktop one-way send remains safe; the
temporary route ID is not synchronously replaced by that send. Incoming
handshakes/packets teach the routing engine a peer/reverse source route when
the peer talks back, which is what allows a Base Station to send a dispatch
back through the relay in the three-node demo. This learning limitation must
be covered by the real-device setup or an explicit peer route.

The Room database stores both packet status and learned endpoint records. On a
service restart, valid stored endpoints are restored before the outbox loop is
started; delivered/expired records older than seven days are pruned. Duplicate
dispatches are acknowledged for protocol reliability but do not create a second
notification after the packet has already been persisted.

The Android implementation is deliberately explicit about hardware limits. A
Pixel 8 Pro emulator smoke check confirmed APK startup, foreground-service
survival, the local `8888` listener and Room ingress through adb forwarding;
that result is not evidence of Wi-Fi Direct or a multi-device mesh:
Wi-Fi Direct IP discovery and multi-hop feasibility must be recorded from real
devices, not inferred from a fake test. The desktop Java demo remains useful as
the reproducible midterm path. The Android source compiles and has loopback
protocol/transport tests, but the device-only items below remain CHƯA XÁC MINH:

- actual peer discovery, group-owner/client address behavior and vendor ROM
  background-service survival;
- two/three-device Wi-Fi Direct handshake, reconnect and multi-hop relay;
- GPS fix quality and the selected area's offline tile coverage.
