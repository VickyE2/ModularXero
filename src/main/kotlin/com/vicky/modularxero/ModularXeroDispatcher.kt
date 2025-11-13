package com.vicky.modularxero

import com.vicky.modularxero.common.Response
import com.vicky.modularxero.common.values.MapValue
import com.vicky.modularxero.common.values.MessageValue
import com.vicky.modularxero.common.values.StringValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.vicky.modularxero.common.util.HibernateUtil
import com.vicky.modularxero.db.ModuleDatabaseManager
import com.vicky.modularxero.sandbox.ModuleSandbox

/**
 * The main dispatcher that holds and manages modules.
 */
class ModularXeroDispatcher {
    private val modules: MutableMap<String, AbstractModule> = mutableMapOf()
    private val MAPPER = ObjectMapper()

    fun registerModule(module: AbstractModule) {
        println("Registering module ${module.name}!")
        modules[module.name] = module

        // init module with dispatcher reference
        module.init(this)
        ModuleSandbox.registerModule(module)

        // register annotated classes with Hibernate
        for (entity in module.getModuleAnnotatedClasses()) {
            HibernateUtil.registerEntity(entity)
        }

        // if a module wants to auto-start (e.g. servers), start it now
        if (module.autoStart()) {
            try {
                module.setSessionFactory(ModuleDatabaseManager.getSessionFactory(module))
                module.start()
            } catch (ex: Exception) {
                println("Failed to auto-start module ${module.name}: ${ex.message}")
            }
        }
    }

    fun startModule(name: String) {
        modules[name]?.start() ?: println("Module $name not found!")
    }

    fun stopModule(name: String) {
        if (modules[name] != null) {
            modules[name]!!.stop()
            ModuleSandbox.unregisterModule(modules[name]!!)
        }
        else {
            println("Module $name not found!")
        }
    }

    fun pauseModule(name: String) {
        modules[name]?.pause() ?: println("Module $name not found!")
    }

    fun listModules(): List<String> = modules.keys.toList()
    fun getModules(): Map<String, AbstractModule> = modules

    /**
     * Dispatch incoming request to its target module.
     */
    fun dispatch(conn: org.java_websocket.WebSocket, requestJson: String) {
        val node = MAPPER.readTree(requestJson)
        val nullableTargetModule = node.get("moduleAddress")

        if (nullableTargetModule != null) {
            val targetModule = nullableTargetModule.asText()
            val module = modules[targetModule]

            if (module != null) {
                module.metrics.messagesHandled++
                module.metrics.bytesReceived += requestJson.toByteArray().size.toLong()

                // 🚀 If module is a server-type, forward to its internal handler
                when (module) {
                    is AbstractServerModule -> {
                        module.handleDispatchedRequest(conn, node)
                    }

                    else -> {
                        val response = module.handleRequest(node)
                        if (response != null) {
                            response.id = node.get("id")?.asText() ?: "non-specified"
                            val responseJson = MAPPER.writeValueAsString(response)
                            module.metrics.bytesSent += responseJson.toByteArray().size.toLong()

                            // Send reply back to the originating client connection
                            conn.send(responseJson)
                            return
                        } else {
                            conn.send(MAPPER.writeValueAsString(Response.error("Unexpected Module Error occurred: $targetModule")))
                            return
                        }
                    }
                }
            }
        }
    }
}
