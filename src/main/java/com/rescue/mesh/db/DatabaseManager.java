package com.rescue.mesh.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý kết nối cơ sở dữ liệu SQLite nhúng cho Trạm Chỉ Huy C2.
 *
 * Cơ sở lý thuyết:
 *   - SQLite: CSDL nhúng nhẹ, zero-config, phù hợp cho ứng dụng dã chiến
 *     không phụ thuộc server ngoài (self-contained).
 *   - HikariCP: Connection Pool hiệu suất cao nhất Java, giảm overhead
 *     tạo/đóng connection liên tục khi nhiều thread cùng truy cập DB.
 *
 * Design Pattern: Singleton + Lazy Initialization
 *   - Đảm bảo chỉ có 1 DataSource duy nhất trong toàn bộ ứng dụng
 *   - Thread-safe nhờ volatile + double-checked locking
 *
 * Schema gồm 5 bảng:
 *   1. victims — Hồ sơ nạn nhân + trạng thái cứu nạn (Đề 54)
 *   2. mesh_packets — Nhật ký toàn bộ gói tin (Evidence Log)
 *   3. dispatch_logs — Lịch sử lệnh điều phối + ACK tracking
 *   4. relay_nodes — Danh sách trạm tiếp sóng (Topology)
 *   5. network_metrics — Số liệu băng thông theo chu kỳ (Đề 53)
 */
public class DatabaseManager {

    /** Đường dẫn file database mặc định */
    private static final String DEFAULT_DB_PATH = "rescue_c2.db";

    /** Singleton instance */
    private static volatile DatabaseManager instance;

    /** HikariCP DataSource — Connection Pool */
    private final HikariDataSource dataSource;

    /**
     * Hàng đợi ghi tuần tự (Write Queue) — giải quyết vấn đề SQLITE_BUSY.
     *
     * SQLite chỉ cho phép 1 writer tại 1 thời điểm. Khi bão gói tin (packet storm),
     * nhiều TCP handler cùng ghi DB sẽ gây xung đột. ExecutorService single-thread
     * serialize toàn bộ write operations vào 1 luồng duy nhất.
     *
     * Tham khảo: FINAL spec §5.2 — "Hàng đợi ghi tuần tự"
     */
    private final ExecutorService writeExecutor;

    // =========================================================
    // CONSTRUCTOR & SINGLETON
    // =========================================================

