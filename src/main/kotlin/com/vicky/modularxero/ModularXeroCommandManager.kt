package com.vicky.modularxero

import com.vicky.modularxero.DeafenCommand.Companion.MODULE_DEAFENING
import com.vicky.modularxero.Startup.Companion.commandLogHolder
import com.vicky.modularxero.Startup.Companion.logHolder
import com.vicky.modularxero.Startup.Companion.commandLogHolder
import com.vicky.modularxero.common.ListContainmentElement
import com.vicky.modularxero.common.MapValueBuilder
import com.vicky.modularxero.common.TextElement
import com.vicky.modularxero.common.ansiColorize
import com.vicky.modularxero.common.colorAtStop
import com.vicky.modularxero.common.getAnyWrapped
import com.vicky.modularxero.common.getWrapped
import com.vicky.modularxero.common.toFullMap
import com.vicky.modularxero.common.values.MessageValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import java.util.*
import java.util.concurrent.Callable

@Command(
    name = "",
    description = ["Server commands"],
    mixinStandardHelpOptions = true
)
class ModularXeroCommandManager(
    dispatcher: ModularXeroDispatcher
) : Callable<Int> {

    val root = CommandLine(this)
    companion object {
        lateinit var dispatcherRef: ModularXeroDispatcher
    }

    init {
        // Register default commands
        /* how do i make that libraries that each sub module needs it can append it so that the main load these classes (or downloads from maven) into a libraries folder. */
        root.addSubcommand("status", StatusCommand())
        root.addSubcommand("help", HelpCommand())
        root.addSubcommand("start", StartCommand())
        root.addSubcommand("pingtest", PingTestCommand())
        root.addSubcommand("stop", StopCommand())
        root.addSubcommand("pause", PauseCommand())
        root.addSubcommand("resume", ResumeCommand())
        root.addSubcommand("moduleinfo", AboutModuleCommand())
        root.addSubcommand("metrics", MetricsCommand())
        root.addSubcommand("deafen", DeafenCommand())

        dispatcher.getModules().values.forEach { module ->
            val moduleName = module.name

            val moduleSpec = CommandSpec.create()
                .name(moduleName)
                .usageMessage(
                    Model.UsageMessageSpec()
                        .description(module.description ?: "Commands for $moduleName")
                )

            val helpOption = Model.OptionSpec.builder("-h", "--help")
                .usageHelp(true) // Tell picocli this option triggers help
                .description("Show this help message and exit.")
                .build()

            module.getCommands().forEach { (cmdName, cmdClass) ->
                val cmdInstance = cmdClass.getDeclaredConstructor().newInstance()
                val cmdSpec = CommandLine(cmdInstance).commandSpec

                moduleSpec.addSubcommand(cmdName, cmdSpec)
            }

            moduleSpec.addOption(helpOption)

            root.addSubcommand(moduleName, moduleSpec)
        }
        dispatcherRef = dispatcher
    }

    fun execute(input: String) {
        val args = input.split(" ").toTypedArray()
        root.execute(*args)
    }

    fun registerModuleCommands(module: AbstractModule) {
        for (cmd in module.getCommands()) {
            root.addSubcommand(cmd::class.simpleName?.lowercase() ?: module.name, cmd)
        }
    }

    override fun call(): Int {
        commandLogHolder.info("Use --help to see available commands.")
        return 0
    }
}

@Command(name = "help", description = ["Displays help information about commands."], mixinStandardHelpOptions = true)
class HelpCommand : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    override fun call(): Int {
        usage(spec.root().commandLine(), commandPrintStream)
        return 0
    }
}

class LoadedModules : Iterable<String> {

    @Spec
    lateinit var spec: CommandSpec

    override fun iterator(): Iterator<String> {
        val action = spec.name()

        return when (action) {
            "start" -> ModularXeroCommandManager.dispatcherRef.getModules().filter { it.value.hasBeenStopped }.keys.iterator()
            "stop"  -> ModularXeroCommandManager.dispatcherRef.getModules().filter { !it.value.hasBeenStopped }.keys.iterator()
            "pause"  -> ModularXeroCommandManager.dispatcherRef.getModules().filter { !it.value.isPaused }.keys.iterator()
            "resume"  -> ModularXeroCommandManager.dispatcherRef.getModules().filter { it.value.isPaused }.keys.iterator()
            "deafen"  -> {
                val deafen = spec.findOption("-d").getValue<Boolean>()
                ModularXeroCommandManager.dispatcherRef.getModules().filter {
                    if (deafen) {
                        MODULE_DEAFENING[it.key]!!
                    }
                    else {
                        !MODULE_DEAFENING[it.key]!!
                    }
                }.keys.iterator()
            }
            else    -> ModularXeroCommandManager.dispatcherRef.getModules().keys.iterator()
        }
    }
}

