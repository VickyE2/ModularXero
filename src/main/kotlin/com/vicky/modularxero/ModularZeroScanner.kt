package com.vicky.modularxero

import com.vicky.modularxero.DeafenCommand.Companion.MODULE_DEAFENING
import com.vicky.modularxero.Startup.Companion.main_logger
import com.vicky.modularxero.common.LoadingIndicatorElement
import com.vicky.modularxero.common.Logger.ContextLogger
import com.vicky.modularxero.common.Logger.ContextLogger.LogType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.jar.JarFile

// tiny descriptor holder
data class ModuleDescriptor(
    val mainClass: String,
    val moduleName: String,
    val embeddedLibs: List<String> = emptyList(),
    val mavenDeps: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val apiMin: Int,
    val apiMax: Int,
    val description: String?
)

private const val SANDBOX_INTERNAL = "com/vicky/modularxero/sandbox/ModuleSandbox"
private const val STRING_INTERNAL = "java/lang/String"
private const val CONTEXT_LOGGER_INTERNAL = "com/vicky/modularxero/common/Logger/ContextLogger"

// simple ModuleClassLoader (per-module loader)
class ModuleClassLoader(urls: Array<URL>, parent: ClassLoader, val moduleName: String) : URLClassLoader(urls, parent) {
    companion object {
        val logger = ContextLogger(ContextLogger.ContextType.SYSTEM, "MODULE-CLASS-LOADER", { true }, true, "module_patcher")
    }

    init {
        MODULE_DEAFENING[moduleName] = true
        logger.print("ModuleClassLoader for $moduleName URLs:")
        urLs.forEach { logger.print("  - $it", LogType.BASIC) }
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        try {
            return super.loadClass(name, resolve)
        } catch (e: ClassNotFoundException) {
        }

        val cls = try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            throw e
        }

