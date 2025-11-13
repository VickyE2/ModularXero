package com.vicky.modularxero

import com.fasterxml.jackson.databind.ObjectMapper
import com.vicky.modularxero.common.util.HibernateUtil
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.NetworkInterface

class ModularXero(
    private val dispatcher: ModularXeroDispatcher,
    port: Int
) : WebSocketServer(InetSocketAddress(port)) {
    private val mapper = ObjectMapper()

    override fun onOpen(conn: org.java_websocket.WebSocket, handshake: ClientHandshake?) {
       ModularXeroConsole.GLOBAL_READER.printAbove("[MoX-S] Client connected: ${conn?.remoteSocketAddress}")
    }

    override fun onClose(conn: org.java_websocket.WebSocket, code: Int, reason: String?, remote: Boolean) {
       ModularXeroConsole.GLOBAL_READER.printAbove("[MoX-S] Client disconnected: ${conn?.remoteSocketAddress}")
    }

    override fun onMessage(conn: org.java_websocket.WebSocket, message: String?) {
       ModularXeroConsole.GLOBAL_READER.printAbove("[MoX-S] Message: $message")

        if (message != null) {
            val response = dispatcher.dispatch(conn, message)
            conn.send(mapper.writeValueAsString(response))
        }
    }

    override fun onError(conn: org.java_websocket.WebSocket, ex: Exception?) {
       ModularXeroConsole.GLOBAL_READER.printAbove("[MoX-S] Error: ${ex?.message}")
    }

    override fun onStart() {
       println("[MoX-S] Server started!")
    }
}


object MetricsCollector {
    fun collectJvmMetrics(): Map<String, Any> {
        val memoryBean = ManagementFactory.getMemoryMXBean()
        val osBean = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean

        val heapUsed = memoryBean.heapMemoryUsage.used / 1024 / 1024
        val heapMax = memoryBean.heapMemoryUsage.max / 1024 / 1024
        val cpuLoad = osBean?.systemCpuLoad ?: -1.0
        val availableProcessors = osBean?.availableProcessors ?: -1

        return mapOf(
            "heapUsedMB" to heapUsed,
            "heapMaxMB" to heapMax,
            "cpuLoad" to cpuLoad,
            "availableProcessors" to availableProcessors
        )
    }

    fun collectNetworkMetrics(): Map<String, Any> {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        return interfaces.associate { it.displayName to mapOf(
            "isUp" to it.isUp,
            "mtu" to it.mtu
        )}
    }
}