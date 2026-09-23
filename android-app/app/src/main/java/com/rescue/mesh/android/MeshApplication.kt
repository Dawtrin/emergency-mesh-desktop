package com.rescue.mesh.android

import android.app.Application
import android.provider.Settings
import com.rescue.mesh.android.data.MeshDatabase
import com.rescue.mesh.android.data.PacketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MeshApplication : Application() {
    val nodeId: String by lazy {
        getSharedPreferences("mesh", MODE_PRIVATE).getString("node_id", null)
            ?.takeIf { it.isNotBlank() }
            ?: "ANDROID-${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "NODE"}"
    }
    val database: MeshDatabase by lazy { MeshDatabase.get(this) }
    val repository: PacketRepository by lazy { PacketRepository(database) }
    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    override fun onTerminate() {
        applicationScope.coroutineContext.cancel()
        super.onTerminate()
    }
}
