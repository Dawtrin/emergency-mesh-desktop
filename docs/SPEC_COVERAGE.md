# Specification coverage audit

Audit date: 2026-09-08. The source-level items below are checked against
`Emergency_Mesh_Command_System_Spec.md`. `PASS (code/test)` means the behavior
is implemented in this repository and covered by the available build/tests;
`CHƯA XÁC MINH` is deliberately reserved for behavior that needs the actual
phones, Wi-Fi Direct stack, GPS or map files.

| Specification area | Current coverage | Evidence / limitation |
|---|---|---|
| Canonical `MeshPacket` v1, snake_case Gson JSON, TTL/hop/route metadata | PASS (code/test) | Java and Kotlin protocol models, Java↔Kotlin fixture checks, forwarding checksum recomputation |
| SHA-256 integrity checksum over protocol metadata and payload | PASS (code/test) | `ChecksumUtil` implementations and tamper/fixture tests; checksum is not authentication |
| Desktop ServerSocket gateway, UTF-8 framing, 64 KiB bound, timeouts, bounded workers | PASS (code/test) | `SocketServer` and integration/overload tests; configurable port with 8888 default |
| Desktop JavaFX dashboard, triage colors, alarm, dispatch form, offline Leaflet map | PASS (code/test) | FXML/controllers, `BaseStationSeverityStyleTest`, offline Leaflet assets, map bridge and JavaFX/controller tests |
| Desktop SQLite persistence, migrations, repositories, dispatch outbox/ACK | PASS (code/test) | JDBC migration/repository/outbox/E2E tests, restart recovery, and `BaseStationDuplicateAckTest` |
| Desktop three-process/LAN demo configuration and end-to-end flow | PASS (code/test; LAN/process run pending) | `NodeConfig`, demo profile, socket/E2E tests; the automated harness uses real sockets in one JVM. Separate Windows processes, multiple computers, LAN routing and firewall rules remain environment-specific. Phase 5 reliability/preflight hardening is tracked separately and is not used as proof here. |
| Android Kotlin build and protocol compatibility | PASS (code/test; emulator smoke) | `android-app/`, debug APK build, 12 JVM tests including loopback UTF-8 transport and routing/outbox tests; Pixel 8 Pro emulator cold-started the APK, kept `MeshForegroundService` foreground with TCP `*:8888` listening and accepted a canonical SOS through adb forwarding. |
| Android `WifiP2pManager` discovery/connect/dynamic receiver/reconnect | PASS (code; hardware pending) | `WifiDirectManager` and Compose peer Connect action; actual discovery/group-owner behavior is CHƯA XÁC MINH |
| Android foreground socket service, WakeLock, bounded coroutines and framing | PASS (code/test; emulator smoke; physical-device pending) | `MeshForegroundService`, `SocketMeshTransport`; Pixel 8 Pro emulator verified foreground notification/process survival and TCP `*:8888` before/after HOME. Vendor-ROM background survival and physical-device behavior are CHƯA XÁC MINH |
| Android Room inbox/outbox, duplicate suppression, TTL/hop forwarding, retry | PASS (code/test; hardware pending) | `MobileRoutingEngineDeliveryTest` proves finite max-attempt delivery, final ACK timeout, ACK-before-timeout delivery, durable ACK retry, and duplicate ACK reuse; device restart/multi-hop run is CHƯA XÁC MINH |
| Android one-tap SOS, GPS timeout/manual fallback, statuses, dispatch alert/ACK | PASS (code) | Compose UI, Fused provider, status flow, high-priority notification/vibration; actual GPS quality is CHƯA XÁC MINH |
| Android OsmDroid offline map | PASS (code; map data pending) | Data connection disabled and local tile path configured; selected-area tile coverage is CHƯA XÁC MINH |
| End-to-end Android source → relay → Base → dispatch → source on real phones | CHƯA XÁC MINH | A single Pixel 8 Pro emulator smoke-tested app startup, Room ingress and local port visibility only; no Wi-Fi Direct relay, multi-phone path, or end-to-end dispatch/ACK was verified |
| No Internet/4G/BTS demo proof | CHƯA XÁC MINH | Requires a recorded run with mobile data/Internet disabled and the target devices |

## What remains before calling the full specification verified

1. Build/install the APK on the exact two or three demo phones (the available
   Pixel 8 Pro emulator is only a smoke-test environment).
2. Record discovery, group-owner/client addresses, handshake and reconnect.
3. Run the five-step no-Internet SOS/relay/Base/dispatch/ACK scenario and keep
   packet IDs, route history, hop counts and dashboard screenshots.
4. Confirm GPS accuracy and copy/test the offline tile archive for the chosen
   demo area.

The desktop midterm path is independently runnable without Android. The Android
module is implemented in source and build artifacts, and has a limited emulator
smoke result, but Wi-Fi Direct, GPS quality, multi-hop/store-and-forward between
devices, and the no-Internet field demo stay explicitly unverified until the
target-phone test run exists.
