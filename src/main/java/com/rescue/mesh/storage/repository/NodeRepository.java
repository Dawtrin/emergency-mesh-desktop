package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.model.NodeRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface NodeRepository {
    void upsert(NodeRecord node);
    void upsert(Connection conn, NodeRecord node) throws SQLException;
    Optional<NodeRecord> findById(String nodeId);
    List<NodeRecord> findAll();
    int count();
}
