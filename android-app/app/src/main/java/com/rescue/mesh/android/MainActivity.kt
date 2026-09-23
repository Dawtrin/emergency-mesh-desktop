package com.rescue.mesh.android

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rescue.mesh.android.location.FusedLocationProvider
import com.rescue.mesh.android.location.ManualLocationProvider
import com.rescue.mesh.android.service.MeshForegroundService
import com.rescue.mesh.android.ui.MeshScreen
import com.rescue.mesh.android.ui.MeshViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MeshViewModel by viewModels()
    private var binder: MeshForegroundService.LocalBinder? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasRequiredPermissions()) startMeshService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? MeshForegroundService.LocalBinder
            binder?.let { viewModel.attach(it, (application as MeshApplication).nodeId) }
        }

        override fun onServiceDisconnected(name: ComponentName?) { binder = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle()
            MeshScreen(
                state = state.value,
                onSubmitSos = { sender, type, message, count, severity, latitude, longitude, onSubmitted ->
                    val locationProvider = if (latitude != null && longitude != null) {
                        ManualLocationProvider(latitude, longitude)
                    } else FusedLocationProvider(this)
                    lifecycleScope.launch {
                        locationProvider.current().onSuccess { sample ->
                            viewModel.submitSos(sender, type, message, count, severity, sample.location, sample, onSubmitted)
                        }.onFailure { error ->
                            // The UI shows a clear error instead of silently sending a stale location.
                            viewModel.reportError("Không lấy được GPS: ${error.message}. Nhập tọa độ thủ công rồi thử lại.")
                            onSubmitted(false)
                        }
                    }
                },
                onRegisterPeer = viewModel::registerPeer,
                onConnectWifiPeer = viewModel::connectWifiPeer
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasRequiredPermissions()) startMeshService()
    }

    override fun onStop() {
        runCatching { unbindService(connection) }
        super.onStop()
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshForegroundService::class.java).setAction(MeshForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun requestPermissionsIfNeeded() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private fun hasRequiredPermissions(): Boolean =
        (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) &&
            (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
}
