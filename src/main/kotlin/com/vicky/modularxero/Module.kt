package com.vicky.modularxero

import com.fasterxml.jackson.databind.JsonNode
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.common.Response
import com.vicky.modularxero.common.values.MapValue
import com.vicky.modularxero.common.values.MessageValue
import com.vicky.modularxero.db.ModuleDatabaseManager
import org.hibernate.SessionFactory
import org.java_websocket.server.WebSocketServer
import org.jetbrains.annotations.Nullable

const val DataFolderName: String = "modules-data"

/**
 * These can set a `DEFAULT_LOGGER` to act as the base for the sandboxing logger
 */
abstract class AbstractModule : Module {
    private val DEFAULT_LOGGER: ContextLogger by lazy {
        ContextLogger(ContextLogger.ContextType.SUB_SYSTEM, "${name.uppercase()}-MAIN_LOGGER")
    }

    override fun init(dispatcher: ModularXeroDispatcher) {
        DEFAULT_LOGGER.saveToFile(shouldSaveLog)
        super.init(dispatcher)
    }
    override fun getCommands(): List<Any> = emptyList()
    override val shouldSaveLog: Boolean
        get() = true

    final override fun getLogger(): ContextLogger = DEFAULT_LOGGER
}

internal interface Module {
    /**
     * Optional initialization hook called with the dispatcher instance when the module is registered.
     * Default is a no-op.
     */
    fun init(dispatcher: ModularXeroDispatcher) {}

    val name: String
    val metrics: ModuleMetrics
    val shouldSaveLog: Boolean
    fun start()
    fun stop()
    fun pause()
    @Nullable fun handleRequest(request: JsonNode): Response<MapValue<MessageValue<*>>>?
    fun getModuleAnnotatedClasses() : List<Class<*>>

    /**
     * If true, dispatcher will start the module automatically when registered.
     * Default false so we don't surprise ports at runtime.
     */
    fun autoStart(): Boolean = true

    /**
     * All commands must be registered under one command of the name of the module for specificity purposes.
     */
    fun getCommands(): List<Any> = emptyList()
    fun setSessionFactory(factory: SessionFactory)
    fun getLogger(): ContextLogger
}

/**
 * Base class for modules that want to host their own WebSocketServer.
 * Subclasses implement createServer(dispatcher) and may override autoStart().
 */
abstract class AbstractServerModule @JvmOverloads
constructor(private val serverName: String,
            private val serverPort: Int = 0) : AbstractModule() {

    protected lateinit var dispatcher: ModularXeroDispatcher
    protected var isRunning: Boolean = false

    // server instance managed by this helper
    private var serverInstance: WebSocketServer? = null

    override fun init(dispatcher: ModularXeroDispatcher) {
        this.dispatcher = dispatcher
    }

    /**
     * Create a WebSocketServer wired to this module's logic.
     * Implementations receive the dispatcher to allow cross-module calls or registration.
     */
    protected abstract fun createServer(dispatcher: ModularXeroDispatcher, portHint: Int): WebSocketServer?

    final override val name: String
        get() = serverName

    override fun start() {
        if (serverInstance != null && isRunning) return // already running
        serverInstance = createServer(dispatcher, serverPort)
        if (serverInstance != null) {
            serverInstance!!.start()
            println("[${name}] server started on port ${serverInstance!!.port}")
        }
        else {
            println("Unable to create server instance for $name")
        }
    }

    override fun stop() {
        serverInstance?.let {
            it.stop()
            println("[${name}] server stopped")
        }
        serverInstance = null
        destroy()
    }

    abstract fun destroy()
    override fun pause() { }

    open fun handleDispatchedRequest(conn: org.java_websocket.WebSocket, node: JsonNode) {
        serverInstance!!.onMessage(conn, node.toPrettyString())
    }

    final override fun handleRequest(request: JsonNode): Response<MapValue<MessageValue<*>>>? {
        error("Server Based Module dosent expect `handleRequest(request: JsonNode)` type call")
    }

    /**
     * Expose server instance for diagnostics (nullable).
     */
    fun getServer(): WebSocketServer? = serverInstance

    /**
     * By default server modules don't auto-start — override if you want automatic start.
     */
    override fun autoStart(): Boolean = true
}

data class ModuleMetrics(
    var memoryBytes: Long = 0,
    var messagesHandled: Long = 0,
    var bytesSent: Long = 0,
    var bytesReceived: Long = 0
) {
    fun addBytesSent(bytes: Long) {
        bytesSent += bytes
    }
    fun addBytesReceived(bytes: Long) {
        bytesReceived += bytes
    }
    fun addMessagesHandled(bytes: Long) {
        messagesHandled += bytes
    }
}

