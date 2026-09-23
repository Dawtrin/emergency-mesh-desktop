package com.rescue.mesh.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: MessageStatusEntity)

    @Query("SELECT * FROM message_status WHERE packetId = :packetId LIMIT 1")
    suspend fun find(packetId: String): MessageStatusEntity?

    @Query("SELECT * FROM message_status ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MessageStatusEntity>>

    @Query("DELETE FROM message_status WHERE status IN ('DELIVERED', 'EXPIRED') AND updatedAt < :before")
    suspend fun prune(before: Long): Int
}
