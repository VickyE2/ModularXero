package com.vicky.modularxero

import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import java.util.concurrent.Callable
import java.util.logging.Logger

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
        root.addSubcommand("stop", StopCommand())
        root.addSubcommand("pause", PauseCommand())
        root.addSubcommand("metrics", MetricsCommand())
        root.addSubcommand("deafen", DeafenCommand())

        // Register module commands dynamically
        dispatcher.getModules().values.forEach { module ->
            for (cmd in module.getCommands()) {
                Logger.getGlobal().info("Registering command: $cmd")
                root.addSubcommand(null, cmd)
            }
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
        ModularXeroConsole.GLOBAL_READER.printAbove("Use --help to see available commands.")
        return 0
    }
}

@Command(name = "help", description = ["Displays help information about commands."], mixinStandardHelpOptions = true)
class HelpCommand : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    override fun call(): Int {
        usage(spec.root().commandLine(), System.out)
        return 0
    }
}

@Command(name = "status", description = ["Show status of all modules"])
class StatusCommand : Callable<Int> {
    override fun call(): Int {
       ModularXeroConsole.GLOBAL_READER.printAbove("Module Status:")
        ModularXeroCommandManager.dispatcherRef.getModules().forEach { (name, _) ->
           ModularXeroConsole.GLOBAL_READER.printAbove("- $name: running") // You could track actual state (started, stopped etc)
        }
        return 0
    }
}

@Command(name = "start", description = ["Start a module"])
class StartCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"])
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            module.start()
           ModularXeroConsole.GLOBAL_READER.printAbove("Started module: $moduleName")
            0
        } else {
           ModularXeroConsole.GLOBAL_READER.printAbove("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "stop", description = ["Stop a module"])
class StopCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"])
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            try {
                module.stop()
            } catch(_: Exception) {}
           ModularXeroConsole.GLOBAL_READER.printAbove("Stopped module: $moduleName")
            0
        } else {
           ModularXeroConsole.GLOBAL_READER.printAbove("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "deafen", description = ["Deafen a or all modules"])
class DeafenCommand : Callable<Int> {
    @Option(names = ["-m", "--module"], description = ["Output file (default: print to console)"])
    var moduleName: String? = null
    @Option(names = ["-d", "--deafen"], description = ["Set deafening on or off"])
    var deafen: Boolean = true

    companion object {
        var MODULE_DEAFENING: MutableMap<String, Boolean> = mutableMapOf()

        fun isDeafened(moduleName: String?): Boolean =
            MODULE_DEAFENING[moduleName] ?: true
    }

    override fun call(): Int {
        return if (moduleName != null) {
            val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
            if (module != null) {
                MODULE_DEAFENING.put(moduleName!!, deafen)
                ModularXeroConsole.GLOBAL_READER.printAbove("${if (deafen) "Deafened" else "Un-deafened"} module: $moduleName")
                0
            } else {
                ModularXeroConsole.GLOBAL_READER.printAbove("Module not found: $moduleName")
                1
            }
        }
        else {
            MODULE_DEAFENING.entries.forEach { (key, _) ->
                ModularXeroConsole.GLOBAL_READER.printAbove("${if (deafen) "Deafened" else "Un-deafened"} module: $key")
                MODULE_DEAFENING[key] = deafen
            }
            0
        }
    }
}

@Command(name = "pause", description = ["Pause a module"])
class PauseCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Module name"])
    lateinit var moduleName: String

    override fun call(): Int {
        val module = ModularXeroCommandManager.dispatcherRef.getModules()[moduleName]
        return if (module != null) {
            module.pause()
            ModularXeroConsole.GLOBAL_READER.printAbove("Paused module: $moduleName")
            0
        } else {
           ModularXeroConsole.GLOBAL_READER.printAbove("Module not found: $moduleName")
            1
        }
    }
}

@Command(name = "metrics", description = ["Show server and module metrics"])
class MetricsCommand : Callable<Int> {
    override fun call(): Int {
       ModularXeroConsole.GLOBAL_READER.printAbove("=== JVM Metrics ===")
        MetricsCollector.collectJvmMetrics().forEach { (k, v) -> println("$k: $v") }

       ModularXeroConsole.GLOBAL_READER.printAbove("\n=== Network Metrics ===")
        MetricsCollector.collectNetworkMetrics().forEach { (k, v) -> println("$k: $v") }

       ModularXeroConsole.GLOBAL_READER.printAbove("\n=== Module Analytics ===")
        ModularXeroCommandManager.dispatcherRef.getModules().values.forEach { module ->
            val m = module.metrics
            module.metrics.memoryBytes = org.openjdk.jol.vm.VM.current().sizeOf(module)
           ModularXeroConsole.GLOBAL_READER.printAbove("Module: [${module.name}] | Mem: ${m.memoryBytes/1024} KB | Msgs: ${m.messagesHandled} | In: ${m.bytesReceived} bytes | Out: ${m.bytesSent} bytes")
        }
        return 0
    }
}

