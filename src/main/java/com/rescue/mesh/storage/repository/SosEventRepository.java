package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.model.SosEventRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface SosEventRepository {
    void insert(SosEventRecord record);
    void insert(Connection conn, SosEventRecord record) throws SQLException;
    boolean existsById(String packetId);
    boolean existsById(Connection conn, String packetId) throws SQLException;
    Optional<SosEventRecord> findById(String packetId);
    List<SosEventRecord> findAllOrderByTimeAsc();
    List<SosEventRecord> findAllOrderByTimeDesc();
    void updateRescueStatus(String packetId, String status);
    int countTotal();
    int countBySeverity(String severity);
}
