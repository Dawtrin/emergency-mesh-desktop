package com.rescue.mesh.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_nodes")
data class NodeEntity(
    @PrimaryKey val nodeId: String,
    val host: String? = null,
    val port: Int? = null,
    val protocolVersion: String = "1.0",
    val lastSeenAt: Long = System.currentTimeMillis(),
    val connected: Boolean = false
)
