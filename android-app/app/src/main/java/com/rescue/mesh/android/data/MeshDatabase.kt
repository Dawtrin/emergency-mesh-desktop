package com.rescue.mesh.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MeshPacketEntity::class, NodeEntity::class, MessageStatusEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun packetDao(): MeshPacketDao
    abstract fun nodeDao(): MeshNodeDao
    abstract fun messageStatusDao(): MessageStatusDao

    companion object {
        @Volatile private var instance: MeshDatabase? = null

        fun get(context: Context): MeshDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeshDatabase::class.java,
                "emergency_mesh.db"
            ).fallbackToDestructiveMigrationOnDowngrade().build().also { instance = it }
        }
    }
}
