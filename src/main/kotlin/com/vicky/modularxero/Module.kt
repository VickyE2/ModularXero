package com.vicky.modularxero

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong
import com.fasterxml.jackson.databind.JsonNode
import com.vicky.modularxero.DeafenCommand.Companion.isDeafened
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.common.MapValueBuilder
import com.vicky.modularxero.common.Response
import com.vicky.modularxero.common.values.KmpClassResolver
import com.vicky.modularxero.common.values.MapValue
import com.vicky.modularxero.common.values.StringValue
import org.hibernate.SessionFactory
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.java_websocket.server.WebSocketServer
import org.jetbrains.annotations.Nullable
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

const val DataFolderName: String = "modules-data"
val classCommunicator = ClassBasedCommunicator()

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModuleEntry(
    val moduleName: String,
    val authors: Array<String> = [],
    val mavenDeps: Array<String> = [],
    val embeddedLibs: Array<String> = [],
    val description: String = "",
    val min: Int,
    val max: Int = Int.MAX_VALUE
)

/**
 * These can set a `DEFAULT_LOGGER` to act as the base for the sandboxing logger
 */
abstract class AbstractModule : Module {

    private val DEFAULT_LOGGER: ContextLogger by lazy {
        ContextLogger(ContextLogger.ContextType.FEATURE, "${name.uppercase()}-MAIN_LOGGER",
            { isDeafened(name) }, true, name
        )
    }

    override fun init(dispatcher: ModularXeroDispatcher) {
        DEFAULT_LOGGER.saveToFile(shouldSaveLog)
        KmpClassResolver.registerExtraLoader(this::class.java.classLoader)
        super.init(dispatcher)
    }
    override fun getCommands(): Map<String, Class<Callable<*>>> = emptyMap()
    override val shouldSaveLog: Boolean
        get() = true

    override fun getLogger(): ContextLogger = DEFAULT_LOGGER

    private val workerDispatcher = Executors.newCachedThreadPool { r ->
        Thread(r, "module-worker-${name}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    protected val workerScope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val blockingExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "module-worker-${name}").apply { isDaemon = true }
    }

    /**
     * Run a suspending block on a worker thread and block the caller until result or timeout.
     * Returns null on timeout or failure.
     *
     * IMPORTANT: This method MUST NOT be called from the module's runner thread (detects and throws).
     */
    fun <T> runSuspendingSync(timeoutMs: Long = 20_000L, block: suspend () -> T): T? {
        // detect runner thread: adjust predicate to your naming convention
        val tName = Thread.currentThread().name
        if (tName.startsWith("module-$name-thread")) {
            // caller is the module's event loop — refuse to block here (prevent deadlock)
            throw IllegalStateException("runSuspendingSync must NOT be called from module runner thread")
        }

        val task = Callable<T?> {
            try {
                runBlocking {
                    // optional per-call timeout
                    withTimeoutOrNull(timeoutMs) { block() }
                }
            } catch (t: Throwable) {
                null
            }
        }

        val f = blockingExecutor.submit(task)
        return try {
            f.get(timeoutMs + 1000L, TimeUnit.MILLISECONDS) // extra safety margin
        } catch (te: TimeoutException) {
            f.cancel(true)
            null
        } catch (ex: Throwable) {
            null
        }
    }

    fun shutdownBlockingExecutor() {
        blockingExecutor.shutdownNow()
    }

    /** Helper: fire-and-forget off the worker pool */
    fun launchWorker(block: suspend () -> Unit) {
        workerScope.launch { block() }
    }

    /** Helper: run blocking or suspend work and get result (suspends the caller coroutine). */
    suspend fun <T> callWorker(block: suspend () -> T): T =
        withContext(workerDispatcher) { block() }

    /** Shutdown worker resources when module stops */
    override fun stop() {
        workerScope.cancel()
        workerDispatcher.close()
    }
}

/**
 * Just a more uhhhh weird abstract module
 */
abstract class WeirdlyAbstractModule : AbstractModule() {
    internal var internalizedName: String = ""
    final override val name: String
        get() = internalizedName

