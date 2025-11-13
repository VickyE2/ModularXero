package com.vicky.modularxero.sandbox

import com.vicky.modularxero.AbstractModule
import com.vicky.modularxero.DataFolderName
import com.vicky.modularxero.ModuleClassLoader
import com.vicky.modularxero.Startup.Companion.dispatcher
import com.vicky.modularxero.common.Logger.ContextLogger
import java.io.File
import java.lang.reflect.Field
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger


class SandboxFile(private val moduleName: String, path: String) : File(path) {
    /**
     * mkdirs should create directories inside the module's sandbox only.
     * We call ModuleSandbox.ensureSafeDirs which will resolve the safe path and create directories under module data.
     */
    override fun mkdirs(): Boolean {
        return ModuleSandbox.ensureSafeDirs(moduleName, this.path)
    }

    /**
     * createNewFile should create the file inside the module sandbox only.
     * The ModuleSandbox.createFile(moduleName, path) call will ensure safety and parent dirs.
     * We then delegate to the actual File.createNewFile() on the safe file (which is returned)
     */
    @Synchronized
    override fun createNewFile(): Boolean {
        val safe = ModuleSandbox.createFile(moduleName, this.path)
        return safe.createNewFile()
    }
}

object ModuleSandbox {
    private val logger = Logger.getGlobal()

    // baseDir = <jar-parent>/modules-data
    private val baseDir: File = File(
        File(ModuleSandbox::class.java.protectionDomain.codeSource.location.toURI()).parentFile,
        DataFolderName
    ).apply { mkdirs() }

    @JvmStatic
    fun logException(t: Throwable, moduleName: String) {
        try {
            // create a module-specific error log
            val module = dispatcher!!.getModules()[moduleName]!!
            val logger = module.getLogger()
            printStackTrace(moduleName, t, logger)
            this.logger.finest("Logged exception for module=$moduleName: ${t.javaClass.name}: ${t.message}")
        } catch (ex: Throwable) {
            this.logger.severe("ModuleSandbox.logException failed: ${ex.message}")
        }
    }

    @JvmStatic
    fun logMessage(message: Any?, moduleName: String) {
        try {
            val module = dispatcher!!.getModules()[moduleName]!!
            val logger = module.getLogger()
            logger.print("---- ${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())} [captured] ----")
            logger.print(message.toString())
        } catch (ex: Throwable) {
            logger.severe("ModuleSandbox.logMessage failed: ${ex.message}")
        }
    }

    @JvmStatic
    fun printStackTrace(moduleName: String?, t: Throwable, logger: ContextLogger) {
        try {
            val now = ZonedDateTime.now()
            val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now)
            logger.print("---- $time [captured by $moduleName] ----", true)
            for (el in t.stackTrace) {
                logger.print("  at $el", true)
            }
        } catch (inner: Throwable) {
            this.logger.severe("Failed to sandbox stacktrace: $inner")
            inner.printStackTrace()
        }
    }

    /**
     * Return a SandboxFile for a single-string path.
     * Ensures parent dirs exist inside module sandbox.
     */
    @JvmStatic
    fun createFile(moduleName: String, possiblePath: String): SandboxFile {
        val moduleDir = File(baseDir, sanitize(moduleName))
        if (!moduleDir.exists()) {
            logger.finest("Creating modules data folder for $moduleName")
            moduleDir.mkdirs()
        }

        val path = normalizeRelativePath(possiblePath)
        val safeFile = File(moduleDir, path)

        if (!isSafePath(moduleDir, safeFile)) {
            throw SecurityException("🚫 Sandbox violation: $path escapes module boundaries of $moduleName")
        }

        // Ensure parent directories for this file exist (these are inside moduleDir)
        safeFile.parentFile?.mkdirs()

        logger.finest("Sandbox path resolved: ${safeFile.absolutePath}")
        // return SandboxFile bound to the safe path
        return SandboxFile(moduleName, safeFile.path)
    }

    /**
     * Return a SandboxFile for parent + child variant.
     * Ensures directories exist and safety checks.
     */
    @JvmStatic
    fun createFile(moduleName: String, parent: String, possibleChild: String): SandboxFile {
        val parentDirSandbox = createFile(moduleName, parent) // this already creates parent dirs inside module
        val child = normalizeRelativePath(possibleChild)
        val safeFile = File(parentDirSandbox.parentFile, child)

        if (!isSafePath(File(baseDir, sanitize(moduleName)), safeFile)) {
            throw SecurityException("🚫 Sandbox violation: $child escapes module boundaries of $moduleName")
        }

        safeFile.parentFile?.mkdirs()
        return SandboxFile(moduleName, safeFile.path)
    }

    /**
     * Called by SandboxFile.mkdirs() to ensure directories inside module sandbox are created.
     * Returns true if dirs were created or already exist.
     */
    @JvmStatic
    fun ensureSafeDirs(moduleName: String, possiblePath: String): Boolean {
        val sf = createFile(moduleName, possiblePath)
        val parent = File(sf.parent)
        return parent.mkdirs() || parent.exists()
    }

    private fun isSafePath(root: File, file: File): Boolean {
        return try {
            file.canonicalPath.startsWith(root.canonicalPath)
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun normalizeRelativePath(inPath: String): String {
        var p = inPath
        p = if (p.startsWith(".\\")) p.substringAfter(".\\")
        else if (p.startsWith("./")) p.substringAfter("./")
        else p
        // prevent absolute paths from sneaking in
        if (File(p).isAbsolute) {
            // convert absolute to relative filename only (safer)
            p = File(p).name
        }
        return p
    }

    private val activeModules: ConcurrentHashMap<String?, AbstractModule?> = ConcurrentHashMap<String?, AbstractModule?>()

    // Called when modules load/unload
    @JvmStatic
    fun registerModule(module: AbstractModule) {
        activeModules.put(module.name, module)
    }

    @JvmStatic
    fun unregisterModule(module: AbstractModule) {
        var classLoader = module.javaClass.classLoader
        if (classLoader is ModuleClassLoader) {
            activeModules.remove(classLoader.moduleName)
        }
    }

    @JvmStatic
    fun getModuleLogger(moduleName: String): ContextLogger? {
        val module = activeModules[moduleName]
        if (module != null) return module.getLogger()
        return ContextLogger(ContextLogger.ContextType.SYSTEM, "FALLBACK")
    }
}