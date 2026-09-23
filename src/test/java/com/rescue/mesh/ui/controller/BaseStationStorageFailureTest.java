package com.rescue.mesh.ui.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.DatabaseConfig;
import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Defect 4 & 5: SQLite Startup Failure and Background Lifecycle Tests")
class BaseStationStorageFailureTest {

    @Test
    @DisplayName("Khi SQLite init thất bại, dừng SocketServer/DispatchOutbox và tuyệt đối KHÔNG gửi ACK")
    void testStorageInitFailureHaltsNetworkServicesAndNeverAcksSos() throws Exception {
        BaseStationController controller = new BaseStationController();

        // Bắt các gói tin ACK được sinh ra (nếu có)
        List<MeshPacket> capturedAcks = Collections.synchronizedList(new ArrayList<>());
        controller.setAckSender((host, port, ack) -> capturedAcks.add(ack));

        // Cấu hình DatabaseManager với đường dẫn hoàn toàn bất khả thi để buộc khởi tạo thất bại
        // Ví dụ: trỏ tới đường dẫn file bên trong một file thông thường (không phải thư mục)
        File tempFile = File.createTempFile("fake_file", ".tmp");
        tempFile.deleteOnExit();
        String impossiblePath = tempFile.getAbsolutePath() + File.separator + "forbidden" + File.separator + "sub.db";
        DatabaseConfig badConfig = DatabaseConfig.forFile(new File(impossiblePath));
        controller.setDatabaseConfig(badConfig);

        NodeConfig nodeConfig = NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--port", "19090"});
        controller.initializeStation(nodeConfig);

        // Chờ tác vụ background trên backgroundStorageExecutor hoàn tất
        boolean initFinished = controller.awaitInitialization(5, TimeUnit.SECONDS);
        assertTrue(initFinished, "Background storage initialization phải hoàn thành trong 5s");

        // Kiểm tra trạng thái lưu trữ: thất bại
        assertFalse(controller.isStorageHealthy(), "Storage health phải là false khi SQLite init lỗi");
        assertNotNull(controller.getStartupErrorMessage(), "Phải có thông báo lỗi khởi tạo");

        // Yêu cầu: MUST NOT start SocketServer or DispatchOutboxService
        assertNull(controller.getSocketServer(), "SocketServer tuyệt đối KHÔNG được khởi động khi SQLite lỗi");
        assertNull(controller.getDispatchOutboxService(), "DispatchOutboxService tuyệt đối KHÔNG được khởi động khi SQLite lỗi");

        // Gửi gói tin SOS hợp lệ đến BaseStationController
        MeshPacket sosPacket = PacketFactory.createSosPacket(
                "VICTIM-01", "Pham Van A", "FLOOD", "Ngập lụt nghiêm trọng",
                2, MeshPacket.SEVERITY_CRITICAL, 20.95, 105.75
        );
        controller.onPacketArrived(sosPacket);

        // Yêu cầu: Không bao giờ được ACK gói tin khi lưu trữ gặp sự cố
        assertTrue(capturedAcks.isEmpty(), "Tuyệt đối không được gửi ACK cho gói tin SOS khi CSDL SQLite chưa sẵn sàng!");

        // Dọn dẹp sạch sẽ
        controller.shutdown();
    }

    @Test
    @DisplayName("Khi ghi CSDL thất bại trong lúc nhận packet, tuyệt đối KHÔNG sinh ACK")
    void testDatabaseWriteFailureDuringPacketArrivedNeverAcks(@TempDir Path tempDir) throws Exception {
        BaseStationController controller = new BaseStationController();

        List<MeshPacket> capturedAcks = Collections.synchronizedList(new ArrayList<>());
        controller.setAckSender((host, port, ack) -> capturedAcks.add(ack));

        // Khởi tạo DB thành công trước
        File dbFile = tempDir.resolve("test_healthy.db").toFile();
        DatabaseConfig dbConfig = DatabaseConfig.forFile(dbFile);
        controller.setDatabaseConfig(dbConfig);

        NodeConfig nodeConfig = NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--port", "19091"});
        controller.initializeStation(nodeConfig);

        assertTrue(controller.awaitInitialization(5, TimeUnit.SECONDS));
        assertTrue(controller.isStorageHealthy());

        // Đóng database manager bên dưới để làm hỏng mọi thao tác ghi tiếp theo
        BaseStationStorageService storage = controller.getStorageService();
        assertNotNull(storage);
        storage.getDatabaseManager().close();

        // Gửi gói tin SOS
        MeshPacket sosPacket = PacketFactory.createSosPacket(
                "VICTIM-02", "Tran Thi B", "MEDICAL", "Chấn thương nặng",
                1, MeshPacket.SEVERITY_HIGH, 21.05, 105.80
        );
        controller.onPacketArrived(sosPacket);

        // Ghi DB thất bại -> KHÔNG được gửi ACK
        assertTrue(capturedAcks.isEmpty(), "Khi thao tác ghi SQLite bị lỗi, tuyệt đối KHÔNG được gửi ACK!");

        controller.shutdown();
    }