        if (resolve) resolveClass(cls)
        ModularXeroDispatcher.INSTANCE!!.registerClass(cls, moduleName)
        return cls
    }

    override fun findClass(name: String): Class<*> {
        val resourcePath = name.replace('.', '/') + ".class"
        val input = getResourceAsStream(resourcePath)
            ?: throw ClassNotFoundException(name)

        val bytes = input.use { it.readBytes() }
        val transformed = transformClassBytes(bytes)
        return defineClass(name, transformed, 0, transformed.size)
    }

    private fun transformClassBytes(classBytes: ByteArray): ByteArray {
        val cr = org.objectweb.asm.ClassReader(classBytes)
        val cn = org.objectweb.asm.tree.ClassNode()
        // expand frames to get accurate info for COMPUTE_FRAMES later
        cr.accept(cn, org.objectweb.asm.ClassReader.SKIP_FRAMES)

        var modified = false

        for (mn in cn.methods) {
            val insnList = mn.instructions ?: continue
            val it = insnList.iterator()

            val hasFileOrPrint = cn.methods.any { mn ->
                mn.instructions.any { insn ->
                    insn is TypeInsnNode && insn.desc == "java/io/File" ||
                            insn is MethodInsnNode && (
                            (insn.owner == "java/lang/Throwable" && insn.name.startsWith("printStackTrace"))
                            )
                }
            }

            if (!hasFileOrPrint) return classBytes

            while (it.hasNext()) {
                val insn = it.next()
                if (cn.name.startsWith("com/vicky/modularxero/sandbox/")) {
                    continue
                }
                if (cn.name.contains('$') || cn.name.endsWith("DefaultImpls")) continue

                if (insn.opcode == Opcodes.NEW
                    && insn is TypeInsnNode
                    && insn.desc == "java/io/File") {

                    val dupNode = insn.next
                    if (dupNode == null || dupNode.opcode != Opcodes.DUP) continue

                    // find the INVOKESPECIAL that calls java/io/File.<init>
                    var look = dupNode.next
                    var matchedInit: MethodInsnNode? = null
                    var steps = 0
                    while (look != null && steps < 32) { // increase a bit to be tolerant
                        if (look is MethodInsnNode
                            && look.owner == "java/io/File"
                            && look.name == "<init>") {
                            matchedInit = look
                            break
                        }
                        look = look.next
                        steps++
                    }

                    if (matchedInit != null) {
                        try {
                            val desc = matchedInit.desc
                            when (desc) {
                                "(L$STRING_INTERNAL;)V" -> {
                                    // static factory: createFile(moduleName, path) -> SandboxFile
                                    val callNode = MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        SANDBOX_INTERNAL,
                                        "createFile",
                                        "(L$STRING_INTERNAL;L$STRING_INTERNAL;)Lcom/vicky/modularxero/sandbox/SandboxFile;",
                                        false
                                    )

                                    // find earliest push of the 1 argument and insert moduleName const before it
                                    val earliest = findArgPushStart(matchedInit, 1)
                                    if (earliest != null) {
                                        insnList.insertBefore(earliest, LdcInsnNode(moduleName))
                                    } else {
                                        // fallback: insert just before matchedInit
                                        insnList.insertBefore(matchedInit, LdcInsnNode(moduleName))
                                    }

                                    // replace constructor call with static factory
                                    insnList.set(matchedInit, callNode)
                                    // remove NEW and DUP
                                    insnList.remove(insn)
                                    insnList.remove(dupNode)
                                    modified = true
                                    logger.print("Patched File(String) -> ModuleSandbox.createFile in ${cn.name}.${mn.name}${mn.desc}")
                                    continue
                                }
                                "(L$STRING_INTERNAL;L$STRING_INTERNAL;)V" -> {
                                    // static factory: createFile(moduleName, parent, child) -> SandboxFile
                                    val callNode = MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        SANDBOX_INTERNAL,
                                        "createFile",
                                        "(L$STRING_INTERNAL;L$STRING_INTERNAL;L$STRING_INTERNAL;)Lcom/vicky/modularxero/sandbox/SandboxFile;",
                                        false
                                    )

                                    // find earliest push of the 2 arguments and insert moduleName const before it
                                    val earliest = findArgPushStart(matchedInit, 2)
                                    if (earliest != null) {
                                        insnList.insertBefore(earliest, LdcInsnNode(moduleName))
                                    } else {
                                        insnList.insertBefore(matchedInit, LdcInsnNode(moduleName))
                                    }

                                    insnList.set(matchedInit, callNode)
                                    insnList.remove(insn)
                                    insnList.remove(dupNode)
                                    modified = true
                                    logger.print("Patched File(String,String) -> ModuleSandbox.createFile in ${cn.name}.${mn.name}${mn.desc}")
                                    continue
                                }
                                else -> {
                                    // not a signature we handle
                                }
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            continue
                        }
                    }
                }

                if (insn is MethodInsnNode
                    && insn.opcode == Opcodes.INVOKESTATIC
                    && insn.owner == "java/nio/file/Paths"
                    && insn.name == "get") {
                    try {
                        when (insn.desc) {

                            // Paths.get(String, String...)
                            "(L$STRING_INTERNAL;[L$STRING_INTERNAL;)Ljava/nio/file/Path;" -> {

                                val callNode = MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    SANDBOX_INTERNAL,
                                    "getPath",
                                    "(L$STRING_INTERNAL;L$STRING_INTERNAL;[L$STRING_INTERNAL;)Ljava/nio/file/Path;",
                                    false
                                )

                                // insert moduleName before the FIRST argument
                                val earliest = findArgPushStart(insn, 2)
                                if (earliest != null) {
                                    insnList.insertBefore(earliest, LdcInsnNode(moduleName))
                                } else {
                                    insnList.insertBefore(insn, LdcInsnNode(moduleName))
                                }

                                insnList.set(insn, callNode)
                                modified = true
                                logger.print("Patched Paths.get(String,String...) -> ModuleSandbox.getPath in ${cn.name}.${mn.name}${mn.desc}")
                            }

                            // Paths.get(URI)
                            "(Ljava/net/URI;)Ljava/nio/file/Path;" -> {

                                val callNode = MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    SANDBOX_INTERNAL,
                                    "getPath",
                                    "(L$STRING_INTERNAL;Ljava/net/URI;)Ljava/nio/file/Path;",
                                    false
                                )

                                val earliest = findArgPushStart(insn, 1)
                                if (earliest != null) {
                                    insnList.insertBefore(earliest, LdcInsnNode(moduleName))
                                } else {
                                    insnList.insertBefore(insn, LdcInsnNode(moduleName))
                                }

                                insnList.set(insn, callNode)
                                modified = true
                                logger.print("Patched Paths.get(URI) -> ModuleSandbox.getPath in ${cn.name}.${mn.name}${mn.desc}")
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }


                val min = insn as? MethodInsnNode
                if (min != null) {
                    // ---------- 1) Throwable#printStackTrace ----------
                    if (min.opcode == Opcodes.INVOKEVIRTUAL &&
                        min.owner == "java/lang/Throwable" &&
                        min.name == "printStackTrace") {

                        when (min.desc) {
                            "()V" -> {
                                // stack: ..., throwable
                                // push Sandbox.getModuleLogger(moduleName)
                                insnList.insertBefore(min,
                                    MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        SANDBOX_INTERNAL,
                                        "getModuleLogger",
                                        "(Ljava/lang/String;)Ljava/io/PrintStream;",
                                        false
                                    )
                                )
                                insnList.insertBefore(min, LdcInsnNode(moduleName))

                                // change method to the (PrintStream) version
                                val repl = MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "java/lang/Throwable",
                                    "printStackTrace",
                                    "(Ljava/io/PrintStream;)V",
                                    false
                                )
                                insnList.set(min, repl)
                                modified = true

                                logger.print("Patched Throwable.printStackTrace() in ${cn.name}.${mn.name}${mn.desc}")
                                continue
                            }
                        }
                    }

                    if (min.opcode == Opcodes.INVOKEVIRTUAL
                        && min.owner == "java/io/PrintStream"
                        && min.name == "logger.print") {

                        // Typical pattern: GETSTATIC java/lang/System out|err -> push arg(s) -> INVOKEVIRTUAL logger.print
                        // We'll remove the GETSTATIC (if found nearby) and replace with ModuleSandbox.logMessage(Object, String)

                        // try to find GETSTATIC System.out|err within a few instructions back
                        var candidate: AbstractInsnNode? = min.previous
                        var foundGetStatic: FieldInsnNode? = null
                        var stepsBack = 0
                        while (candidate != null && stepsBack < 8) {
                            if (candidate is FieldInsnNode
                                && candidate.opcode == Opcodes.GETSTATIC
                                && candidate.owner == "java/lang/System"
                                && (candidate.name == "out" || candidate.name == "err")) {
                                foundGetStatic = candidate
                                break
                            }
                            // if we hit another method or label, we might be in a different pattern — keep scanning but limit
                            candidate = candidate.previous
                            stepsBack++
                        }

                        if (foundGetStatic != null) {
                            insnList.remove(foundGetStatic) // remove the PrintStream push
                        } else {
                            // Not the simple pattern: we could still try to handle but skip to avoid corrupting stack
                            // Fallback: skip transforming this invocation
                            continue
                        }

                        // Now stack has: ..., arg  (arg could be many types). We want (arg, moduleName) on stack.
                        // Insert moduleName so call signature will be (Ljava/lang/Object;Ljava/lang/String;)V
                        insnList.insertBefore(min, LdcInsnNode(moduleName))

                        val repl = MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SANDBOX_INTERNAL,
                            "logMessage",
                            "(Ljava/lang/Object;L$STRING_INTERNAL;)V",
                            false
                        )
                        insnList.set(min, repl)
                        modified = true
                        logger.print("Patched PrintStream.logger.print -> ModuleSandbox.logMessage in ${cn.name}.${mn.name}${mn.desc}")
                        continue
                    }
                }

                if (insn.opcode == Opcodes.NEW
                    && insn is TypeInsnNode
                    && insn.desc == CONTEXT_LOGGER_INTERNAL) {

                    val dupNode = insn.next
                    if (dupNode == null || dupNode.opcode != Opcodes.DUP) continue

                    // Find constructor
                    var look = dupNode.next
                    var matchedInit: MethodInsnNode? = null
                    var steps = 0
                    while (look != null && steps < 32) {
                        if (look is MethodInsnNode
                            && look.owner == CONTEXT_LOGGER_INTERNAL
                            && look.name == "<init>"
                            && look.desc == "(L$CONTEXT_LOGGER_INTERNAL\$ContextType;L$STRING_INTERNAL;)V") {
                            matchedInit = look
                            break
                        }
                        look = look.next
                        steps++
                    }

                    if (matchedInit != null) {
                        // Build the static call node descriptor correctly:
                        // getModuleLogger(Ljava/lang/String;)Lcom/vicky/.../ContextLogger;
                        val callNode = MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SANDBOX_INTERNAL,
                            "getModuleLogger",
                            "(L${STRING_INTERNAL};)L${CONTEXT_LOGGER_INTERNAL};",
                            false
                        )

                        // Find the earliest push of the constructor arguments (you already have this helper)
                        val earliest = findArgPushStart(matchedInit, 2)
                        val insertPoint = earliest ?: matchedInit // fall back to just before ctor

                        // Insert module name LDC and the static call *adjacent* and *in this order*:
                        insnList.insertBefore(insertPoint, LdcInsnNode(moduleName))
                        insnList.insertBefore(insertPoint, callNode)

                        // Now update the constructor descriptor to include the parent logger first.
                        // Note the correct L...; wrappers and $ for nested class name
                        matchedInit.desc =
                            "(L${CONTEXT_LOGGER_INTERNAL};L${CONTEXT_LOGGER_INTERNAL}\$ContextType;L${STRING_INTERNAL};)V"

                        modified = true
                        logger.print("Patched ContextLogger(ContextType,String) -> ContextLogger(parent,ContextType,String) in ${cn.name}.${mn.name}${mn.desc}")
                    }
                }
            }
        } // end methods

        if (!modified) return classBytes

        try {
            val cw = object : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
                override fun getCommonSuperClass(type1: String?, type2: String?): String {
                    return try {
                        super.getCommonSuperClass(type1, type2)
                    } catch (e: Exception) {
                        "java/lang/Object" // fallback
                    }
                }
            }
            cn.accept(cw)
            return cw.toByteArray()
        } catch (t: Throwable) {
            t.printStackTrace()
            return classBytes
        }
    }

    /**
     * Find the earliest instruction that corresponds to the first argument push
     * for an INVOKESPECIAL. It walks backwards from matchedInit and skips
     * Frame/Line/Label nodes and counts 'meaningful' instructions until argCount
     * pushes are seen. Returns that instruction (the earliest push) or null.
     */
    private fun findArgPushStart(matchedInit: MethodInsnNode, argCount: Int): AbstractInsnNode? {
        var p: org.objectweb.asm.tree.AbstractInsnNode? = matchedInit.previous
        var seen = 0
        var earliest: org.objectweb.asm.tree.AbstractInsnNode? = null

        while (p != null && seen < argCount) {
            if (p is org.objectweb.asm.tree.LineNumberNode || p is org.objectweb.asm.tree.FrameNode || p is org.objectweb.asm.tree.LabelNode) {
                p = p.previous
                continue
            }
            // count this as one argument-producing instruction (method call, var load, ldc, getstatic, new+invokespecial combos, etc.)
            seen++
            earliest = p
            p = p.previous
        }
        return earliest
    }

    /**
     * Utility to unload/close the classloader
     */
    fun unload() {
        try { this.close() } catch (_: Throwable) {}
    }
}

