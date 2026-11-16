package com.vicky.modularxero

import com.fasterxml.jackson.databind.ObjectMapper
import com.vicky.modularxero.Startup.Companion.logHolder
import com.vicky.modularxero.Startup.Companion.main_logger
import com.vicky.modularxero.common.util.HibernateUtil
import com.vicky.modularxero.network.ClientCtx
import com.vicky.modularxero.network.ConnectionTracker
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

abstract class TrackableWebSocket(address: InetSocketAddress) : WebSocketServer(address) {
    protected val tracker = ConnectionTracker(10)
    protected val sel: Selector = Selector.open()

    override fun onConnect(key: SelectionKey): Boolean {
        if (key.isAcceptable) {
            val server = key.channel() as ServerSocketChannel
            val client = server.accept() ?: return false

            client.configureBlocking(false)

            val remote = client.remoteAddress as InetSocketAddress
            val ip = remote.address.hostAddress

            // 🔐 rate-limit decision
            if (!tracker.tryAcquire(ip)) {
                client.close()
                return false
            }

            // attach context
            val clientKey = client.register(sel, SelectionKey.OP_READ)
            clientKey.attach(ClientCtx(ip))
        }
        return false
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        tracker.release(conn.remoteSocketAddress.address.hostAddress)
    }

    fun closeClient(key: SelectionKey) {
        val ctx = key.attachment() as? ClientCtx
        if (ctx != null) {
            tracker.release(ctx.ip)
        }

        try {
            key.channel().close()
        } catch (_: Exception) {
        }

        key.cancel()
    }
}

class ModularXero(
    private val dispatcher: ModularXeroDispatcher,
    private val address: InetSocketAddress
) : TrackableWebSocket(address) {
    private val mapper = ObjectMapper()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
        main_logger.info("[MoX-S] Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        super.onClose(conn, code, reason, remote)
        main_logger.info("[MoX-S] Client disconnected: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String?) {
        main_logger.info("[MoX-S] Message: $message")

        if (message != null) {
            val response = dispatcher.dispatch(conn, message)
            conn.send(mapper.writeValueAsString(response))
        }
    }

    override fun onError(conn: WebSocket, ex: Exception?) {
        logHolder.error("[MoX-S] Error: ${ex?.message}")
    }

    override fun onStart() {
        logHolder.info("[MoX-S] Server started @ ${address.hostName + ":" + address.port}")
    }

    companion object {
        internal const val API: Int = 1
    }
}

data class JvmMetrics(
    val heapUsedMB: Long = 0,
    val heapMaxMB: Long = 0,
    val cpuLoad: Double = -1.0,
    val availableProcessors: Int = -1
)

data class NetworkInterfaceMetrics(
    val isUp: Boolean,
    val mtu: Int
)

data class NetworkMetrics(
    val interfaces: Map<String, NetworkInterfaceMetrics> = emptyMap()
)

object MetricsCollector {

    @Volatile
    var jvm: JvmMetrics = JvmMetrics()
        private set

    @Volatile
    var network: NetworkMetrics = NetworkMetrics()
        private set

    private var started = false

    private var updateHooks = mutableListOf<(JvmMetrics, NetworkMetrics) -> Unit>()
    val metricsScope = CoroutineScope(Dispatchers.IO + CoroutineName("metrics-collector"))

    fun start(intervalMs: Long = 1000L) {
        if (started)
            return

        started = true

        metricsScope.launch {
            // 2. isActive automatically checks if this coroutine is still running
            while (isActive) {
                try {
                    jvm = collectJvmMetrics()
                    network = collectNetworkMetrics()

                    updateHooks.forEach { it(jvm, network) }

                    // 3. delay() pauses the loop without blocking the actual thread
                    delay(intervalMs.milliseconds)
                } catch (e: CancellationException) {
                    // 4. Coroutines throw this when stopped. Re-throw to exit gracefully.
                    throw e
                } catch (e: Exception) {
                    // Log other errors here so your loop doesn't crash permanently
                    println("Error collecting metrics: ${e.message}")
                }
            }
        }
    }

    fun addUpdateHook(hook: (JvmMetrics, NetworkMetrics) -> Unit) {
        updateHooks.add(hook)
    }

    private fun collectJvmMetrics(): JvmMetrics {
        val memoryBean =
            ManagementFactory.getMemoryMXBean()

        val osBean =
            ManagementFactory.getOperatingSystemMXBean()
                    as? com.sun.management.OperatingSystemMXBean

        return JvmMetrics(
            heapUsedMB =
                memoryBean.heapMemoryUsage.used / 1024 / 1024,

            heapMaxMB =
                memoryBean.heapMemoryUsage.max / 1024 / 1024,

            cpuLoad =
                osBean?.systemCpuLoad ?: -1.0,

            availableProcessors =
                osBean?.availableProcessors ?: -1
        )
    }

    private fun collectNetworkMetrics(): NetworkMetrics {
        val interfaces =
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .associate { networkInterface ->
                    networkInterface.displayName to
                            NetworkInterfaceMetrics(
                                isUp = networkInterface.isUp,
                                mtu = networkInterface.mtu
                            )
                }

        return NetworkMetrics(interfaces)
    }
}