    internal var internalizedDescription: String = ""
    final override val description: String
        get() = internalizedDescription
}

/**
 * These cannot communicate through communication
 */
abstract class AbstractSystemModule(
    override val name: String,
    override val description: String
) : AbstractModule(), TextUnderstander
{
    final override val isPaused: Boolean
        get() = false
    final override fun resume() { }
    final override val hasBeenStopped: Boolean
        get() = false

    private val DEFAULT_LOGGER: ContextLogger by lazy {
        ContextLogger(ContextLogger.ContextType.SUB_SYSTEM, "${name.uppercase()}-PRINTER",
            { isDeafened(name) }, true, name
        )
    }

    private lateinit var sessionFactory: SessionFactory

    /**
     * Set the Hibernate session factory for this module.
     * All DB operations should use this sessionFactory.
     */
    override fun setSessionFactory(factory: SessionFactory) {
        this.sessionFactory = factory
    }

    /**
     * Accessor for DB sessions in the module.
     */
    protected fun session() = sessionFactory.openSession()

    /**
     * Accessor for DB sessions in the module.
     */
    protected fun sessionFactory() = sessionFactory

    /**
     * Optional hooks for lifecycle management.
     */
    override fun start() {
        getLogger().print("SubsystemModule '$name' started")
    }

    final override fun stop() {
        super.stop()
        getLogger().print("SubsystemModule '$name' stopped")
        destroy()
    }

    final override fun pause() {
        getLogger().print("Subsystem modules cannot be paused. Dont use stop either as that should only be used internally...", ContextLogger.LogType.ERROR)
    }

    /**
     * Override this to clean up internal resources like temp files or DB connections
     */
    abstract fun destroy()

    final override fun handleRequest(request: JsonNode): Response<MapValue> =
        throw UnsupportedOperationException("System Modules Basically dont communicate through networks.. how did we get here...")

    final override fun getLogger(): ContextLogger = DEFAULT_LOGGER
}

internal interface Module {
    /**
     * Optional initialization hook called with the dispatcher instance when the module is registered.
     * Default is a no-op.
     */
    fun init(dispatcher: ModularXeroDispatcher) {}

    val description: String?
    val name: String
    val metrics: ModuleMetrics
    val shouldSaveLog: Boolean
    val isPaused: Boolean
    val hasBeenStopped: Boolean
    fun start()
    fun stop()
    fun pause()
    fun resume()

    /**
     * _**This runs on the [Dispatchers.IO] thread**_
     */
    @Nullable fun handleRequest(request: JsonNode): Response<MapValue>?
    fun getModuleAnnotatedClasses() : List<Class<*>>

    /**
     * If true, dispatcher will start the module automatically when registered.
     * Default false so we don't surprise ports at runtime.
     */
    fun autoStart(): Boolean = true

    /**
     * All commands must be registered under one command of the name of the module for specificity purposes.
     */
    fun getCommands(): Map<String, Class<Callable<*>>> = emptyMap()
    fun setSessionFactory(factory: SessionFactory)
    fun getLogger(): ContextLogger
}

interface TextUnderstander {
    /**
     * All implementations must handle the message with type `ping` (non-case-sensitive) this method can test everything about the module
     * it doesn't have to be a simple pong message, it can be a health check.
     *
     * _**This runs on the [Dispatchers.IO] thread**_
     */
    fun handle(request: Message): Message
}

/**
 * Base class for modules that want to host their own WebSocketServer.
 * Subclasses implement createServer(dispatcher) and may override autoStart().
 */
abstract class AbstractServerModule @JvmOverloads
constructor(private val serverPort: Int = 0) : WeirdlyAbstractModule()
{
    protected lateinit var dispatcher: ModularXeroDispatcher
    protected var isRunning: Boolean = false
    protected var serverModuleHasBeenStopped: Boolean = false

    // server instance managed by this helper
    private var serverInstance: TrackableWebSocket? = null

    override fun init(dispatcher: ModularXeroDispatcher) {
        super.init(dispatcher)
        this.dispatcher = dispatcher
    }

    /**
     * Create a WebSocketServer wired to this module's logic.
     * Implementations receive the dispatcher to allow cross-module calls or registration.
     */
    protected abstract fun createServer(dispatcher: ModularXeroDispatcher, address: InetSocketAddress): TrackableWebSocket?
    protected open fun createServer(dispatcher: ModularXeroDispatcher, portHint: Int): TrackableWebSocket? {
        return createServer(dispatcher, InetSocketAddress("0.0.0.0", serverPort))
    }

    final override val hasBeenStopped: Boolean
        get() = serverModuleHasBeenStopped

    final override fun start() {
        val keystoreFile = FileInputStream(System.getenv("WS_KEYSTORE"))
        val keystorePassword = System.getenv("WS_KEYSTORE_PASSWORD")
        val keyPassword = System.getenv("WS_KEY_PASSWORD")

        val ks = KeyStore.getInstance("JKS")
        ks.load(keystoreFile, keystorePassword.toCharArray())

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, keyPassword.toCharArray())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, null)