@Command(name = "status", description = ["Show status of all modules"])
class StatusCommand : Callable<Int> {
    override fun call(): Int {
       commandLogHolder.info("Module Status:")
        ModularXeroCommandManager.dispatcherRef.getModules().forEach { (name, module) ->
           commandLogHolder.info("- $name: ${if (!module.isPaused && !module.hasBeenStopped) "running" else if (module.isPaused) "paused" else if (module.hasBeenStopped) "stopped" else "???"}")
        }
        return 0
    }
}

@Command(name = "about_module", description = ["Show information about a specific module"])
class AboutModuleCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"],
            completionCandidates = LoadedModules::class)
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            when (module) {
                is AbstractSystemModule -> {
                    commandLogHolder.info("Information about `system` module: $moduleName")
                }
                is AbstractServerModule -> {
                    commandLogHolder.info("Information about `server` module: $moduleName")
                }
                else -> {
                    commandLogHolder.info("Information about module: $moduleName")
                }
            }
            logHolder.printing  {
                sh("========================[${moduleName.uppercase()}]========================")
                spacer("   "); h("General Info")
                spacer("   "); row("Module Name", moduleName)
                spacer("   "); row("Module Description", module.description ?: "nil")
                spacer("   "); row("Is Suspended", module.isPaused.toString())
                spacer("   "); row("Is Shutdown", module.hasBeenStopped.toString())
                spacer("   "); row("Saves Logs", module.shouldSaveLog.toString())
                append("\n")
                spacer("   "); h("Descriptor Specifics")
                val desc = ModularXeroCommandManager
                    .dispatcherRef
                    .getDescriptors()[moduleName]
                if (desc != null) {
                    spacer("   "); row("Authors", desc.authors.ifNotEmptyElse("--NONE--") { it.joinToString(", ") })
                    spacer("   "); row("Dependencies", desc.mavenDeps.ifNotEmptyElse("--NONE--") { it.joinToString(",\n                 ") })
                    spacer("   "); row("Embedded Libraries", desc.embeddedLibs.ifNotEmptyElse("--NONE--") { it.joinToString(",\n                       ") })
                    spacer("   "); row("Maximum Api Level", desc.apiMax.toString())
                    spacer("   "); row("Minimum Api Level", desc.apiMin.toString())
                }
                else {
                    spacer("   No Descriptor Found.")
                }
                append("\n")
                spacer("   "); h("Metrics")
                spacer("   "); row("Bytes Sent", module.metrics.bytesSent.toString())
                spacer("   "); row("Bytes Received", module.metrics.bytesReceived.toString())
                spacer("   "); row("Memory Used", module.metrics.memoryBytes.toString())
                spacer("   "); row("Messages Handled", module.metrics.messagesHandled.toString())

                sh("=========================${"=".repeat(moduleName.uppercase().length)}=========================")
            }


            0
        } else {
            logHolder.error("Module not found: $moduleName")
            1
        }
    }
}

private fun getMessageableModules(
    dispatcher: ModularXeroDispatcher,
    target: String?
): List<Pair<String, AbstractModule>> {

    val modules = dispatcher.getModules()

    return if (target == null) {
        modules
            .filterValues { it is TextUnderstander }
            .map { it.key to it.value }
    } else {
        val m = modules[target]
        if (m != null && m is TextUnderstander) listOf(target to m) else emptyList()
    }
}

@Command(
    name = "ping-test",
    description = ["Benchmark Messageable modules using ping"]
)
class PingTestCommand : Callable<Int> {

    @Parameters(
        index = "0",
        arity = "0..1",
        description = ["Optional module name (omit to test all)"],
        completionCandidates = LoadedModules::class
    )
    var moduleName: String? = null

    @Option(names = ["-n", "--iterations"], description = ["Requests per module"])
    var iterations = 100

    @Option(names = ["-c", "--concurrency"], description = ["Parallelism"])
    var concurrency = 20

    @Option(names = ["-m", "--mode"], description = ["serial | parallel"])
    var mode = "parallel"

    @Option(names = ["-t", "--timeout"], description = ["Timeout ms"])
    var timeoutMs = 5_000L

