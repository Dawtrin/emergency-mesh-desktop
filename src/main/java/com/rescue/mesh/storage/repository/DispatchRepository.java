package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.model.DispatchRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DispatchRepository {
    void insert(DispatchRecord record);
    void insert(Connection conn, DispatchRecord record) throws SQLException;
    Optional<DispatchRecord> findById(String packetId);
    void updateStatus(String packetId, String deliveryStatus);
    void updateRetry(String packetId, String deliveryStatus, int attemptCount, Long nextAttemptTime, long updatedAt);
    List<DispatchRecord> findPendingOrSentDue(long nowTimestamp);
    List<DispatchRecord> findAll();
    int count();
}
