package com.vicky.modularxero

import com.vicky.modularxero.common.Response
import com.fasterxml.jackson.databind.ObjectMapper
import com.vicky.modularxero.Startup.Companion.main_logger
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.db.ModuleDatabaseManager
import com.vicky.modularxero.sandbox.ModuleSandbox
import java.util.concurrent.ConcurrentHashMap

/**
 * The main dispatcher that holds and manages modules.
 */
class ModularXeroDispatcher {
    private val modules: MutableMap<String, AbstractModule> = mutableMapOf()
    private val descriptors: MutableMap<String, ModuleDescriptor> = mutableMapOf()
    private val runners: MutableMap<String, ModuleRunner> = mutableMapOf()
    private val classToModule = ConcurrentHashMap<String, String>()

    fun registerClass(clazz: Class<*>, moduleName: String) {
        classToModule[clazz.name] = moduleName
    }

    fun getModuleForClass(className: String): String? {
        return classToModule[className]
    }

    constructor() {
        INSTANCE = this
    }

    companion object {
        val MAPPER = ObjectMapper()
        var INSTANCE: ModularXeroDispatcher? = null
    }
    private val logger = ContextLogger(main_logger, ContextLogger.ContextType.SUB_SYSTEM, "Modular-X-Dispatcher")


    fun registerModule(module: AbstractModule, desc: ModuleDescriptor? = null) {
        // existing setup (hibernate, session factory, init etc.)
        if (modules.contains(module.name)) {
            logger.warn("Module with name ${module.name} is already present. Please report this to the module creators or remove the module.")
            logger.warn("Conflicting module: ${modules[module.name]}, authors: ${desc?.authors ?: emptyList()}${if (desc != null) ", description: ${desc.description}, main class: ${desc.mainClass}, " else ""}")
            val exdesc = descriptors[module.name]
            logger.warn("Existing module: ${modules[module.name]}, authors: ${exdesc?.authors ?: emptyList()}${if (exdesc != null) ", description: ${exdesc.description}, main class: ${exdesc.mainClass}, " else ""}")
            return
        }
        modules[module.name] = module
        if (desc != null)
            descriptors[module.name] = desc

        val runner = ModuleRunner(module, module.name, mailboxCapacity = 2048, singleThreaded = true)
        runners[module.name] = runner

        if (module is TextUnderstander)
            classCommunicator.registerModuleRunner(module.name, runner)

        module.setSessionFactory(ModuleDatabaseManager.getSessionFactory(module))
        ModuleSandbox.registerModule(module)
        module.init(this)
        runner.start()

        if (module.autoStart()) {
            try { module.start() } catch (ex: Exception) { module.getLogger().print("Failed to auto-start: ${ex.message}", true) }
        }
    }

    fun dispatch(conn: org.java_websocket.WebSocket, requestJson: String) {
        val node = MAPPER.readTree(requestJson)
        val target = node.get("moduleAddress")?.asText() ?: return

        val runner = runners[target]
        if (runner == null) {
            conn.send(MAPPER.writeValueAsString(Response.error("Module $target not found")))
            return
        }

        runner.module.metrics.addMessagesHandled(1)
        runner.module.metrics.addBytesReceived(requestJson.toByteArray().size.toLong())

        val env = DispatchEnvelope(conn, node)
        if (!runner.offerDispatch(env)) {
            // queue full -> backpressure policy. E.g. reject client politely
            conn.send(MAPPER.writeValueAsString(Response.error("Module $target busy (queue full)")))
        }
        // otherwise module will handle and respond
    }

    fun getRunnerForModule(name: String): ModuleRunner? = runners[name]

    fun startModule(name: String) {
        modules[name]?.start() ?: logger.print("Module $name not found!", ContextLogger.LogType.WARNING)
    }

    fun stopModule(name: String) {
        if (modules[name] != null) {
            runners[name]?.stop()
            modules[name]!!.stop()
            ModuleSandbox.unregisterModule(modules[name]!!)
        }
        else {
            logger.print("Module $name not found!", ContextLogger.LogType.WARNING)
        }
    }

    fun pauseModule(name: String) {
        modules[name]?.pause() ?: logger.print("Module $name not found!", ContextLogger.LogType.WARNING)
    }

    fun resumeModule(name: String) {
        modules[name]?.resume() ?: logger.print("Module $name not found!", ContextLogger.LogType.WARNING)
    }

    fun listModules(): List<String> = modules.keys.toList()
    fun getModules(): Map<String, AbstractModule> = modules
    fun getDescriptors(): Map<String, ModuleDescriptor> = descriptors
    fun getRunners(): Map<String, ModuleRunner> = runners

    fun getModule(moduleName: String) : AbstractModule? = modules[moduleName]
    fun stopAll() {
        modules.forEach {
            stopModule(it.key)
        }
    }
}
