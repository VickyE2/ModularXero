package com.vicky.modularxero.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ClientCtx(
    val ip: String,
    var lastSeen: Long = System.currentTimeMillis()
)

class ConnectionTracker(private val maxPerIp: Int) {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun tryAcquire(ip: String): Boolean {
        val cnt = counts.computeIfAbsent(ip) { AtomicInteger(0) }
        val after = cnt.incrementAndGet()

        if (after > maxPerIp) {
            cnt.decrementAndGet()
            return false
        }
        return true
    }

    fun release(ip: String) {
        val cnt = counts[ip] ?: return
        if (cnt.decrementAndGet() <= 0) {
            counts.remove(ip, cnt)
        }
    }
}
