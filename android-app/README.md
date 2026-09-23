# Emergency Mesh Rescue — Android field node

This module is the Kotlin Android implementation of the field-node part of the
project specification. It uses the same `MeshPacket` v1 JSON and canonical
SHA-256 integrity checksum as the Java desktop app.

## Toolchain

- Android Gradle Plugin 8.7.3
- Kotlin 2.1.0 / Compose compiler plugin
- `minSdk 26`, `targetSdk 35`, Java/Kotlin JVM target 17
- Room 2.7.0, Coroutines 1.7.3, Gson 2.10.1
- Google Play Services Location 21.3.0 and OsmDroid 6.1.18

Open `android-app/` in Android Studio or run `gradlew.bat :app:assembleDebug`
from that directory. The generated APK is a debug artifact and is not a
substitute for testing on the actual phone models used for the demonstration.

## Runtime behavior

- `MeshForegroundService` owns the newline-framed UTF-8 TCP transport and
  persists packets in Room. The service uses a bounded 64 KiB frame, socket
  timeouts, structured IO coroutines, a notification, and bounded WakeLock/
  WifiLock lifetimes.
- `WifiDirectManager` uses the real Android `WifiP2pManager` and dynamic
  `BroadcastReceiver` lifecycle. The UI shows discovered peers and exposes a
  real Connect action. A Wi-Fi Direct client automatically uses the group-owner
  address as its preferred one-hop route and retries the selected peer when it
  reappears. The outbound transport sends a handshake but does not wait for a
  response, preserving compatibility with the legacy desktop one-way send; the
  temporary route ID is replaced only after a peer handshake/packet is received
  (or an explicit route is saved). A group owner learns client routes from
  incoming handshakes instead of guessing an address.
- `MobileRoutingEngine` validates checksum and TTL, uses a bounded LRU seen
  cache, mutates forwarding metadata exactly once, learns explicit peer routes,
  sends idempotent durable ACKs, and retries a Room outbox with capped jitter.
  Each packet has a finite delivery budget; a final successful SOS/dispatch send
  waits once for its ACK and then becomes terminal `FAILED` with no automatic
  retry if the ACK never arrives.
- The Compose UI has a large one-tap SOS action, validation, GPS with a 10 s
  timeout, explicit manual-coordinate fallback, machine-readable delivery
  status, high-priority dispatch notification/vibration, and an OsmDroid view
  with data connection disabled. SOS/ACK packets use the preferred Wi-Fi Direct
  route when an exact destination route is not yet known; dispatch packets use
  a learned exact reverse route.

## Permissions and offline operation

The app requests `NEARBY_WIFI_DEVICES` (API 33+), location, and notification
permission at runtime. Wi-Fi Direct and GPS are operating-system facilities;
Internet/mobile data is not required for the local TCP flow. OsmDroid is set to
`setUseDataConnection(false)` and must be supplied with a local tile cache or
offline archive under the app's `files/osmdroid/tiles` area. If the map is
missing, the SOS flow remains usable and the UI shows the limitation.

## Demo setup

1. Install the debug APK on each phone, grant Nearby devices, Location and
   Notifications permissions, and leave mobile data/Internet disabled.
2. Start the foreground service from the app. In the Wi-Fi Direct card, wait
   for discovery and press **Kết nối** on the intended relay/base peer.
3. If a desktop Base Station or a fixed endpoint is used, enter its IP/port in
   the manual peer section. The first configured peer is the fallback next hop
   for SOS and ACK; the relay should explicitly configure its Base endpoint.
4. Enter the SOS form. GPS is attempted for at most ten seconds; coordinates
   may be entered manually when GPS is unavailable. A queued packet remains in
   Room and is retried when a route becomes available.

The Android service listens on port `8888` by default. A different local port
can be placed in the `mesh` shared preferences by the host app before starting
the service; the handshake advertises the actual configured port.

## Device verification status

The available Pixel 8 Pro emulator has been used for a limited smoke check: the
debug APK cold-started, `MeshForegroundService` remained foreground with its
notification, TCP `*:8888` stayed listening after HOME, and a canonical SOS was
accepted through adb port forwarding. This does not exercise radio behavior.
The following remain **NOT YET VERIFIED** until tested on the exact demo phones
and Android versions:

1. Wi-Fi Direct peer discovery and group-owner/client endpoint behavior.
2. Background service survival and notification behavior on the vendor ROM.
3. Two-device handshake, heartbeat/SOS, disconnect and reconnect.
4. Three-hop field topology and Store-and-Forward over multiple Wi-Fi Direct
   groups (many Android versions cannot keep one phone in multiple groups).
5. GPS fix quality and offline tile coverage on the selected demo area.

Do not use a simulator log as evidence for those hardware-only criteria.
