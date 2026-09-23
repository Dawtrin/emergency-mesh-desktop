package com.rescue.mesh.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: NodeEntity)

    @Query("SELECT * FROM mesh_nodes ORDER BY nodeId")
    fun observeAll(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM mesh_nodes ORDER BY lastSeenAt DESC")
    suspend fun findAll(): List<NodeEntity>

    @Query("SELECT * FROM mesh_nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun find(nodeId: String): NodeEntity?
}
