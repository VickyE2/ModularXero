package com.vicky.modularxero;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

import java.io.IOException;
import java.util.function.Function;

public class ModularXeroConsole extends Thread {
    private final ModularXeroDispatcher dispatcher;
    private final Runnable onExit;
    private Boolean running = true;
    private final ModularXeroCommandManager commandManager;
    public static LineReader GLOBAL_READER = null;

    public ModularXeroConsole(ModularXeroDispatcher dispatcher, Runnable onExit) {
        this.dispatcher = dispatcher;
        this.onExit = onExit;
        this.commandManager = new ModularXeroCommandManager(dispatcher);
    }

    public ModularXeroCommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public void run() {
        try {
            CommandLine cmd = commandManager.getRoot();
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            GLOBAL_READER = reader;
            Thread serverThread = new Thread(() -> {
                ModularXeroConsole.GLOBAL_READER.printAbove("Server is running...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.start();

            while (running) {
                try {
                    String input = reader.readLine("[MoX] >> ");
                    if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                        dispatcher.getModules().forEach((name, it) -> {
                            try {
                                it.stop();
                            } catch (Exception e) { }
                        });
                        running = false;
                        ModularXeroConsole.GLOBAL_READER.printAbove("Quitting....");
                        serverThread.interrupt();
                        break;
                    } else if (!input.isEmpty()) {
                        cmd.execute(input.split("\\s+"));
                    }
                } catch (Exception e) {
                    System.out.printf("Error: %s%n", e.getMessage());
                    e.printStackTrace();
                }

                try {
                    serverThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            onExit.run();
            this.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean isRunning() {
        return running;
    }
}