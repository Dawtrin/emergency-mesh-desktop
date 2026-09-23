package com.rescue.mesh.android.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenPacketLruCacheTest {
    @Test
    fun duplicateIsSuppressedAndCapacityIsBounded() {
        var now = 1_000L
        val cache = SeenPacketLruCache(maxEntries = 2, expiryMs = 100, nowMs = { now })
        assertFalse(cache.checkAndMark("a"))
        assertTrue(cache.checkAndMark("a"))
        assertFalse(cache.checkAndMark("b"))
        assertFalse(cache.checkAndMark("c"))
        assertEquals(2, cache.size())
        now += 101
        assertFalse(cache.checkAndMark("a"))
    }
}
