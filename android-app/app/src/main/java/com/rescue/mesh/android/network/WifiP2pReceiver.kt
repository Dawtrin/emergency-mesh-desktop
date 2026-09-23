package com.rescue.mesh.android.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Thin receiver kept separate so lifecycle registration is testable. */
class WifiP2pReceiver(private val onIntent: (Intent) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) onIntent(intent)
    }
}
