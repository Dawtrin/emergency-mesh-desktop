package com.rescue.mesh.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshPacketDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(packet: MeshPacketEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(packet: MeshPacketEntity)

    @Query("SELECT * FROM mesh_packets WHERE packetId = :packetId LIMIT 1")
    suspend fun find(packetId: String): MeshPacketEntity?

    // FAILED is terminal.  Keeping it out of this query is what makes the
    // Room outbox finite after the delivery policy gives up.
    @Query("SELECT * FROM mesh_packets WHERE status IN ('PENDING', 'SENT_WAITING_ACK') AND nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<MeshPacketEntity>

    @Query("SELECT * FROM mesh_packets WHERE sourceNodeId = :sourceNodeId ORDER BY createdAt DESC")
    fun observeForSource(sourceNodeId: String): Flow<List<MeshPacketEntity>>

    @Query("SELECT * FROM mesh_packets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MeshPacketEntity>>

    @Query("UPDATE mesh_packets SET status = :status, attempts = :attempts, nextAttemptAt = :nextAttemptAt, updatedAt = :updatedAt, lastError = :lastError WHERE packetId = :packetId")
    suspend fun updateStatus(packetId: String, status: String, attempts: Int, nextAttemptAt: Long, updatedAt: Long, lastError: String?)

    @Query("DELETE FROM mesh_packets WHERE status IN ('DELIVERED', 'EXPIRED') AND updatedAt < :before")
    suspend fun prune(before: Long): Int
}
