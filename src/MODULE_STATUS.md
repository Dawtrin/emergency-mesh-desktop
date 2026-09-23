# MODULE STATUS — EMERGENCY MESH RESCUE
*Cập nhật sau khi triển khai Phase 5 — chờ Codex nghiệm thu*

## TỔNG QUAN TIẾN ĐỘ
```
MODULE 1 (Shared Core & Protocol v1): ██████████ 100% — HOÀN THÀNH (Phase 1)
MODULE 2 (Client Node):               ██████████ 100% — HOÀN THÀNH
MODULE 3 (Base Station UI):           ██████████ 100% — HOÀN THÀNH
MODULE 4 (SQLite Persistence & Gateway): ██████████ 100% — HOÀN THÀNH (Phase 2)
MODULE 5 (True Offline Desktop Map):  ██████████ 100% — HOÀN THÀNH (Phase 3)
MODULE 6 (Desktop LAN Demo Flow):     ██████████ 100% — HOÀN THÀNH (Phase 4)
MODULE 7 (Desktop Reliability):       ██████████ 100% — ĐÃ TRIỂN KHAI (Phase 5, chưa nghiệm thu)
```

---

## MODULE 1 — Shared Model & Routing Engine (Canonical Protocol v1)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `model/MeshPacket.java` | ✅ HOÀN THÀNH | POJO + Gson + Canonical SHA-256 Checksum |
| `model/PacketValidator.java` | ✅ HOÀN THÀNH | Structural & Discovery Info Validation |
| `routing/SeenPacketCache.java` | ✅ HOÀN THÀNH | ConcurrentHashMap + TTL expiry |
| `routing/RoutingEngine.java` | ✅ HOÀN THÀNH | Core Flooding Routing + Callback API + onAckReceived |
| `util/ChecksumUtil.java` | ✅ HOÀN THÀNH | Deterministic Canonical JSON SHA-256 |
| `util/PacketFactory.java` | ✅ HOÀN THÀNH | Factory tạo SOS, DISPATCH, ACK, HEARTBEAT canonical |

---

## MODULE 2 — Simulated Client Node (Victim & Relay)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `SimulatedNodeApp.java` | ✅ HOÀN THÀNH | JavaFX Application Entry Point |
| `network/SocketClient.java` | ✅ HOÀN THÀNH | TCP Client (Gateway Pattern, UTF-8, Retry, Timeout) |
| `network/NodeConfig.java` | ✅ HOÀN THÀNH | Command-line Argument Parser |
| `ui/controller/NodeClientController.java` | ✅ HOÀN THÀNH | JavaFX Controller (MVC + Observer, Platform.runLater) |
| `resources/fxml/NodeClient.fxml` | ✅ HOÀN THÀNH | Giao diện iOS 27 style (SOS form + Live Log) |

---

## MODULE 3 — Base Station Server & Dashboard

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `BaseStationApp.java` | ✅ HOÀN THÀNH | JavaFX Dashboard Application Entry Point (--map-file, --relay-port, --relay-host) |
| `BaseStationLauncher.java` | ✅ HOÀN THÀNH | Fat JAR Module System Workaround |
| `network/SocketServer.java` | ✅ HOÀN THÀNH | Hardened TCP Gateway (Bounded Pool 10/20/50, 64KB Frame Limit, 5s Read Timeout, UTF-8, Graceful Shutdown) |
| `ui/controller/BaseStationController.java` | ✅ HOÀN THÀNH | Main Dashboard Controller (Stats, Map Bridge, Table, Audio, SQLite Storage & Outbox Integration, LifecycleState state machine, JavaFX thread confinement via UiDispatcher, Offline Map Manager) |
| `ui/component/VictimTableRow.java` | ✅ HOÀN THÀNH | JavaFX TableView Bean (Adapter Pattern, fromSosRecord) |
| `audio/AlarmPlayer.java` | ✅ HOÀN THÀNH | Phase Accumulation Siren Tone Synthesizer & Fallbacks |
| `resources/fxml/BaseStation.fxml` | ✅ HOÀN THÀNH | Dashboard FXML (Stats cards, Table, WebView Map, Dispatch) |
| `resources/map/leaflet_offline.html` | ✅ HOÀN THÀNH | Bản đồ Leaflet Fully Offline (không CDN, không URL ngoài, queue pre-readiness, safe JSON bridge, focus marker, route rendering, error overlay) |
| `resources/map/leaflet/*` | ✅ HOÀN THÀNH | Bundled Leaflet 1.9.4 CSS, JS, và images (cục bộ 100%) |
| `resources/css/dark-theme.css` | ✅ HOÀN THÀNH | Giao diện Apple iOS 27 Clean Modern Dark Theme |

