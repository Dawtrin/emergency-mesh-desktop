package com.rescue.mesh.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rescue.mesh.android.routing.DeliveryStatus

@Entity(tableName = "message_status")
data class MessageStatusEntity(
    @PrimaryKey val packetId: String,
    val status: String = DeliveryStatus.PENDING.name,
    val hopCount: Int = 0,
    val routeHistoryJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
    val detail: String? = null
)