private fun bytesContains(hay: ByteArray, needle: ByteArray): Boolean {
    if (needle.isEmpty()) return true
    var i = 0
    val last = needle.size - 1
    val limit = hay.size - needle.size
    while (i <= limit) {
        if (hay[i] == needle[0]) {
            var j = 1
            while (j <= last && hay[i + j] == needle[j]) j++
            if (j > last) return true
        }
        i++
    }
    return false
}

/**
 * Search classes in the jar for the annotation descriptor (internal form).
 * Returns fully-qualified class names that contain the annotation descriptor bytes.
 */
private fun findAnnotatedClassesInJar(jar: File, annotationFqcn: String): List<String> {
    val internalDesc = "L" + annotationFqcn.replace('.', '/') + ";" // e.g. Lcom/vicky/.../ModuleEntry;
    val needle = internalDesc.toByteArray(Charsets.UTF_8)
    val found = mutableListOf<String>()
    JarFile(jar).use { jf ->
        val entries = jf.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            if (!e.name.endsWith(".class")) continue
            val bytes = jf.getInputStream(e).use { it.readBytes() }
            if (bytesContains(bytes, needle)) {
                val className = e.name.removeSuffix(".class").replace('/', '.')
                found += className
            }
        }
    }
    return found
}

class ModularZeroScanner(
    private val folder: File =
        File(File(ModularXero::class.java.protectionDomain.codeSource.location.toURI()).parentFile, "modules"),
    private val libsDir: File =
        File(File(ModularXero::class.java.protectionDomain.codeSource.location.toURI()).parentFile, "modules-libs") // where downloaded/extracted jars go
)
{
    val logger = ContextLogger(main_logger, ContextLogger.ContextType.SYSTEM, "Module-Scanner")
    init {
        if (!libsDir.exists()) libsDir.mkdirs()
        if (!folder.exists()) folder.mkdirs()
    }

    /** Read module.yml from the jar and parse a minimal format */
    private fun readModuleDescriptor(jar: File): ModuleDescriptor? {
        JarFile(jar).use { jf ->
            val entry = jf.getJarEntry("module.yml") ?: return null
            val txt = jf.getInputStream(entry).bufferedReader().use { it.readText() }
            var main: String? = null
            var description: String? = null
            var name: String? = null
            val embedded = mutableListOf<String>()
            val authors = mutableListOf<String>()
            val maven = mutableListOf<String>()

            var apiMin: Int? = null
            var apiMax: Int? = null

            // very small parser — supports single-line csv style or indented lists
            var currentlyOn = ""
            for (lineRaw in txt.lines()) {
                val line = lineRaw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                when {
                    line.startsWith("main:") -> if (main == null) main = line.substringAfter("main:").trim()
                    line.startsWith("description:") -> if (description == null) description = line.substringAfter("description:").trim()
                    line.startsWith("module_name:") || line.startsWith("name:") ->
                        if (name == null) name = line.substringAfter(':').trim()

                    line.startsWith("embeddedLibs:") -> {
                        val rest = line.substringAfter("embeddedLibs:").trim()
                        currentlyOn = "e"
                        if (rest.isNotEmpty()) embedded.addAll(rest.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    }
                    line.startsWith("mavenDeps:") -> {
                        val rest = line.substringAfter("mavenDeps:").trim()
                        currentlyOn = "m"
                        if (rest.isNotEmpty()) maven.addAll(rest.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    }
                    line.startsWith("authors:") -> {
                        val rest = line.substringAfter("authors:").trim()
                        currentlyOn = "a"
                        if (rest.isNotEmpty()) authors.addAll(rest.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    }

                    // API variants:
                    line.startsWith("api:") -> {
                        val rest = line.substringAfter("api:").trim()
                        currentlyOn = "api" // enter api block if rest empty
                        if (rest.isNotEmpty()) {
                            // handle "2" or "2-4"
                            if (rest.contains("-")) {
                                val parts = rest.split("-").map { it.trim() }
                                apiMin = parts.getOrNull(0)?.toIntOrNull()
                                apiMax = parts.getOrNull(1)?.toIntOrNull()
                            } else {
                                val v = rest.toIntOrNull()
                                if (v != null) { apiMin = v; apiMax = v }
                            }
                        }
                    }
                    line.startsWith("apiMin:") || line.startsWith("api_min:") || line.startsWith("minApi:") -> {
                        apiMin = line.substringAfter(':').trim().toIntOrNull()
                    }
                    line.startsWith("apiMax:") || line.startsWith("api_max:") || line.startsWith("maxApi:") -> {
                        apiMax = line.substringAfter(':').trim().toIntOrNull()
                    }

                    // inside an `api:` block, allow "min:" / "max:" lines
                    currentlyOn == "api" && line.startsWith("min:") -> {
                        apiMin = line.substringAfter("min:").trim().toIntOrNull()
                    }
                    currentlyOn == "api" && line.startsWith("max:") -> {
                        apiMax = line.substringAfter("max:").trim().toIntOrNull()
                    }

                    line.startsWith("-") -> {
                        val item = line.substringAfter("-").trim()
                        when (currentlyOn) {
                            "m" -> maven.add(item)
                            "a" -> authors.add(item)
                            else -> embedded.add(item)
                        }
                    }
                    // else ignore
                }
            }

            if (main == null) return null
            if (main.endsWith(".class")) {
                logger.print("The module main class shouldn't end with .class," +
                        " tell the developers${" [" + authors.joinToString() + "]"} about this.", LogType.WARNING)
                main = main.dropLast(".class".length)
            }
            if (name == null) throw IllegalArgumentException("Every module yml must have a `moduleName` specified")
            if (apiMin == null) throw IllegalArgumentException("Minimum api level MUST be specified")


            // Normalise: if only apiMin provided, treat apiMax as Int.MAX_VALUE (meaning ">= min")
            // If only apiMax provided and no min, treat min as 1 (legacy)
            val normalMin = apiMin
            val normalMax = when {
                apiMax != null -> apiMax
                else -> apiMin
            }

            return ModuleDescriptor(main, name, embedded, maven, authors, normalMin, normalMax, description)
        }
    }

    /** Extract embedded jars referenced inside the module jar to a destination folder. Returns list of extracted files. */
    private fun extractEmbeddedJars(moduleJar: File, embeddedPaths: List<String>, destDir: File): List<File> {
        if (embeddedPaths.isEmpty()) return emptyList()
        val extracted = mutableListOf<File>()
        JarFile(moduleJar).use { jf ->
            for (path in embeddedPaths) {
                val entry = jf.getJarEntry(path) ?: continue
                val out = File(destDir, "${moduleJar.nameWithoutExtension}_${File(path).name}")
                jf.getInputStream(entry).use { inp ->
                    FileOutputStream(out).use { outS ->
                        inp.copyTo(outS)
                    }
                }
                extracted += out
            }
        }
        return extracted
    }

    /** Download a single artifact from Maven Central — NO transitive resolution! */
    private fun downloadFromMavenCentral(coord: String, destDir: File): File? {
        val parts = coord.split(':')
        if (parts.size < 3) return null
        val (group, artifact, version) = parts
        val groupPath = group.replace('.', '/')
        val jarName = "$artifact-$version.jar"
        val url = "https://repo1.maven.org/maven2/$groupPath/$artifact/$version/$jarName"
        val out = File(destDir, jarName)
        if (out.exists()) {
            // logger.print("Found already existing module $coord", LogType.AMBIENCE)
            return out
        }
        val start = Instant.now();

        logger.print("Downloading $url -> $coord")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) {
                logger.print("Failed to download $url.jar: HTTP ${conn.responseCode}", true)
                return null
            }
            conn.inputStream.use { inp ->
                FileOutputStream(out).use { outS -> inp.copyTo(outS) }
            }
            logger.print("Downloaded $url.jar -> took ${Duration.between(start, Instant.now()).toSeconds()}s")
            return out
        } catch (t: Throwable) {
            logger.print("Error downloading $coord: ${t.message}", true)
            return null
        }
    }

    /**
     * Load modules: returns a list of triples (module jar file, module classloader, mainClass).
     * Only the main class declared in module.yml is loaded — we do not scan every class.
     */
    fun findAndPrepareModules(): List<Quad<File, ModuleClassLoader, Class<*>, ModuleDescriptor>> {
        val results = mutableListOf<Quad<File, ModuleClassLoader, Class<*>, ModuleDescriptor>>()
        if (!folder.exists() || !folder.isDirectory) {
            logger.print("Folder for modules `$folder` dosent exist", LogType.WARNING)
            return results
        }

        val jars = folder.listFiles { f ->
            logger.print("Found possible (jar-able) entry: ${f.name}")
            f.extension.equals("jar", ignoreCase = true) } ?: return results
        val parent = ModularZeroScanner::class.java.classLoader // app classloader as parent

        jars@for (jar in jars) {
            try {
                val descFromYml = readModuleDescriptor(jar)
                val desc = if (descFromYml != null) {
                    descFromYml
                }
                else {
                    // attempt annotation fallback
                    val annotated = try {
                        findAnnotatedClassesInJar(jar, ModuleEntry::class.java.name)
                    } catch (t: Throwable) {
                        logger.print("Failed scanning ${jar.name} for annotations: ${t.message}", true)
                        emptyList()
                    }

                    when {
                        annotated.isEmpty() -> {
                            logger.print("No module.yml and no @ModuleEntry in ${jar.name}, skipping.", LogType.WARNING)
                            continue@jars
                        }
                        annotated.size > 1 -> {
                            logger.print("Jar ${jar.name} contains multiple @ModuleEntry annotated classes: ${annotated.joinToString(", ")} — only one allowed. Skipping.", LogType.ERROR)
                            continue@jars
                        }
                        else -> {
                            val mainClassName = annotated.first()
                            // create temporary loader for that jar so we can read annotation values reflectively
                            val tempUrls = arrayOf(jar.toURI().toURL())
                            val tempLoader = ModuleClassLoader(tempUrls, parent, jar.nameWithoutExtension + "-annot-scan")
                            val moduleClazz = tempLoader.loadClass(mainClassName)
                            val ann = moduleClazz.getAnnotation(ModuleEntry::class.java)
                            if (ann == null) {
                                logger.print("Annotation was detected by bytes but reflection failed to find @ModuleEntry on $mainClassName", true)
                                continue@jars
                            }

                            // sanity: require moduleName present
                            if (ann.moduleName.isBlank()) {
                                logger.print("Annotated module in ${jar.name} must provide a non-empty moduleName in @ModuleEntry", LogType.ERROR)
                                continue@jars
                            }

                            logger.print("Found annotated Module class $mainClassName with name ${ann.moduleName}", LogType.SUCCESS)
                            ModuleDescriptor(
                                mainClass = mainClassName,
                                moduleName = ann.moduleName,
                                embeddedLibs = ann.embeddedLibs.toList(),
                                mavenDeps = ann.mavenDeps.toList(),
                                authors = ann.authors.toList(),
                                description = ann.description,
                                apiMin = ann.min,
                                apiMax = ann.max
                            )
                        }
                    }
                }

                logger.print("Module descriptor built for ${jar.name}: main=${desc.mainClass}", LogType.SUCCESS)

                // extract any embedded jars inside the module
                val extracted = extractEmbeddedJars(jar, desc.embeddedLibs, libsDir)

                // download any maven deps (non-transitive)
                val downloaded = desc.mavenDeps.mapNotNull { coord -> downloadFromMavenCentral(coord, libsDir) }

                // Prepare list of URLs: the module jar itself + extracted + downloaded
                val urls = mutableListOf(jar.toURI().toURL())
                urls += extracted.map { it.toURI().toURL() }
                urls += downloaded.map { it.toURI().toURL() }

                // Create per-module loader (isolated), parent = app classloader (so module sees API)
                val loader = ModuleClassLoader(urls.toTypedArray(), parent, desc.moduleName)

                // Load only the declared main class using module loader
                val moduleClazz = loader.loadClass(desc.mainClass)

                if (isModuleCompatible(desc, ModularXero.API)) {
                    results += Quad(jar, loader, moduleClazz, desc)
                }
                else {
                    logger.print("Module `${desc.moduleName} is not compatible with the current build's api version." +
                            " Module api min: ${desc.apiMin}, Module api max: ${desc.apiMax}, env api: ${ModularXero.API}")
                }
            } catch (t: Throwable) {
                logger.print("Failed to prepare module ${jar.name}: ${t.message}", true)
            }
        }
        return results
    }
}

fun isModuleCompatible(
    desc: ModuleDescriptor,
    platformApiVersion: Int
): Boolean {
    val min = desc.apiMin
    val max = desc.apiMax
    return platformApiVersion in min..max
}

data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Extracts jars embedded under the given path inside the runnable JAR (e.g. "libs/")
 * into [outputDir] and returns a URLClassLoader that loads those jars.
 *
 * Usage:
 * val loader = EmbeddedLibsLoader.extractAndCreateLoader("libs/", File("modules-libs"))
 * Thread.currentThread().contextClassLoader = loader
 */
object EmbeddedLibsLoader {

    /**
     * Extract embedded jars from the running jar (entries under resourcePrefix), put them into outputDir,
     * and create a URLClassLoader that loads them (parent = current app classloader).
     *
     * resourcePrefix must end with a slash if you want to search under a folder (eg "libs/").
     */
    fun extractAndCreateLoader(
        resourcePrefix: String = "libs/",
        outputDir: File = File("./modules-libs"),
        embeddedLibs: LoadingIndicatorElement
    ): URLClassLoader {
        if (!outputDir.exists()) outputDir.mkdirs()

        val appClassLoader = EmbeddedLibsLoader::class.java.classLoader
        val codeSource = EmbeddedLibsLoader::class.java.protectionDomain.codeSource

        val extractedFiles = mutableListOf<File>()

        if (codeSource != null && codeSource.location != null) {
            val locationUrl = codeSource.location
            val locationFile = File(locationUrl.toURI())

            if (locationFile.isFile) {
                // Running from a jar file — open and extract entries under resourcePrefix
                JarFile(locationFile).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!name.startsWith(resourcePrefix)) continue
                        if (!name.endsWith(".jar")) continue

                        val outName = name.substringAfterLast('/') // keep just filename
                        val outFile = File(outputDir, outName)

                        // Avoid re-extraction if file exists and size matches (optional)
                        if (outFile.exists()) {
                            val sizeMatches = (entry.size >= 0 && outFile.length() == entry.size)
                            if (sizeMatches) {
                                extractedFiles += outFile
                                continue
                            }
                        }

                        // Extract
                        jar.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { out ->
                                input.copyTo(out)
                            }
                        }
                        // try to mark delete on exit (best-effort)
                        extractedFiles += outFile
                    }
                }
            } else if (locationFile.isDirectory) {
                // Running from IDE (exploded classes). Look for "libs/" directory next to classes.
                val libsDir = File(locationFile, resourcePrefix)
                if (libsDir.exists() && libsDir.isDirectory) {
                    libsDir.listFiles { f -> f.extension.equals("jar", ignoreCase = true) }
                        ?.forEach { src ->
                            val outFile = File(outputDir, src.name)
                            // copy if not exists or different size
                            if (!outFile.exists() || outFile.length() != src.length()) {
                                Files.copy(src.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                outFile.deleteOnExit()
                            }
                            extractedFiles += outFile
                        }
                } // else nothing to extract
            }
        } else {
            // As a fallback: try to load resources via classloader (useful in some environments)
            val urls = appClassLoader.getResources(resourcePrefix)
            while (urls.hasMoreElements()) {
                val url = urls.nextElement()
                // Not going to handle every resource URL scheme here; prefer jar or exploded.
                embeddedLibs.update { original, _ -> "$original - resource: ${url.toString().ellipsize(6)}" }
            }
        }

        // Build classloader
        val jarUrls = extractedFiles.map { it.toURI().toURL() }.toTypedArray()
        // Parent should be the app classloader so extracted libs can see main app APIs
        val loader = URLClassLoader(jarUrls, appClassLoader)

        return loader
    }
}
