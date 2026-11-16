package com.vicky.modularxero

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.vicky.modularxero.common.BlockElement
import com.vicky.modularxero.common.HorizontalArrangement
import com.vicky.modularxero.common.ListContainmentElement
import com.vicky.modularxero.common.LoadingIndicatorElement
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.common.MapValueBuilder
import com.vicky.modularxero.common.MessageType
import com.vicky.modularxero.common.ResponseHelper.getBoolean
import com.vicky.modularxero.common.ResponseHelper.getCurrency
import com.vicky.modularxero.common.ResponseHelper.getDouble
import com.vicky.modularxero.common.ResponseHelper.getEnum
import com.vicky.modularxero.common.ResponseHelper.getFloat
import com.vicky.modularxero.common.ResponseHelper.getInt
import com.vicky.modularxero.common.ResponseHelper.getKClass
import com.vicky.modularxero.common.ResponseHelper.getList
import com.vicky.modularxero.common.ResponseHelper.getLong
import com.vicky.modularxero.common.ResponseHelper.getMap
import com.vicky.modularxero.common.ResponseHelper.getObject
import com.vicky.modularxero.common.ResponseHelper.getString
import com.vicky.modularxero.common.ResponseHelper.getTimeValue
import com.vicky.modularxero.common.ResponseHelper.getUUID
import com.vicky.modularxero.common.StaticColorProvider
import com.vicky.modularxero.common.TerminalUI
import com.vicky.modularxero.common.getAsMessage
import com.vicky.modularxero.common.parentTo
import com.vicky.modularxero.common.toJson
import com.vicky.modularxero.common.util.HibernateUtil
import com.vicky.modularxero.common.values.MapValue
import com.vicky.modularxero.common.values.MessageValue
import com.vicky.modularxero.modules.bueats.BuEatsModule
import com.vicky.modularxero.modules.email.EmailModule
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.security.KeyStore
import java.time.LocalDateTime
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Startup {
    companion object {
        var normalShutdown: Boolean = false
        lateinit var dispatcher: ModularXeroDispatcher
        lateinit var logHolder: ListContainmentElement
        val commandLogHolder: ListContainmentElement = ListContainmentElement(17)
        lateinit var currentUI: TerminalUI
        val main_logger = ContextLogger(ContextLogger.ContextType.SYSTEM, "MAIN")

        @OptIn(ExperimentalUuidApi::class)
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                Class.forName("org.sqlite.JDBC")
                java.util.logging.Logger.getGlobal().info("SQLite JDBC driver loaded successfully!")
                Class.forName("org.hibernate.community.dialect.SQLiteDialect")
                java.util.logging.Logger.getGlobal().info("SQLiteDialect found and loaded.")
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                java.util.logging.Logger.getGlobal().severe("SQLiteDialect missing from classpath!")
            }

            try {
                println("Starting json test")
                val builder = MapValueBuilder()
                    .put("string", "email_code_result")
                    .put("int", 10)
                    .put("long", 10L)
                    .put("double", 10.10)
                    .put("float", 10.10f)
                    .put("boolean", true)
                    .put("currency", BigDecimal.TEN.toKmp())
                    .put("enum", MessageType.VERIFY)
                    .put("list", listOf("A string", 20, MessageType.VERIFY))
                    .put(
                        "map", MapValueBuilder()
                            .put("some_key", 30)
                            .put("some_value", 70L)
                            .build()
                    )
                    .put("uuid", Uuid.random())
                    .put("time", kotlinx.datetime.LocalDateTime.now())
                    .put("kclass", Class.forName("org.hibernate.community.dialect.SQLiteDialect").kotlin)
                    .put("object_0", '2')
                    .put("object_1", byteArrayOf(10, 20, 30, 42))
                    .build()
                val json = builder.toJson()
                val unjson = json.getAsMessage<MapValue>()

                java.util.logging.Logger.getGlobal().info("serialized: $json")
                java.util.logging.Logger.getGlobal().info(
                    """
                    un-serialized: {
                        string: ${unjson.getString("string")},
                        int: ${unjson.getInt("int")},
                        long: ${unjson.getLong("long")},
                        double: ${unjson.getDouble("double")},
                        float: ${unjson.getFloat("float")},
                        currency: ${unjson.getCurrency("currency")},
                        boolean: ${unjson.getBoolean("boolean")},
                        enum: ${unjson.getEnum("enum", MessageType::class)},
                        list: ${unjson.getList<Any>("list")},
                        map: ${unjson.getMap<Any>("map")},
                        uuid: ${unjson.getUUID("uuid")},
                        time: ${unjson.getTimeValue("time")},
                        kclass: ${unjson.getKClass("kclass")},
                        kclass_name_test: ${unjson.getKClass("kclass")?.simpleName},
                        object_0: ${unjson.getObject<Char>("object_0")},
                        object_1: ${unjson.getObject<ByteArray>("object_1")},
                    }
                    """.trimIndent()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                error("Serialization Test Failed")
            }
            MetricsCollector.start()
            lateinit var console: ModularXeroConsole
            val terminal =
                TerminalBuilder.builder()
                    .dumb(false)
                    .system(true)
                    .build()

            currentUI = TerminalUI.attach(terminal)
            currentUI.terminal.puts(InfoCmp.Capability.clear_screen)
            currentUI.terminal.flush()

            val block = currentUI.add(BlockElement.rounded(LoadingIndicatorElement("Startup")).apply {
                borderColor = StaticColorProvider("#444444")
                horizontalArrangement = HorizontalArrangement.CENTER
                fillMaxWidth = true
            })

            logHolder = ListContainmentElement(7)
            currentUI.add(logHolder)

            initInterceptor()

            val embeddedLibs = LoadingIndicatorElement("Applying Embedded Libraries...").parentTo(block)
            val libsLoader = EmbeddedLibsLoader.extractAndCreateLoader("libs/", File("./modules-libs"), embeddedLibs)
            Thread.currentThread().contextClassLoader = libsLoader
            embeddedLibs.complete("Applied Embedded Libraries.")

            val dispatcherIndicator = LoadingIndicatorElement("Starting Module Dispatcher").parentTo(block)
            dispatcher = ModularXeroDispatcher()
            val scanner = ModularZeroScanner()
            val foundModules = scanner.findAndPrepareModules()

            dispatcherIndicator.update { original, _ -> "$original - Registering Inbuilt (BuEats)" }
            dispatcher.registerModule(BuEatsModule())
            dispatcherIndicator.update { original, _ -> "$original - Registering Inbuilt (Email)" }
            dispatcher.registerModule(EmailModule())
            for ((jarFile, loader, clazz, desc) in foundModules) {
                if ((clazz.modifiers and 1024) != 0 || clazz.isInterface) {
                    println("Skipping abstract/interface ${clazz.name}")
                    loader.unload()
                    continue
                }
                val instance = clazz.kotlin.objectInstance ?: clazz.getDeclaredConstructor().newInstance()
                try {
                    instance as WeirdlyAbstractModule
                } catch (_: Exception) {
                    main_logger
                        .severe("All modules being registered by jar scanning must me instances of WeirdlyAbstractModule.class not AbstractModule.class as the latter is internal")
                    continue
                }
                instance.internalizedName = desc.moduleName
                instance.internalizedDescription = desc.description ?: ""
                dispatcherIndicator.update { original, _ -> "$original - Registering Loaded (${instance.internalizedName})" }
                dispatcher.registerModule(instance, desc)
                main_logger.info("Registered ${clazz.name} from ${jarFile.name}")
            }
            dispatcherIndicator.complete("Module Dispatcher Started Successfully")

            val serverIndicator = LoadingIndicatorElement("Starting Server on 8025").parentTo(block)
            val server = ModularXero(dispatcher, InetSocketAddress("0.0.0.0", 8025))

            val consoleIndicator = LoadingIndicatorElement("Starting Real Console").parentTo(block)
            console = ModularXeroConsole(dispatcher, terminal) {
                main_logger.info("Stopping server...")
                server.stop()
                HibernateUtil.shutdown()
            }

            val keystoreFile = FileInputStream(System.getenv("WS_KEYSTORE"))
            val keystorePassword = System.getenv("WS_KEYSTORE_PASSWORD")
            val keyPassword = System.getenv("WS_KEY_PASSWORD")

            val ks = KeyStore.getInstance("JKS")
            ks.load(keystoreFile, keystorePassword.toCharArray())

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, keyPassword.toCharArray())   // <-- THIS MUST BE THE ENTRY PASSWORD

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, null, null)
            
            consoleIndicator.complete("Console Started")
            console.start()
            serverIndicator.update { original, _ -> "$original - Applying WebSocketFactory" }
            server.setWebSocketFactory(DefaultSSLWebSocketServerFactory(sslContext))
            server.start()
            serverIndicator.complete("Server Started")
            
            Runtime.getRuntime().addShutdownHook(Thread {
                if (!normalShutdown) {
                    println("Shutting down abruptly...")
                    dispatcher.stopAll()
                    server.stop()
                    console.interrupt()
                }
            })
        }
    }
}
