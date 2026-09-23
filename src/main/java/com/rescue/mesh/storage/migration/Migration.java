package com.rescue.mesh.storage.migration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface đại diện cho một migration phiên bản trong SQLite.
 */
public interface Migration {

    /**
     * Số phiên bản migration (ví dụ: 1, 2, 3...).
     */
    int getVersion();

    /**
     * Mô tả ngắn gọn thay đổi schema.
     */
    String getDescription();

    /**
     * Áp dụng migration lên connection đang mở (được thực thi bên trong transaction).
     */
    void apply(Connection conn) throws SQLException;
}