---

## MODULE 4 — SQLite Persistence & Dispatch Outbox (Phase 2)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `storage/DatabaseConfig.java` | ✅ HOÀN THÀNH | Cấu hình path runtime `data/mesh_desktop.db` và in-memory test |
| `storage/DatabaseManager.java` | ✅ HOÀN THÀNH | Quản lý kết nối SQLite, PRAGMA foreign_keys = ON, WAL, busy_timeout 5s |
| `storage/migration/Migration.java` | ✅ HOÀN THÀNH | Interface versioned migration |
| `storage/migration/MigrationManager.java` | ✅ HOÀN THÀNH | Quản lý áp dụng migration tự động, idempotent |
| `storage/migration/V1InitialSchemaMigration.java` | ✅ HOÀN THÀNH | 5 bảng (`packets`, `sos_events`, `nodes`, `dispatch_commands`, `routes`) + 9 indexes |
| `storage/model/*` | ✅ HOÀN THÀNH | PacketRecord, SosEventRecord, NodeRecord, DispatchRecord, RouteRecord |
| `storage/repository/*` | ✅ HOÀN THÀNH | CRUD & query repositories cho 5 bảng |
| `storage/BaseStationStorageService.java` | ✅ HOÀN THÀNH | Lưu gói tin idempotent, khôi phục trạng thái Dashboard khi restart |
| `service/DispatchOutboxService.java` | ✅ HOÀN THÀNH | Hàng đợi dispatch với Exponential Backoff (2s, 4s, 8s, 16s, max 60s, max 5 attempts), ACK matching |

---

## MODULE 5 — True Offline Desktop Map (Phase 3)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `docs/architecture/ADR-001-offline-map-tile-format.md` | ✅ HOÀN THÀNH | ADR lựa chọn MBTiles v1.3 làm định dạng tile cục bộ |
| `map/MBTilesReader.java` | ✅ HOÀN THÀNH | Đọc tile từ file SQLite MBTiles, tự động TMS/XYZ, LRU cache 256 entries và native zoom range |
| `map/LocalTileServer.java` | ✅ HOÀN THÀNH | Loopback HTTP Server (chỉ bind 127.0.0.1, port động, chống path traversal, 4 worker threads, 404 fallback 1x1 transparent PNG) |
| `map/OfflineMapManager.java` | ✅ HOÀN THÀNH | Quản lý vòng đời MBTilesReader & LocalTileServer, phân giải path ưu tiên (--map-file, env, default) |
| `src/test/resources/fixtures/test-tiles.mbtiles` | ✅ HOÀN THÀNH | Fixture MBTiles tối thiểu hợp lệ phục vụ kiểm thử tự động và được giữ lại khi clone repository |

---

## MODULE 6 — Desktop Multi-Process / LAN Demo (Phase 4)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `network/NodeConfig.java` | ✅ HOÀN THÀNH | Cấu hình/validation bind host, port, next hop, relay và victim route cho local hoặc LAN |
| `routing/RoutingEngine.java` | ✅ HOÀN THÀNH | Forward copy đúng TTL/hop/route/checksum; explicit reverse route cho dispatch/ACK |
| `ui/controller/NodeClientController.java` | ✅ HOÀN THÀNH | Hiển thị trạng thái queue/forward/dispatch và lỗi bind port có thể xử lý |
| `docs/demo/desktop-topology.md` | ✅ HOÀN THÀNH | Topology/lệnh chạy một laptop và ba laptop chung Wi-Fi/hotspot, firewall và troubleshooting |

---

## MODULE 7 — Desktop Reliability, Observability & Failure Handling (Phase 5)

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `network/ConnectionStateController.java` | ✅ ĐÃ TRIỂN KHAI | State machine `DISCONNECTED/CONNECTING/CONNECTED/RETRY_WAIT/FAILED/STOPPED`, bounded backoff, injected scheduler/sender và cancellation. |
| `network/ReconnectablePacketSender.java` | ✅ ĐÃ TRIỂN KHAI | Một retry sequence bounded cho peer, daemon scheduler, shutdown cancel và UI state callback. |
| `network/SocketClient.java` | ✅ ĐÃ TRIỂN KHAI | Shared bounded daemon transport queue; TCP `SENT` được phân biệt với ACK end-to-end. |
| `demo/DemoProfile.java`, `demo/DemoPreflight.java` | ✅ ĐÃ TRIỂN KHAI | Parse profile/versioned safe preflight checks without changing OS state. |
| `config/demo-profile.properties`, `scripts/preflight-demo.ps1` | ✅ ĐÃ TRIỂN KHAI | Template LAN không secret và preflight JDK/JAR/port/map/host. |
| `docs/demo/troubleshooting.md` | ✅ ĐÃ TRIỂN KHAI | Recovery guide, database/outbox persistence, firewall and startup order. |

