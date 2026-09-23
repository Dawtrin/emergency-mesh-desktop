package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.model.PacketRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PacketRepository {
    void insert(PacketRecord packet);
    void insert(Connection conn, PacketRecord packet) throws SQLException;
    boolean existsById(String packetId);
    boolean existsById(Connection conn, String packetId) throws SQLException;
    Optional<PacketRecord> findById(String packetId);
    List<PacketRecord> findAllOrderByReceivedAtDesc(int limit);
    int count();
}
