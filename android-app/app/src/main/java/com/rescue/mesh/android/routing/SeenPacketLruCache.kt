package com.rescue.mesh.android.routing

import java.util.LinkedHashMap

/** Thread-safe bounded duplicate cache with time-based expiry. */
class SeenPacketLruCache(
    private val maxEntries: Int = 2_048,
    private val expiryMs: Long = 15 * 60 * 1_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(expiryMs > 0) { "expiryMs must be positive" }
    }

    private val entries = object : LinkedHashMap<String, Long>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun checkAndMark(packetId: String): Boolean {
        require(packetId.isNotBlank()) { "packetId must not be blank" }
        purgeExpired()
        if (entries.containsKey(packetId)) return true
        entries[packetId] = nowMs()
        return false
    }

    @Synchronized fun remove(packetId: String) { entries.remove(packetId) }
    @Synchronized fun size(): Int = entries.size
    @Synchronized fun clear() = entries.clear()

    @Synchronized
    private fun purgeExpired() {
        val cutoff = nowMs() - expiryMs
        entries.entries.removeIf { it.value < cutoff }
    }
}