---

## LOG KIỂM THỬ VÀ XÁC MINH

| Bộ kiểm thử | Số test | Kết quả | Ghi chú |
|-------------|---------|---------|---------|
| `ChecksumTamperTest` | 21 | PASS | Phase 1 Checksum tamper & canonical verification |
| `CoreMeshSystemTest` | 9 | PASS | Phase 1 End-to-end multi-hop routing tests |
| `MeshPacketValidationTest` | 52 | PASS | Phase 1 Boundary and structural validation tests |
| `ProtocolFixtureContractTest` | 9 | PASS | Phase 1 Known-answer deterministic fixture tests |
| `DatabaseMigrationTest` | 6 | PASS | Phase 2.1 SQLite schema, migrations, inMemory shared cache, foreign keys |
| `RepositoryTest` | 9 | PASS | Phase 2.2 Storage CRUD, idempotency, DB restart |
| `SocketServerIntegrationTest` | 13 | PASS | Hardened TCP Gateway, dynamic bind, malformed/duplicate/TTL packet handling, overload socket closure, >64KB frame limit |
| `DispatchOutboxTest` | 13 | PASS | Race-safe monotonic ACKED, synchronous ACK preserve, exponential backoff |
| `EndToEndDispatchAckIntegrationTest` | 1 | PASS | Phase 4 real bidirectional socket flow: Victim -> Relay -> Base -> Dispatch -> Relay -> Victim -> ACK -> Relay -> Base -> SQLite ACKED |
| `NodeConfigTest` | 6 | PASS | Phase 4 local/LAN topology parsing and validation of host, ports, duplicate options and map path |
| `RoutingEngineForwardingTest` | 6 | PASS | Phase 4 exact forwarding mutation, explicit reverse route, checksum/TTL/duplicate/next-hop failures |
| `BaseStationStorageFailureTest` | 4 | PASS | Phase 2 SQLite init failure halts services, write failure prevents ACK, shutdown race safe & thread-confinement UiDispatcher |
| `MBTilesReaderTest` | 8 | PASS | Phase 3.3 MBTiles open, metadata, tile query, PNG signature, non-existent, LRU cache eviction, shutdown |
| `LocalTileServerTest` | 8 | PASS | Phase 3.3 Loopback 127.0.0.1 binding, dynamic port, 200 tile HTTP, 404 transparent PNG, traversal rejection, out-of-bounds rejection, 405 method rejection, graceful shutdown |
| `OfflineMapVerificationTest` | 9 | PASS | Phase 3.5 Zero external network URLs, local fixture loading, missing/corrupt map error, marker queue before readiness, safe JSON serialization, coordinate bounds, loopback isolation, clean shutdown |
| `MapBridgeTest` | 3 | PASS | Phase 3.4 Queue/replay exactly once, marker focus theo packet ID, route chỉ vẽ với relay coordinates đã resolve |
| `ConnectionStateControllerTest` | 3 | PASS | Phase 5 deterministic bounded backoff, final failure and shutdown cancellation |
| `DemoPreflightTest` | 2 | PASS | Phase 5 profile parsing plus JDK/JAR/port/map/LAN preflight failures |
| `BaseStationDuplicateAckTest` | 1 | PASS | Duplicate SOS không tạo lại sự kiện UI nhưng Base Station gửi lại ACK sau khi kiểm tra SQLite idempotent thành công |
| `BaseStationNonSosArrivalTest` | 1 | PASS | HEARTBEAT được lưu để audit nhưng không tạo SOS, không tăng thống kê và không gửi ACK cứu hộ |
| `BaseStationSeverityStyleTest` | 2 | PASS | Ánh xạ CRITICAL/HIGH/MEDIUM sang CSS triage trên toàn bộ dòng TableView |
| **TỔNG CỘNG** | **186** | **186/186 PASS (100%)** | **0 failures, 0 errors, 0 skipped** |
