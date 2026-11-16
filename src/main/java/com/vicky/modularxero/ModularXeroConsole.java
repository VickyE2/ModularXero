package com.vicky.modularxero;

import com.vicky.modularxero.common.*;
import com.vicky.modularxero.common.Logger.ANSIColor;
import kotlin.Unit;
import org.jline.reader.*;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.vicky.modularxero.common.TextKt.ansiText;

public class ModularXeroConsole extends Thread {
    private final ModularXeroDispatcher dispatcher;
    private final Runnable onExit;
    private final Terminal terminal;
    private Boolean running = true;
    private final ModularXeroCommandManager commandManager;

    public ModularXeroConsole(ModularXeroDispatcher dispatcher, Terminal terminal, Runnable onExit) {
        this.dispatcher = dispatcher;
        this.onExit = onExit;
        this.terminal = terminal;
        this.commandManager = new ModularXeroCommandManager(dispatcher);
    }

    public ModularXeroCommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public void run() {
        try {
            CommandLine cmd = commandManager.getRoot();

            terminal.writer().print("\u001b[?25l");
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();

            Startup.Companion.getCurrentUI().clearAll();
            Startup.Companion.setLogHolder(new ListContainmentElement(17));

            var holder = BlockElement.Companion.rounded(new TextElement(ansiText(builder -> {
                builder.color("yellow");
                builder.append("Commands");
                return Unit.INSTANCE;
            })));
            holder.add(Startup.Companion.getCommandLogHolder());
            Startup.Companion.getCurrentUI().add(new RowElement(
                    List.of(
                            new WeightedElement(holder),
                            new WeightedElement(Startup.Companion.getLogHolder())
                    ),
                    1
            ));

            var metrics = BlockElement.Companion.rounded(new TextElement(ansiText(builder -> {
                builder.color("grey");
                builder.append("Global Metrics");
                return Unit.INSTANCE;
            })));
            metrics.setFillMaxWidth(true);

            var memoryBar = new ProgressBarElementBuilder()
                    .text("Memory")
                    .filledColor(new StaticColorProvider(ANSIColor.BOLD + ANSIColor.CYAN))
                    .inProgressCharacter('/')
                    .outOfProgressCharacter('/')
                    .width(40)
                    .build();
            var memoryText = new TextElement("Memory:", false);
            var memory = new RowElement(List.of(
                    new WeightedElement(memoryText),
                    new WeightedElement(memoryBar,8)
            ));

            var cupBar = new ProgressBarElementBuilder()
                    .text("CPU")
                    .filledColor(new StaticColorProvider(ANSIColor.BOLD + ANSIColor.GREEN))
                    .inProgressCharacter('/')
                    .outOfProgressCharacter('/')
                    .width(40)
                    .build();
            var cpu = new RowElement(List.of(
                    new WeightedElement(new TextElement("CPU:", false)),
                    new WeightedElement(cupBar,8)
            ));

            MetricsCollector.INSTANCE.addUpdateHook((jvm, ignored) -> {
                memoryText.update("Memory - " + jvm.getHeapUsedMB() + "/" + jvm.getHeapMaxMB());
                memoryBar.update((double) jvm.getHeapUsedMB() / jvm.getHeapMaxMB(), "");
                cupBar.update(Math.min(1.0, Math.max(0.0, jvm.getCpuLoad())), "");
                return Unit.INSTANCE;
            });

            metrics.add(memory);
            metrics.add(cpu);

            Startup.Companion.getCurrentUI().add(metrics);

            AtomicReference<String> typingInput = new AtomicReference<>("");
            AtomicReference<String> finalInput = new AtomicReference<>("");
            StringBuilder currentBuffer = new StringBuilder();

            Attributes originalAttributes = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            var jLine = BlockElement.Companion.rounded(new TextElement("Input"));
            jLine.setFillMaxWidth(true);
            jLine.add(new UpdatingTextElement(() -> "[MoX] > " + typingInput.get()));
            Startup.Companion.getCurrentUI().add(jLine);

            UIOutputWriter writer = new UIOutputWriter(Startup.Companion.getCommandLogHolder());
            setupCommandLineWriters(cmd, writer);

            Thread serverThread = new Thread(() -> {
                Startup.Companion.getMain_logger().info("Server is running...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.start();

            while (running) {
                int c;
                try {
                    c = reader.read();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (c == -1) break;

                // Handle Ctrl+C or exit signal
                if (c == 3) {
                    running = false;
                    break;
                }

                if (c == '\r' || c == '\n') {
                    // User pressed Enter: commit to final input and reset buffer
                    String submitted = currentBuffer.toString();
                    finalInput.set(submitted);
                    currentBuffer.setLength(0);
                    typingInput.set("");

                    terminal.writer().println();
                    terminal.flush();
                } else if (c == 127 || c == 8) {
                    // Handle Backspace
                    if (!currentBuffer.isEmpty()) {
                        currentBuffer.deleteCharAt(currentBuffer.length() - 1);
                        // Visual backspace feedback on terminal
                        terminal.writer().print("\b \b");
                        terminal.flush();
                    }
                } else if (c >= 32 && c < 127) {
                    // Printable characters
                    currentBuffer.append((char) c);
                    terminal.writer().print((char) c);
                    terminal.flush();
                }

                // Updates on every single keystroke while the user is typing
                typingInput.set(currentBuffer.toString());

                if ("exit".equalsIgnoreCase(finalInput.get()) ||
                        "quit".equalsIgnoreCase(finalInput.get())) {

                    running = false;
                    Startup.Companion.setNormalShutdown(true);

                    dispatcher.getRunners().forEach((name, it) -> {
                        try {
                            it.stop();
                        } catch (Exception ignored) {
                        }
                    });

                    dispatcher.getModules().forEach((name, it) -> {
                        try {
                            it.stop();
                        } catch (Exception ignored) {
                        }
                    });

                    Startup.Companion.getMain_logger().info("Quitting....");
                    serverThread.interrupt();

                } else if (!finalInput.get().isBlank()) {
                    cmd.execute(finalInput.get().trim().split("\\s+"));
                    finalInput.set("");
                }
            }
            onExit.run();
            this.interrupt();
        } finally {
            // Always bring the cursor back when exiting
            terminal.writer().print("\u001b[?25h");
            terminal.flush();
        }
    }

    public void setupCommandLineWriters(CommandLine cmd, UIOutputWriter writer) {
        PrintWriter printWriter = new PrintWriter(writer, true);

        // Apply to current command
        cmd.setOut(printWriter);
        cmd.setErr(printWriter);

        // Force apply to all existing subcommands recursively
        for (CommandLine subcommand : cmd.getSubcommands().values()) {
            setupCommandLineWriters(subcommand, writer);
        }
    }


    public Boolean isRunning() {
        return running;
    }
}