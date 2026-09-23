package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.model.RouteRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface RouteRepository {
    void insert(RouteRecord record);
    void insert(Connection conn, RouteRecord record) throws SQLException;
    List<RouteRecord> findByNodeId(String nodeId);
    List<RouteRecord> findAll();
}