    override fun call(): Int {
        val dispatcher = ModularXeroCommandManager.dispatcherRef

        val targets = getMessageableModules(dispatcher, moduleName)

        if (targets.isEmpty()) {
            logHolder.error(
                if (moduleName == null)
                    "No Messageable modules found."
                else
                    "Module '$moduleName' is not Messageable or does not exist."
            )
            return 1
        }

        commandLogHolder.info(
            "Running ping-test on ${targets.size} module(s)..."
        )

        runBlocking {
            for ((name, _) in targets) {
                runTestForModule(name)
            }
        }

        return 0
    }

    private suspend fun runTestForModule(moduleName: String) {
        val times = mutableListOf<Long>()
        var failed = 0
        var hasprintedonce = false

        val payload = MapValueBuilder()
            .put("checks", listOf("*"))
            .build()

        lateinit var finalR: AttributedString

        suspend fun sendOnce(): Long? {
            val start = System.nanoTime()
            val reply = classCommunicator.request(
                Message("ping-test", moduleName, "ping", payload),
                timeoutMs
            )
            return if (reply != null) {
                if (!hasprintedonce) {
                    hasprintedonce = true
                    finalR = AttributedStringBuilder().doing {
                        h("── response: [$moduleName] ───────────")
                        val full = reply.payload.toFullMap()
                        full.forEach { (key, value) ->
                            if (key == "checks") {
                                value as Map<*, *>
                                spacer("    ")
                                sh("──── checks ────")
                                value.forEach { (k, v) ->
                                    spacer("   ")
                                    rowmid(k.toString(), v.toString())
                                }
                            }
                            else {
                                row(key, value.toString())
                            }
                        }
                    }
                }
                System.nanoTime() - start
            } else null
        }

        if (mode.equals("serial", true)) {
            repeat(iterations) {
                val t = sendOnce()
                if (t != null) times += t else failed++
            }
        } else {
            val sem = kotlinx.coroutines.sync.Semaphore(concurrency)
            coroutineScope {
                repeat(iterations) {
                    launch {
                        sem.withPermit {
                            val t = sendOnce()
                            if (t != null) times += t else failed++
                        }
                    }
                }
            }
        }

        printStats(moduleName, times, failed, finalR)
    }

    private fun printStats(
        moduleName: String,
        timesNs: List<Long>,
        failed: Int,
        finalR: AttributedString
    ) {
        if (timesNs.isEmpty()) {
            logHolder.error(
                "[$moduleName] All requests failed."
            )
            return
        }

        val timesMs = timesNs.map { it / 1_000_000.0 }.sorted()
        val avg = timesMs.average()
        val p95 = timesMs[(timesMs.size * 95 / 100).coerceAtMost(timesMs.size - 1)]
        val p99 = timesMs[(timesMs.size * 99 / 100).coerceAtMost(timesMs.size - 1)]

        logHolder.printing {
            h("── [$moduleName] ───────────────────────")
            row("Requests", "${timesMs.size}")
            row("Failed", "$failed")
            row("Avg", "${"%.3f".format(avg)} ms")
            row("ping-95", "${"%.3f".format(p95)} ms")
            row("ping-99", "${"%.3f".format(p99)} ms")
            append(finalR)
        }
    }
}


