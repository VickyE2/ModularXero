package com.vicky.modularxero

import com.fasterxml.jackson.databind.JsonNode
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.common.MapValueBuilder
import com.vicky.modularxero.common.Response
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.java_websocket.WebSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

data class DispatchEnvelope(val conn: WebSocket, val node: JsonNode)
data class MessageEnvelope(val msg: Message, val replyTo: CompletableDeferred<Message?>? = null)

class ModuleRunner(
    val module: AbstractModule,
    val name: String,
    val mailboxCapacity: Int = 1024,
    singleThreaded: Boolean = true,
    private val watchMsThreshold: Long = 100L // warn if handler takes > watchMsThreshold ms
) {
    // thread/dispatcher
    private var poolSize = 1
    private var executor = if (singleThreaded) {
        poolSize = 1
        Executors.newSingleThreadExecutor { r -> Thread(r, "module-$name-thread").apply { isDaemon = true } }
    } else {
        poolSize = Runtime.getRuntime().availableProcessors()
        Executors.newFixedThreadPool(poolSize) { r -> Thread(r, "module-$name-worker").apply { isDaemon = true } }
    }
    private var dispatcher = executor.asCoroutineDispatcher()
    private var job = SupervisorJob()
    private var scope: CoroutineScope = CoroutineScope(job + dispatcher)

    // mailboxes
    private var dispatchChannel = Channel<DispatchEnvelope>(capacity = mailboxCapacity)
    private var messageChannel = Channel<MessageEnvelope>(capacity = mailboxCapacity)

    // lifecycle
    private val running = AtomicBoolean(false)
    val isRunning get() = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return

        if (executor.isShutdown)
            executor =
                if (poolSize == 1)
                    Executors.newSingleThreadExecutor { r ->
                        Thread(r, "module-$name-thread").apply { isDaemon = true }
                    }
                else
                    Executors.newFixedThreadPool(poolSize) { r ->
                        Thread(r, "module-$name-worker").apply { isDaemon = true }
                    }

        dispatcher = executor.asCoroutineDispatcher()
        if (!job.isActive)
            job = SupervisorJob()
        if (!scope.isActive)
            scope = CoroutineScope(job + dispatcher)

        if (dispatchChannel.isClosedForSend)
        dispatchChannel = Channel(mailboxCapacity)
        messageChannel = Channel(mailboxCapacity)

        repeat(poolSize) {
            if (isRunning)
            scope.launch {
                for (env in dispatchChannel) {
                    val before = System.nanoTime()
                    try {
                        if (module is AbstractServerModule) {
                            module.handleDispatchedRequest(env.conn, env.node)
                        } else {
                            val resp = if (module.isPaused || module.hasBeenStopped) {
                                Response.error("Server module has been stopped or is paused...")
                            } else {
                                module.handleRequest(env.node)
                            }
                            val response = resp ?: Response.error("Unexpected Module Error occurred: ${module.name}")
                            response.id = env.node.get("id")?.asText() ?: "non-specified"
                            val json = ModularXeroDispatcher.MAPPER.writeValueAsString(response)
                            env.conn.send(json)
                        }
                    } catch (ex: Throwable) {
                        module.getLogger().print("Error in module ${module.name}: ${ex.message}", true)
                        ex.printStackTrace()
                    } finally {
                        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)
                        if (tookMs > watchMsThreshold) {
                            module.getLogger().print("Slow dispatch in $name: ${tookMs}ms", ContextLogger.LogType.WARNING)
                        }
                    }
                }
            }
        }
        repeat(poolSize) {
            if (isRunning)
            scope.launch {
                for (env in messageChannel) {
                    val before = System.nanoTime()
                    try {
                        if (module is TextUnderstander) {
                            val result = if (module.isPaused || module.hasBeenStopped) {
                                Message(
                                    module.name, env.msg.from, "error", MapValueBuilder()
                                        .put("reply", "UnReachedResolution")
                                        .put("reason", "Server module has been stopped or is paused")
                                        .build()
                                )
                            } else {
                                try {
                                    module.handle(env.msg)
                                } catch (ex: UnsupportedOperationException) {
                                    null
                                }
                            }
                            env.replyTo?.complete(result)
                        }
                    } catch (ex: Throwable) {
                        env.replyTo?.complete(null)
                        module.getLogger().print("Error handling internal message in $name: ${ex.message}", true)
                        ex.printStackTrace()
                    } finally {
                        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)
                        if (tookMs > watchMsThreshold) {
                            module.getLogger().print("Slow message handling in $name: ${tookMs}ms", ContextLogger.LogType.WARNING)
                        }
                    }
                }
            }
        }

        module.getLogger().print("ModuleRunner for $name started", ContextLogger.LogType.PENDING)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        job.cancel()
        dispatchChannel.close()
        messageChannel.close()
        executor.shutdownNow()
        module.getLogger().print("ModuleRunner for $name stopped", ContextLogger.LogType.PENDING)
    }

    /**
     * Enqueue a dispatch. Non-blocking hot-path: trySend() only. Returns false if full.
     */
    fun offerDispatch(env: DispatchEnvelope): Boolean =
        dispatchChannel.trySend(env).isSuccess

    /**
     * Enqueue an internal message. Non-blocking on offer; suspending send available if needed.
     */
    fun offerMessage(env: MessageEnvelope): Boolean =
        messageChannel.trySend(env).isSuccess

    /**
     * Suspend until delivered (for test harness or controlled uses)
     */
    suspend fun deliverMessageBlocking(env: MessageEnvelope, timeoutMs: Long? = null): Message? {
        return if (timeoutMs == null) {
            messageChannel.send(env)
            env.replyTo?.await()
        } else {
            withTimeoutOrNull(timeoutMs) {
                messageChannel.send(env)
                env.replyTo?.await()
            }
        }
    }
}