    static class BlockingDatabaseManager extends DatabaseManager {
        final CountDownLatch initStartedLatch = new CountDownLatch(1);
        final CountDownLatch unblockInitLatch = new CountDownLatch(1);

        public BlockingDatabaseManager(DatabaseConfig config) {
            super(config);
        }

        @Override
        public synchronized void initialize() throws java.sql.SQLException, java.io.IOException {
            initStartedLatch.countDown();
            try {
                // Chờ cho tới khi shutdown() được gọi từ test
                boolean unblocked = unblockInitLatch.await(5, TimeUnit.SECONDS);
                if (!unblocked) {
                    throw new java.sql.SQLException("Timed out waiting for test unblock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.sql.SQLException("Interrupted during test delay", e);
            }
            super.initialize();
        }
    }

    @Test
    @DisplayName("Lifecycle Defect 1: Khi shutdown xảy ra trong lúc DB init đang block, hủy network/outbox và đóng DB")
    void testShutdownDuringDatabaseInitializationPreventsStartupAndClosesDatabase() throws Exception {
        BaseStationController controller = new BaseStationController();

        BlockingDatabaseManager delayedDbManager = new BlockingDatabaseManager(DatabaseConfig.inMemory());
        controller.setDatabaseManager(delayedDbManager);

        NodeConfig nodeConfig = NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--port", "19092"});

        // 1. initializeStation bắt đầu trên background executor
        controller.initializeStation(nodeConfig);

        // 2. Chờ cho đến khi initialize() bắt đầu và đang block
        assertTrue(delayedDbManager.initStartedLatch.await(5, TimeUnit.SECONDS),
                "Database initialization phải bắt đầu và đi vào trạng thái block");

        // 3. Khi shutdown bắt đầu (trạng thái chuyển sang SHUTTING_DOWN/STOPPED), unblock init latch
        Thread unblocker = new Thread(() -> {
            try {
                while (controller.getLifecycleState() == BaseStationController.LifecycleState.INITIALIZING
                        || controller.getLifecycleState() == BaseStationController.LifecycleState.NEW) {
                    Thread.sleep(10);
                }
                delayedDbManager.unblockInitLatch.countDown();
            } catch (InterruptedException ignored) {}
        });
        unblocker.start();

        // 4. Gọi shutdown() trong khi initialization đang bị block
        controller.shutdown();
        unblocker.join(2000);
        assertTrue(controller.isShuttingDownOrStopped(), "Lifecycle state phải là SHUTTING_DOWN hoặc STOPPED");

        // 5. Chờ toàn bộ background task kết thúc
        assertTrue(controller.awaitInitialization(5, TimeUnit.SECONDS),
                "Background initialization task phải hoàn tất");

        // 6. Assert: SocketServer và DispatchOutboxService tuyệt đối không được khởi động
        assertNull(controller.getSocketServer(), "SocketServer tuyệt đối KHÔNG được khởi động sau khi shutdown đã bắt đầu!");
        assertNull(controller.getDispatchOutboxService(), "DispatchOutboxService tuyệt đối KHÔNG được khởi động sau khi shutdown đã bắt đầu!");

        // 7. Assert: CSDL phải được đóng sạch sẽ
        assertTrue(delayedDbManager.isClosed(), "DatabaseManager phải được đóng khi shutdown!");

        // 8. Assert: Shutdown phải là idempotent
        controller.shutdown(); // Gọi lần 2
        assertEquals(BaseStationController.LifecycleState.STOPPED, controller.getLifecycleState());
        assertNull(controller.getSocketServer());
        assertNull(controller.getDispatchOutboxService());
    }

    @Test
    @DisplayName("Lifecycle Defect 2: UI updates từ backgroundStorageExecutor đều được dispatch qua UiDispatcher")
    void testUiUpdatesRoutedThroughUiDispatcher() throws Exception {
        BaseStationController controller = new BaseStationController();

        List<Runnable> dispatchedUiActions = Collections.synchronizedList(new ArrayList<>());
        controller.setUiDispatcher(action -> {
            dispatchedUiActions.add(action);
            action.run();
        });

        controller.setDatabaseConfig(DatabaseConfig.inMemory());
        NodeConfig nodeConfig = NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--port", "19093"});
        controller.initializeStation(nodeConfig);

        assertTrue(controller.awaitInitialization(5, TimeUnit.SECONDS));
        assertFalse(dispatchedUiActions.isEmpty(), "Mọi UI update từ background executor phải đi qua UiDispatcher seam!");

        controller.shutdown();
    }
}