@Command(name = "start", description = ["Start a module"])
class StartCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"],
        completionCandidates = LoadedModules::class)
    lateinit var moduleName: String
    @Option(names = ["-r", "--restart"], description = ["Weather to restart the module if its already running."])
    var shouldRestart: Boolean = false

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            if (module is AbstractSystemModule) {
                logHolder.error("Cannot start a system module through commands, only on server shutdown.")
                return 1
            }
            if (!module.hasBeenStopped && shouldRestart) {
                commandLogHolder.info("This module is already running will attempt to restart instead...")
                try {
                    module.pause()
                    ModularXeroCommandManager.dispatcherRef.getRunners()[moduleName]?.stop()
                    module.stop()
                    module.start()
                }
                catch (e: Exception) {
                    logHolder.error("Failed to restart module ${module.name}")
                    ModularXeroCommandManager.dispatcherRef.getRunners()[moduleName]?.stop()
                    module.stop()
                    return 1
                }
                logHolder.success("Module ${module.name} restarted successfully")
                return 0
            }
            else if (!module.hasBeenStopped) {
                logHolder.error("Cannot start running module.")
                return 1
            }
            ModularXeroCommandManager.dispatcherRef.getRunners()[moduleName]?.start()
            module.start()
            logHolder.success("Started module: $moduleName")
            0
        } else {
           logHolder.error("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "stop", description = ["Stop a module"])
class StopCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"],
        completionCandidates = LoadedModules::class)
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            try {
                if (module is AbstractSystemModule) {
                    logHolder.error("Cannot stop system module through commands only on shutdown.")
                    return 1
                }
                if (module.hasBeenStopped) {
                    logHolder.error("Module has already been stopped")
                    return 1
                }
                ModularXeroCommandManager.dispatcherRef.getRunners()[moduleName]?.stop()
                module.stop()
            } catch(e: Exception) {
                logHolder.error("Error while stopping module: ${module.name}")
                e.printStackTrace()
                return 0
            }
           logHolder.success("Stopped module: $moduleName")
            0
        } else {
           logHolder.error("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "deafen", description = ["Deafen a or all modules"])
class DeafenCommand : Callable<Int> {
    @Option(names = ["-m", "--module"], description = ["Output file (default: print to console)"],
        completionCandidates = LoadedModules::class)
    lateinit var moduleName: String
    @Option(names = ["-d", "--deafen"], description = ["Set deafening on or off"])
    var deafen: Boolean = true

    companion object {
        var MODULE_DEAFENING: MutableMap<String, Boolean> = mutableMapOf()

        fun isDeafened(moduleName: String?): Boolean =
            MODULE_DEAFENING[moduleName] ?: true
    }

    override fun call(): Int {
        return run {
            val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
            if (module != null) {
                MODULE_DEAFENING[moduleName] = deafen
                logHolder.success("${if (deafen) "Deafened" else "Un-deafened"} module: $moduleName")
                0
            } else {
                logHolder.error("Module not found: $moduleName")
                1
            }
        }
    }
}

@Command(name = "pause", description = ["Pause a module"])
class PauseCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"],
        completionCandidates = LoadedModules::class)
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            module.pause()
            logHolder.success("Paused module: $moduleName")
            0
        } else {
           logHolder.error("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "resume", description = ["Resume a paused a module"])
class ResumeCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"],
        completionCandidates = LoadedModules::class)
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            if (module.isPaused) {
                module.resume()
                logHolder.success("Resumed module: $moduleName")
                return 0
            }
            logHolder.error("Module isn't paused so it cannot be resumed")
            1
        } else {
           logHolder.error("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "metrics", description = ["Show server and module metrics"])
class MetricsCommand : Callable<Int> {
    override fun call(): Int {
        logHolder.info("=== JVM Metrics ===")
        MetricsCollector.jvm.let { metrics ->
            println("CPULoad: ${metrics.cpuLoad}")
            println("Available Processors: ${metrics.availableProcessors}")
            println("Heap Max MB: ${metrics.heapMaxMB}")
            println("Heap USed MB: ${metrics.heapUsedMB}")
        }

       // commandLogHolder.info("\n=== Network Metrics ===")
       //  MetricsCollector.collectNetworkMetrics().forEach { (k, v) -> println("$k: $v") }

       logHolder.info("\n=== Module Analytics ===")
        ModularXeroCommandManager.dispatcherRef.getModules().values.forEach { module ->
            val m = module.metrics
            module.metrics.setMemoryBytes(org.openjdk.jol.vm.VM.current().sizeOf(module))
            logHolder.info(formatModuleMetrics(module, m))
        }
        return 0
    }
}

private fun formatModuleMetrics(
    module: AbstractModule,
    m: ModuleMetrics
): String {

    val memoryRatio = if (MetricsCollector.jvm.heapMaxMB > 0) {
        (m.memoryBytes.toDouble() / MetricsCollector.jvm.heapMaxMB)
            .coerceIn(0.0, 1.0)
    } else {
        0.0
    }

    val memoryPercent =
        (memoryRatio * 100).toInt()

    val barWidth = 28
    val filled =
        kotlin.math.round(memoryRatio * barWidth)
            .toInt()
            .coerceIn(0, barWidth)

    val bar = buildString {
        for (i in 0 until barWidth) {
            val ratio =
                i.toDouble() / (barWidth - 1).coerceAtLeast(1)

            if (i < filled) {
                append(
                    ansiColorize(
                        colorAtStop(listOf("#00FF00", "#FFFF00", "#FF0000"), ratio),
                        "/"
                    )
                )
            } else {
                append(" ")
            }
        }
    }

    return buildString {
        appendLine("Module - ${module.name}:")
        appendLine("  ⊢ Memory Usage: |$bar| $memoryPercent% (${m.memoryBytes / 1024} KB)")
        appendLine("  ⨽ Messages")
        appendLine("      ⊢ Handled: ${m.messagesHandled}")
        appendLine("      ⊢ In:      ${m.bytesReceived} bytes")
        appendLine("      ⨽ Out:     ${m.bytesSent} bytes")
    }
}

val header: AttributedStyle = AttributedStyle.DEFAULT.foreground(0x8844FF).bold()
val smallHeader: AttributedStyle = AttributedStyle.DEFAULT.foreground(0x8844FF)
val label: AttributedStyle = AttributedStyle.DEFAULT.foreground(0xAAAAAA)
val value: AttributedStyle = AttributedStyle.DEFAULT.foreground(0xFFFFFF)
val accent: AttributedStyle = AttributedStyle.DEFAULT.foreground(0x44DDFF)
fun AttributedStringBuilder.h(text: String): AttributedStringBuilder =
    style(header).append(text).append("\n")
fun AttributedStringBuilder.a(text: String): AttributedStringBuilder =
    style(header).append(text).append("\n")
fun AttributedStringBuilder.sh(text: String): AttributedStringBuilder =
    style(smallHeader).append(text).append("\n")
fun AttributedStringBuilder.spacer(text: String): AttributedStringBuilder =
    style(header).append(text)
fun AttributedStringBuilder.row(key: String, valueText: String) {
    append("    ")
    style(label).append(key.padEnd(20))
    style(label).append(": ")
    style(accent).append(valueText)
    append("\n")
}
fun AttributedStringBuilder.rowmid(key: String, valueText: String) {
    append("    ")
    style(label).append(key.padEnd(5))
    style(label).append(": ")
    style(accent).append(valueText)
    append("\n")
}

fun AttributedStringBuilder.doing(block: AttributedStringBuilder.() -> Unit): AttributedString {
    this.block()
    return this.toAttributedString()
}

fun ListContainmentElement.error(s: String) {
    this.add(TextElement(AttributedStringBuilder().doing {
        append(s).style(AttributedStyle.DEFAULT.foreground(0x770000))
    }.toAnsi()))
}

fun ListContainmentElement.info(s: String) {
    this.add(TextElement(AttributedStringBuilder().doing {
        append(s).style(AttributedStyle.DEFAULT)
    }.toAnsi()))
}

private fun ListContainmentElement.success(s: String) {
    this.add(TextElement(AttributedStringBuilder().doing {
        append(s).style(AttributedStyle.DEFAULT.foreground(0x008800))
    }.toAnsi()))
}

private fun ListContainmentElement.printing(
    block: AttributedStringBuilder.() -> Unit
) {
    val builder = AttributedStringBuilder()
    builder.block()
    this.add(TextElement(builder.toAttributedString().toAnsi()))
}

/**
 * Nicely format arbitrary payload values into a readable string.
 *
 * - unwraps MessageValue
 * - formats Map and List recursively
 * - limits recursion depth with maxDepth
 */
fun formatAny(value: Any?, depth: Int = 0, maxDepth: Int = 6): String {
    if (value == null) return "null"
    if (depth > maxDepth) return "…"

    return when (value) {
        is MessageValue -> formatAny(value.getAnyWrapped(), depth, maxDepth)

        is Map<*, *> -> {
            if (value.isEmpty()) return "{}"
            // decide compact vs pretty based on nesting
            val entries = value.entries.map { (k, v) ->
                "${k.toString()}: ${formatAny(v, depth + 1, maxDepth)}"
            }
            if (entries.size <= 4 && depth >= 1) {
                "{ ${entries.joinToString(", ")} }"
            } else {
                // multiline for readability
                val indent = "  ".repeat(depth)
                val inner = entries.joinToString("\n") { "${indent}  $it" }
                "{\n$inner\n$indent}"
            }
        }

        is List<*> -> {
            if (value.isEmpty()) "[]"
            else {
                val items = value.map { formatAny(it, depth + 1, maxDepth) }
                if (items.size <= 6 && depth >= 1) "[${items.joinToString(", ")}]"
                else {
                    val indent = "  ".repeat(depth)
                    val inner = items.joinToString("\n") { "${indent}  $it" }
                    "[\n$inner\n${"  ".repeat(depth - 0)}]"
                }
            }
        }

        is Array<*> -> formatAny(value.toList(), depth, maxDepth)

        is String -> "\"$value\""
        is Char -> "'$value'"
        is Number, is Boolean, is UUID -> value.toString()

        else -> value.toString()
    }
}

fun <T> List<T>?.ifNotEmptyElse(value: T, converter: (List<T>) -> T): T {
    return if (this.isNullOrEmpty())
        value
    else converter.invoke(this)
}

/**
 * Helper that prints each top-level key/value using your `row` function.
 * If you want to use a different printer, replace `row` with your own.
 */
fun printPayloadMapFull(map: Map<String, Any?>, row: (String, String) -> Unit) {
    map.forEach { (key, value) ->
        val formatted = formatAny(value)
        row(key, formatted)
    }
}