    private DatabaseManager(String dbPath) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite tối ưu: chỉ cần 1-2 connection vì SQLite serialize writes
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);

        // FIX BUG-3: SQLite PRAGMA phải dùng ConnectionInitSql, KHÔNG phải addDataSourceProperty
        // Mỗi connection mới từ pool sẽ tự động chạy các PRAGMA này
        config.setConnectionInitSql(
                "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;"
        );

        config.setPoolName("RescueC2-SQLite-Pool");

        this.dataSource = new HikariDataSource(config);

        // Khởi tạo write queue (single-thread serialized writes)
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqlite-write-queue");
            t.setDaemon(true);
            return t;
        });

        // Khởi tạo schema ngay khi tạo instance
        initSchema();

        System.out.println("[DB] DatabaseManager khởi tạo thành công — " + dbPath);
    }

    /**
     * Lấy singleton instance. Tạo mới nếu chưa tồn tại.
     * Thread-safe nhờ double-checked locking.
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager(DEFAULT_DB_PATH);
                }
            }
        }
        return instance;
    }

    /**
     * Khởi tạo với đường dẫn DB tùy chỉnh (dùng cho testing).
     */
    public static synchronized DatabaseManager initialize(String dbPath) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new DatabaseManager(dbPath);
        return instance;
    }

    // =========================================================
    // CONNECTION
    // =========================================================

    /**
     * Lấy connection từ pool. Caller phải đóng connection sau khi dùng
     * (dùng try-with-resources).
     *
     * @return Connection sẵn sàng sử dụng
     * @throws SQLException nếu không lấy được connection
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private UserDao userDao;
    private IncidentEventDao incidentEventDao;

    public synchronized UserDao getUserDao() {
        if (userDao == null) {
            userDao = new UserDao(this);
        }
        return userDao;
    }

    public synchronized IncidentEventDao getIncidentEventDao() {
        if (incidentEventDao == null) {
            incidentEventDao = new IncidentEventDao(this);
        }
        return incidentEventDao;
    }

    // =========================================================
    // WRITE QUEUE (§5.2 — Serialize SQLite writes)
    // =========================================================

    /**
     * Thực thi thao tác ghi bất đồng bộ qua write queue.
     * Fire-and-forget — dùng cho insert packet, update heartbeat.
     *
     * @param writeTask Runnable chứa logic ghi DB
     */
    public void executeWrite(Runnable writeTask) {
        if (!writeExecutor.isShutdown()) {
            writeExecutor.execute(writeTask);
        }
    }

    /**
     * Submit thao tác ghi đồng bộ qua write queue.
     * Trả về Future để caller có thể chờ kết quả (dùng cho dispatch cần response).
     *
     * @param writeTask Callable chứa logic ghi DB + trả kết quả
     * @return Future<T> kết quả ghi
     */
    public <T> Future<T> submitWrite(Callable<T> writeTask) {
        return writeExecutor.submit(writeTask);
    }

    // =========================================================
    // SCHEMA INITIALIZATION
    // =========================================================

    /**
     * Tự động tạo 5 bảng nếu chưa tồn tại.
     * Chạy 1 lần khi khởi động ứng dụng.
     */
    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Bật WAL mode cho SQLite
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");

            // 1. Bảng lưu trữ hồ sơ Nạn nhân (Đề 54)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS victims (
                    node_id TEXT PRIMARY KEY,
                    full_name TEXT NOT NULL,
                    phone_number TEXT,
                    blood_type TEXT DEFAULT 'O+',
                    medical_notes TEXT,
                    victim_count INTEGER DEFAULT 1,
                    alert_type TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    dispatched_team TEXT,
                    has_fix INTEGER DEFAULT 1,
                    accuracy_m INTEGER,
                    fix_age_ms INTEGER,
                    first_seen_timestamp INTEGER NOT NULL,
                    last_updated_timestamp INTEGER NOT NULL
                )
            """);

            // Migration an toàn cho column dispatched_team nếu database cũ đã tồn tại
            try {
                stmt.execute("ALTER TABLE victims ADD COLUMN dispatched_team TEXT");
            } catch (SQLException ignored) {
                // Column đã tồn tại
            }
            try {
                stmt.execute("ALTER TABLE victims ADD COLUMN has_fix INTEGER DEFAULT 1");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE victims ADD COLUMN accuracy_m INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE victims ADD COLUMN fix_age_ms INTEGER");
            } catch (SQLException ignored) {}

            // 2. Bảng nhật ký toàn bộ Gói tin Mesh (Evidence Log)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mesh_packets (
                    packet_id TEXT PRIMARY KEY,
                    packet_type TEXT NOT NULL,
                    source_node_id TEXT NOT NULL,
                    destination_node_id TEXT NOT NULL,
                    sender_hop_id TEXT NOT NULL,
                    ttl INTEGER NOT NULL,
                    hop_count INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    route_history TEXT NOT NULL,
                    checksum TEXT NOT NULL,
                    payload_json TEXT
                )
            """);

            // 3. Bảng lịch sử Lệnh Điều phối từ Trạm trưởng
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dispatch_logs (
                    dispatch_id TEXT PRIMARY KEY,
                    target_node_id TEXT NOT NULL,
                    command_message TEXT NOT NULL,
                    sender_name TEXT NOT NULL,
                    dispatched_at INTEGER NOT NULL,
                    ack_received INTEGER DEFAULT 0,
                    ack_timestamp INTEGER
                )
            """);

            // 4. Bảng nhật ký chuyển tiếp Liên Ngành (115 - 114 - 113)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS interagency_dispatches (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    victim_node_id TEXT NOT NULL,
                    agency_type TEXT NOT NULL,
                    agency_name TEXT NOT NULL,
                    payload_sent TEXT NOT NULL,
                    dispatched_at INTEGER NOT NULL,
                    status TEXT DEFAULT 'SENT'
                )
            """);

            // 5. Bảng danh sách cơ sở Cấp cứu & An ninh dã chiến (Hạ tầng GIS)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS emergency_facilities (
                    facility_id TEXT PRIMARY KEY,
                    facility_name TEXT NOT NULL,
                    facility_type TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    contact_phone TEXT,
                    available_capacity INTEGER DEFAULT 10,
                    address TEXT,
                    district TEXT,
                    image_url TEXT
                )
            """);

            // Migrations an toàn cho emergency_facilities
            String[] facCols = {
                "ALTER TABLE emergency_facilities ADD COLUMN address TEXT",
                "ALTER TABLE emergency_facilities ADD COLUMN district TEXT",
                "ALTER TABLE emergency_facilities ADD COLUMN image_url TEXT"
            };
            for (String col : facCols) {
                try { stmt.execute(col); } catch (SQLException ignored) {}
            }

            // 6. Bảng danh sách các Trạm Tiếp sóng (Relay Nodes Topology)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS relay_nodes (
                    node_id TEXT PRIMARY KEY,
                    node_name TEXT,
                    ip_address TEXT,
                    port INTEGER,
                    last_heartbeat_timestamp INTEGER NOT NULL,
                    packets_forwarded_count INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'ONLINE'
                )
            """);

            // 7. Bảng giám sát tải mạng & lưu lượng định kỳ (Đề 53)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS network_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    packets_received INTEGER DEFAULT 0,
                    packets_forwarded INTEGER DEFAULT 0,
                    packets_dropped INTEGER DEFAULT 0,
                    duplicate_drops INTEGER DEFAULT 0,
                    active_relay_count INTEGER DEFAULT 0
                )
            """);

            // 8. Bảng quản lý Đội Cứu Hộ (Rescue Team Tracking — Phase 3)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rescue_teams (
                    team_id TEXT PRIMARY KEY,
                    team_name TEXT NOT NULL,
                    team_type TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    status TEXT DEFAULT 'STANDBY',
                    assigned_victim_id TEXT,
                    member_count INTEGER DEFAULT 4,
                    vehicle_info TEXT,
                    contact_phone TEXT,
                    last_update_timestamp INTEGER NOT NULL
                )
            """);

            // 9. Bảng quản lý Cán bộ & Người dùng Hệ thống C2 (RBAC Security)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    display_name TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    failed_logins INTEGER NOT NULL DEFAULT 0,
                    locked_until INTEGER,
                    must_change_password INTEGER NOT NULL DEFAULT 0,
                    last_login_at INTEGER,
                    created_at INTEGER NOT NULL
                )
            """);

            // 10. Bảng sự kiện cứu nạn (Append-Only Incident Events & Timeline Dossier)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS incident_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    victim_node_id TEXT NOT NULL,
                    episode INTEGER NOT NULL DEFAULT 1,
                    event_type TEXT NOT NULL,
                    actor TEXT,
                    detail TEXT,
                    lat REAL,
                    lng REAL,
                    is_drill INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """);

            // Migration thêm is_drill cho các bảng tác chiến
            String[] drillMigrations = {
                "ALTER TABLE victims ADD COLUMN is_drill INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE victims ADD COLUMN episode INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE dispatch_logs ADD COLUMN is_drill INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE interagency_dispatches ADD COLUMN is_drill INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE mesh_packets ADD COLUMN is_drill INTEGER NOT NULL DEFAULT 0"
            };
            for (String mig : drillMigrations) {
                try { stmt.execute(mig); } catch (SQLException ignored) {}
            }

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_victim ON incident_events(victim_node_id, episode, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)");

            // Indexes cho truy vấn siêu tốc
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_victims_status ON victims(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_packets_source ON mesh_packets(source_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_packets_timestamp ON mesh_packets(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_metrics_timestamp ON network_metrics(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_dispatch_target ON dispatch_logs(target_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_interagency_victim ON interagency_dispatches(victim_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_facilities_type ON emergency_facilities(facility_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rescue_teams_status ON rescue_teams(status)");

            // Nạp dữ liệu 25 cơ sở dã chiến thực tế tại TP. Đà Nẵng (10 Viện, 7 PCCC, 8 Công an)
            stmt.execute("""
                INSERT OR REPLACE INTO emergency_facilities (facility_id, facility_name, facility_type, latitude, longitude, contact_phone, available_capacity, address, district, image_url)
                VALUES 
                -- 10 Bệnh viện & Cấp cứu 115
                ('HOSP_01', 'Bệnh viện Đà Nẵng', 'HOSPITAL', 16.0747, 108.2140, '0236.3821.118', 45, '124 Hải Phòng, Thạch Thang', 'Hải Châu', '/images/facilities/hosp_danang.jpg'),
                ('HOSP_02', 'Bệnh viện C Đà Nẵng', 'HOSPITAL', 16.0739, 108.2135, '0236.3821.480', 30, '122 Hải Phòng, Thạch Thang', 'Hải Châu', '/images/facilities/hosp_c.jpg'),
                ('HOSP_03', 'Bệnh viện Phụ sản - Nhi Đà Nẵng', 'HOSPITAL', 16.0245, 108.2492, '0236.3957.777', 25, '402 Lê Văn Hiến, Khuê Mỹ', 'Ngũ Hành Sơn', '/images/facilities/hosp_phusan_nhi.jpg'),
                ('HOSP_04', 'Bệnh viện ĐKQT Vinmec Đà Nẵng', 'HOSPITAL', 16.0354, 108.2166, '0236.3611.611', 20, 'Đường 30/4, Khuê Trung', 'Hải Châu', '/images/facilities/hosp_vinmec.jpg'),
                ('HOSP_05', 'Bệnh viện Quân y 17 (QK5)', 'HOSPITAL', 16.0597, 108.2045, '0236.3822.420', 20, '02 Nguyễn Tri Phương, Chính Gián', 'Thanh Khê', '/images/facilities/hosp_quany17.jpg'),
                ('HOSP_06', 'Bệnh viện Đa khoa Hoàn Mỹ Đà Nẵng', 'HOSPITAL', 16.0592, 108.2091, '0236.3650.676', 18, '291 Nguyễn Văn Linh, Thạc Gián', 'Thanh Khê', '/images/facilities/hosp_hoanmy.jpg'),
                ('HOSP_07', 'Bệnh viện Ung Bướu Đà Nẵng', 'HOSPITAL', 16.0768, 108.1633, '0236.3717.717', 35, 'Hoàng Thị Loan, Hòa Minh', 'Liên Chiểu', '/images/facilities/hosp_ungbuou.jpg'),
                ('HOSP_08', 'Bệnh viện Giao thông Vận tải Đà Nẵng', 'HOSPITAL', 16.0618, 108.1685, '0236.3764.717', 15, '68 Hoàng Văn Thái, Hòa Minh', 'Liên Chiểu', '/images/facilities/hosp_gtvt.jpg'),
                ('HOSP_09', 'Bệnh viện Phục hồi Chức năng Đà Nẵng', 'HOSPITAL', 16.0467, 108.2471, '0236.3952.127', 20, 'Võ Nguyên Giáp, Mỹ An', 'Ngũ Hành Sơn', '/images/facilities/hosp_phuchoichucnang.jpg'),
                ('HOSP_10', 'Trung tâm Cấp cứu 115 TP Đà Nẵng', 'HOSPITAL', 16.0272, 108.2128, '0236.3825.115', 50, 'Xuân Thủy, Khuê Trung', 'Cẩm Lệ', '/images/facilities/hosp_tt115.jpg'),

                -- 7 Phòng & Đội Cảnh sát PCCC & CNCH 114
                ('FIRE_01', 'Phòng CS PCCC & CNCH CATP Đà Nẵng', 'FIRE_STATION', 16.0381, 108.2120, '114', 15, '183 Phan Đăng Lưu, Khuê Trung', 'Cẩm Lệ', '/images/facilities/fire_catp_hq.jpg'),
                ('FIRE_02', 'Đội Ca-nô PCCC & CNCH Sông Hàn', 'FIRE_STATION', 16.0715, 108.2255, '0236.3910.114', 10, 'Bến Du thuyền Sông Hàn / 38 Bạch Đằng', 'Hải Châu', '/images/facilities/fire_songhan_boat.jpg'),
                ('FIRE_03', 'Đội PCCC & CNCH Sơn Trà - Ngũ Hành Sơn', 'FIRE_STATION', 16.0089, 108.2567, '114', 8, '02 Minh Mạng, Khuê Mỹ', 'Ngũ Hành Sơn', '/images/facilities/fire_sontra.jpg'),
                ('FIRE_04', 'Đội PCCC & CNCH Khu vực Liên Chiểu', 'FIRE_STATION', 16.0722, 108.1489, '114', 12, 'KCN Hòa Khánh, Đường số 2', 'Liên Chiểu', '/images/facilities/fire_lienchieu.jpg'),
                ('FIRE_05', 'Đội PCCC & CNCH Hải Châu - Thanh Khê', 'FIRE_STATION', 16.0531, 108.2095, '114', 10, 'Đường Duy Tân, Hòa Cường Bắc', 'Hải Châu', '/images/facilities/fire_haichau.jpg'),
                ('FIRE_06', 'Đội PCCC & CNCH Huyện Hòa Vang', 'FIRE_STATION', 16.0025, 108.1510, '114', 10, 'Quốc lộ 14B, Hòa Nhơn', 'Hòa Vang', '/images/facilities/fire_hoavang.jpg'),
                ('FIRE_07', 'Đội PCCC & CNCH Quận Cẩm Lệ', 'FIRE_STATION', 16.0255, 108.2085, '114', 10, '298 Cách Mạng Tháng 8, Khuê Trung', 'Cẩm Lệ', '/images/facilities/fire_camle.jpg'),

                -- 8 Trụ sở & Chỉ huy Cảnh sát 113
                ('POLICE_01', 'Trung tâm Thông tin Chỉ huy 113 CATP', 'POLICE_POST', 16.0772, 108.2215, '113', 25, '47 Lý Tự Trọng, Thạch Thang', 'Hải Châu', '/images/facilities/police_113_hq.jpg'),
                ('POLICE_02', 'Công an Quận Hải Châu', 'POLICE_POST', 16.0699, 108.2230, '0236.3822.253', 15, '16 Phan Đình Phùng, Hải Châu 1', 'Hải Châu', '/images/facilities/police_haichau.jpg'),
                ('POLICE_03', 'Công an Quận Sơn Trà', 'POLICE_POST', 16.0664, 108.2361, '0236.3844.527', 15, '01 Đông Du, An Hải Bắc', 'Sơn Trà', '/images/facilities/police_sontra.jpg'),
                ('POLICE_04', 'Công an Quận Thanh Khê', 'POLICE_POST', 16.0611, 108.1812, '0236.3846.115', 15, '324 Hà Huy Tập, An Khê', 'Thanh Khê', '/images/facilities/police_thanhkhe.jpg'),
                ('POLICE_05', 'Công an Quận Ngũ Hành Sơn', 'POLICE_POST', 16.0210, 108.2520, '0236.3847.234', 12, '492 Lê Văn Hiến, Khuê Mỹ', 'Ngũ Hành Sơn', '/images/facilities/police_nguhanhson.jpg'),
                ('POLICE_06', 'Công an Quận Liên Chiểu', 'POLICE_POST', 16.0635, 108.1555, '0236.3841.442', 15, '42 Ngô Thì Nhậm, Hòa Khánh Nam', 'Liên Chiểu', '/images/facilities/police_lienchieu.jpg'),
                ('POLICE_07', 'Công an Quận Cẩm Lệ', 'POLICE_POST', 16.0258, 108.2081, '0236.3673.340', 12, '298 Cách Mạng Tháng 8, Khuê Trung', 'Cẩm Lệ', '/images/facilities/police_camle.jpg'),
                ('POLICE_08', 'Công an Huyện Hòa Vang', 'POLICE_POST', 15.9960, 108.1475, '0236.3846.224', 12, 'Quốc lộ 14B, Hòa Phong', 'Hòa Vang', '/images/facilities/police_hoavang.jpg')
            """);

            // Migrations tự động cho bảng interagency_dispatches
            String[] cols = {
                "ALTER TABLE interagency_dispatches ADD COLUMN responder_unit TEXT",
                "ALTER TABLE interagency_dispatches ADD COLUMN responder_leader TEXT",
                "ALTER TABLE interagency_dispatches ADD COLUMN contact_phone TEXT",
                "ALTER TABLE interagency_dispatches ADD COLUMN eta_minutes INTEGER",
                "ALTER TABLE interagency_dispatches ADD COLUMN special_needs TEXT",
                "ALTER TABLE interagency_dispatches ADD COLUMN updated_at INTEGER"
            };
            for (String colSql : cols) {
                try { stmt.execute(colSql); } catch (SQLException ignored) {}
            }

            // 8. Bảng nhật ký Đàm thoại Bộ đàm dã chiến VHF CH16
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tactical_radio_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    channel TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    recipient TEXT NOT NULL,
                    message TEXT NOT NULL,
                    urgency TEXT DEFAULT 'ROUTINE'
                )
            """);

            // Nạp dữ liệu 8 đội cứu hộ mẫu tại TP. Đà Nẵng (Phase 3: Rescue Team Tracking)
            stmt.execute("""
                INSERT OR IGNORE INTO rescue_teams (team_id, team_name, team_type, latitude, longitude, status, assigned_victim_id, member_count, vehicle_info, contact_phone, last_update_timestamp)
                VALUES
                ('TEAM_SAR_01',  'Đội CNCH Sông Hàn',          'SAR',     16.0715, 108.2255, 'STANDBY', NULL, 6, 'Ca-nô cứu hộ SR-01 + Jet Ski', '0236.3910.114', 0),
                ('TEAM_SAR_02',  'Đội CNCH Hòa Vang',          'SAR',     16.0042, 108.1528, 'STANDBY', NULL, 5, 'Xe bán tải Ford Ranger + Xuồng cao su', '0905.234.777', 0),
                ('TEAM_MED_01',  'Xe Cấp cứu 115 Số 1',        'MEDICAL', 16.0272, 108.2128, 'STANDBY', NULL, 4, 'Xe cứu thương Mercedes Sprinter', '115', 0),
                ('TEAM_MED_02',  'Xe Cấp cứu 115 Số 2',        'MEDICAL', 16.0747, 108.2140, 'STANDBY', NULL, 4, 'Xe cứu thương Toyota HiAce', '115', 0),
                ('TEAM_FIRE_01', 'Xe Chữa cháy PCCC Số 1',     'FIRE',    16.0381, 108.2120, 'STANDBY', NULL, 8, 'Xe chữa cháy Kamaz + thang 32m', '114', 0),
                ('TEAM_FIRE_02', 'Xe CNCH Đặc biệt PCCC',      'FIRE',    16.0420, 108.2187, 'STANDBY', NULL, 6, 'Xe cứu nạn đặc biệt + kích thủy lực', '114', 0),
                ('TEAM_POL_01',  'Tổ Tuần tra CA Hải Châu',     'POLICE',  16.0680, 108.2168, 'STANDBY', NULL, 4, 'Mô tô tuần tra + Loa phát thanh', '113', 0),
                ('TEAM_POL_02',  'Tổ CSGT Cầu Sông Hàn',       'POLICE',  16.0800, 108.2255, 'STANDBY', NULL, 3, 'Xe ô tô tuần tra + Rào chắn', '113', 0)
            """);

            System.out.println("[DB] Schema 9 bảng, 25 cơ sở dã chiến và 8 đội cứu hộ Đà Nẵng đã khởi tạo thành công.");

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi khởi tạo schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Xóa toàn bộ dữ liệu mẫu / dữ liệu tác chiến thử nghiệm
     * Giữ lại cấu trúc bảng và 15 cơ sở cấp cứu hạ tầng GIS Đà Nẵng.
     */
    public void clearAllOperationalData() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM victims;");
            stmt.execute("DELETE FROM mesh_packets;");
            stmt.execute("DELETE FROM dispatch_logs;");
            stmt.execute("DELETE FROM interagency_dispatches;");
            stmt.execute("DELETE FROM tactical_radio_logs;");
            stmt.execute("DELETE FROM network_metrics;");
            stmt.execute("DELETE FROM relay_nodes;");
            // Reset rescue teams về STANDBY
            stmt.execute("UPDATE rescue_teams SET status='STANDBY', assigned_victim_id=NULL, last_update_timestamp=0;");
            stmt.execute("""
                UPDATE emergency_facilities SET available_capacity = CASE facility_id
                    WHEN 'HOSP_01' THEN 45
                    WHEN 'HOSP_02' THEN 30
                    WHEN 'HOSP_03' THEN 25
                    WHEN 'HOSP_04' THEN 20
                    WHEN 'HOSP_05' THEN 20
                    WHEN 'HOSP_06' THEN 18
                    WHEN 'HOSP_07' THEN 35
                    WHEN 'HOSP_08' THEN 15
                    WHEN 'HOSP_09' THEN 20
                    WHEN 'HOSP_10' THEN 50
                    WHEN 'FIRE_01' THEN 15
                    WHEN 'FIRE_02' THEN 10
                    WHEN 'FIRE_03' THEN 8
                    WHEN 'FIRE_04' THEN 12
                    WHEN 'FIRE_05' THEN 10
                    WHEN 'FIRE_06' THEN 10
                    WHEN 'FIRE_07' THEN 10
                    WHEN 'POLICE_01' THEN 25
                    WHEN 'POLICE_02' THEN 15
                    WHEN 'POLICE_03' THEN 15
                    WHEN 'POLICE_04' THEN 15
                    WHEN 'POLICE_05' THEN 12
                    WHEN 'POLICE_06' THEN 15
                    WHEN 'POLICE_07' THEN 12
                    WHEN 'POLICE_08' THEN 12
                    ELSE 10
                END;
            """);
            stmt.execute("VACUUM;");
            System.out.println("[DB] Đã xóa sạch toàn bộ dữ liệu tác chiến mẫu.");
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi xóa dữ liệu: " + e.getMessage());
        }
    }

    // =========================================================
    // SHUTDOWN
    // =========================================================

    /**
     * Đóng DataSource và giải phóng tài nguyên.
     * Gọi khi ứng dụng thoát.
     */
    public void shutdown() {
        // Shutdown write queue trước, chờ tối đa 5 giây cho các write đang chờ
        if (writeExecutor != null && !writeExecutor.isShutdown()) {
            writeExecutor.shutdown();
            try {
                if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    writeExecutor.shutdownNow();
                    System.err.println("[DB] Write queue bị ép dừng sau 5s timeout.");
                }
            } catch (InterruptedException e) {
                writeExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("[DB] Write queue đã shutdown.");
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] DatabaseManager đã shutdown.");
        }
        instance = null;
    }

    /**
     * Kiểm tra DataSource còn hoạt động không.
     */
    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }
}