        if (serverInstance != null && isRunning) return // already running
        serverInstance = createServer(dispatcher, serverPort)
        if (serverInstance != null) {
            serverInstance!!.setWebSocketFactory(DefaultSSLWebSocketServerFactory(sslContext))
            serverInstance!!.start()
            getLogger().print("[${name}] server started on port ${serverInstance!!.port}", ContextLogger.LogType.SUCCESS)
        }
        else {
            getLogger().print("Unable to create server instance for $name", true)
        }
    }

    final override fun stop() {
        super.stop()
        serverInstance?.let {
            it.stop()
            getLogger().print("[${name}] server stopped", ContextLogger.LogType.PENDING)
        }
        serverInstance = null
        serverModuleHasBeenStopped = true
        destroy()
    }

    abstract fun destroy()
    override fun pause() { }
    override fun resume() { }

    open fun handleDispatchedRequest(conn: org.java_websocket.WebSocket, node: JsonNode) {
        serverInstance!!.onMessage(conn, node.toPrettyString())
    }

    final override fun handleRequest(request: JsonNode): Response<MapValue>? {
        error("Server Based Module dosent expect `handleRequest(request: JsonNode)` type call")
    }

    /**
     * Expose server instance for diagnostics (nullable).
     */
    fun getServer(): WebSocketServer? = serverInstance

    /**
     * By default server modules don't auto-start — override if you want automatic start.
     */
    final override fun autoStart(): Boolean = true
}

data class ModuleMetrics (
    private val memoryBytesInternal: AtomicLong = AtomicLong(0),
    private val messagesHandledInternal: AtomicLong = AtomicLong(0),
    private val bytesSentInternal: AtomicLong = AtomicLong(0),
    private val bytesReceivedInternal: AtomicLong = AtomicLong(0)
)
{
    val memoryBytes get() = memoryBytesInternal.get()
    val messagesHandled get() = messagesHandledInternal.get()
    val bytesSent get() = bytesSentInternal.get()
    val bytesReceived get() = bytesReceivedInternal.get()

    internal fun setMemoryBytes(bytes: Long) {
        memoryBytesInternal.addAndGet(bytes)
    }
    fun addBytesSent(bytes: Long) {
        bytesSentInternal.addAndGet(bytes)
    }
    fun addBytesReceived(bytes: Long) {
        bytesReceivedInternal.addAndGet(bytes)
    }
    fun addMessagesHandled(bytes: Long) {
        messagesHandledInternal.addAndGet(bytes)
    }
}

data class Message(
    val from: String, // sender module name
    val to: String,   // receiver module name (or "*" for broadcast)
    val type: String, // MessageType string - adapt if you have a MessageType enum/class
    val payload: MapValue
)

/**
 * Class-based communicator for intra-process module messaging.
 */
class ClassBasedCommunicator(
)
{
    val logger = ContextLogger(ContextLogger.ContextType.SUB_SYSTEM, "CLASS-COMMUNICATOR")
    companion object {
        private const val CORRELATION_KEY = "__corrId"
        private const val SYSTEM_KEY = "system"
    }

    // handlers by module name
    private val runners = ConcurrentHashMap<String, ModuleRunner>()

    fun registerModuleRunner(moduleName: String, runner: ModuleRunner) {
        runners[moduleName] = runner
    }

    fun unregister(moduleName: String) {
        runners.remove(moduleName)
    }

    // correlation id counter + pending response map
    private val idCounter = AtomicLong(1)

    /**
     * Fire-and-forget send. Dispatches message to target's handler (or broadcast if to == "*")
     */
    fun send(message: Message) {
        if (message.to == "*") {
            runners.values.forEach { r -> if (!r.offerMessage(MessageEnvelope(message, null))) {
                r.module.getLogger().print("Dropped broadcast to ${r.name} (mailbox full)", true)
            } }
            return
        }
        val r = runners[message.to] ?: run { logger.print("No runner for ${message.to}", true); return }
        if (!r.offerMessage(MessageEnvelope(message, null))) {
            r.module.getLogger().print("Mailbox full for ${message.to}", true)
        }
    }

    /**
     * Send a request and suspend until the response is available or timeout occurs.
     * Returns null if no response or on timeout.
     */
    suspend fun request(message: Message, timeoutMs: Long = 10_000L): Message? {
        val id = "cid-${idCounter.getAndIncrement()}"
        val enriched = message.copy(
            payload = MapValueBuilder()
                .append(message.payload)
                .put(CORRELATION_KEY, StringValue(id))
                .build()
        )

        val r = runners[enriched.to] ?: return null
        val deferred = CompletableDeferred<Message?>()
        val env = MessageEnvelope(enriched, deferred)

        r.deliverMessageBlocking(env, timeoutMs)
        return deferred.getCompletedOrNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> CompletableDeferred<T>.getCompletedOrNull(): T? {
    return try {
        this.getCompleted()
    } catch (e: Exception) {
        null
    }
}
