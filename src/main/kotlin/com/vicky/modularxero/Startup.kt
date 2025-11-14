package com.vicky.modularxero

import ch.qos.logback.classic.Logger
import com.vicky.modularxero.common.util.HibernateUtil
import com.vicky.modularxero.modules.bueats.BuEatsModule
import java.io.File

class Startup {
    companion object {
        var dispatcher: ModularXeroDispatcher? = null

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                Class.forName("org.sqlite.JDBC")
                println("SQLite JDBC driver loaded successfully!")
                Class.forName("org.hibernate.community.dialect.SQLiteDialect")
                println("SQLiteDialect found and loaded.")
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                println("SQLiteDialect missing from classpath!")
            }

            val libsLoader = EmbeddedLibsLoader.extractAndCreateLoader("libs/", File("./modules-libs"))
            Thread.currentThread().contextClassLoader = libsLoader

            dispatcher = ModularXeroDispatcher()
            val scanner = ModularZeroScanner()
            val foundModules = scanner.findAndPrepareModules()

            for ((jarFile, loader, clazz) in foundModules) {
                if ((clazz.modifiers and 1024) != 0 || clazz.isInterface) {
                    println("Skipping abstract/interface ${clazz.name}")
                    loader.unload()
                    continue
                }
                val instance = clazz.kotlin.objectInstance ?: clazz.getDeclaredConstructor().newInstance()
                dispatcher!!.registerModule(instance as AbstractModule) // or supply console if you need
                java.util.logging.Logger.getGlobal().info("Registered ${clazz.name} from ${jarFile.name}")
            }
            dispatcher!!.registerModule(BuEatsModule())

            val server = ModularXero(dispatcher!!, 8025)
            val console = ModularXeroConsole(dispatcher!!) {
                ModularXeroConsole.GLOBAL_READER.printAbove("Stopping server...")
                server.stop()
                HibernateUtil.shutdown()
            }

            // HibernateUtil.getSessionFactory()
            console.start()
            server.start()

            println("ModularXero server running at ws://localhost:8025")
        }
    }
}