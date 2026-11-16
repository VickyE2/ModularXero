/* Licensed under Apache-2.0 2025. */
package com.vicky.modularxero.common.Logger;

import com.vicky.modularxero.ModularXeroConsole;
import com.vicky.modularxero.ModuleClassLoader;
import com.vicky.modularxero.Startup;
import com.vicky.modularxero.common.TextElement;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.vicky.modularxero.DeafenCommand;
import org.jetbrains.annotations.Nullable;

/**
 * ContextLogger provides a structured logging utility with ANSI color formatting
 * for both plugin-specific and global Bukkit logging.
 * <p>
 * This logger allows messages to be formatted with a context tag,
 * which includes a context type and a context name, as well as
 * additional formatting such as color and post effects (e.g., bold, italic).
 * </p>
 */
public class ContextLogger extends PrintStream {
    private final ContextType context;
    private final String contextName;
    public static final String sessionId = generateSessionId();
    public static final String logFilePath = "modules-globals/logs/";
    public static final String LOG_JOKE = "(peenared)";
    private final boolean isModule;
    private final String moduleName;
    private Boolean shouldSave = true;
    private Supplier<Boolean> deafen = () -> false;
    private final Class<?> callingClass;
    @Nullable public File logFile;

    /**
     * Constructs a ContextLogger with the specified context type and context name.
     * The plugin instance is retrieved via {@code vicky_utils.getPlugin()}.
     *
     * @param context     The context type (e.g., SYSTEM, FEATURE, HIBERNATE)
     * @param contextName A name representing the logging context (converted to uppercase)
     */
    public ContextLogger(ContextType context, String contextName) {
        super(new LoggerOutputStream(null), true);
        this.context = context;
        this.contextName = contextName.toUpperCase();
        Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.skip(1).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        Class<?> callerClassSub = getInvokingSubclass();

        if (callerClassSub != null && callerClassSub.getClassLoader() instanceof ModuleClassLoader) {
            this.callingClass = callerClassSub;
            deafen = () -> DeafenCommand.Companion.isDeafened(((ModuleClassLoader) callerClassSub.getClassLoader()).getModuleName());
            this.isModule = true;
            this.moduleName = ((ModuleClassLoader) callerClassSub.getClassLoader()).getModuleName();
        } else if (callerClass != null && callerClass.getClassLoader() != null && callerClass.getClassLoader() instanceof ModuleClassLoader) {
            this.callingClass = callerClass;
            deafen = () -> DeafenCommand.Companion.isDeafened(((ModuleClassLoader) callerClass.getClassLoader()).getModuleName());
            this.isModule = true;
            this.moduleName = ((ModuleClassLoader) callerClass.getClassLoader()).getModuleName();
        }
        else {
            this.callingClass = callerClass;
            this.isModule = false;
            this.moduleName = null;
        }
        ((LoggerOutputStream) this.out).logger = this;
    }

    public ContextLogger(ContextType context, String contextName, Supplier<Boolean> deafen, boolean isModule, String moduleName) {
        super(new LoggerOutputStream(null));
        this.context = context;
        this.contextName = contextName.toUpperCase();
        this.deafen = deafen;
        this.isModule = isModule;
        this.moduleName = moduleName;
        this.callingClass = null;
        ((LoggerOutputStream) this.out).logger = this;
    }

    /**
     * Constructs a ContextLogger with the specified context type and context name.
     * The plugin instance is retrieved via {@code vicky_utils.getPlugin()}.
     *
     * @param context     The context type (e.g., SYSTEM, FEATURE, HIBERNATE)
     * @param parent      The parent for thi
     * @param contextName A name representing the logging context (converted to uppercase)
     */
    public ContextLogger(ContextLogger parent, ContextType context, String contextName) {
        super(new LoggerOutputStream(null));
        this.context = context;
        this.contextName = parent.contextName + "-to-" + contextName.toUpperCase();

        this.logFile = parent.logFile;
        this.shouldSave = parent.shouldSave;
        this.callingClass = parent.callingClass;
        this.deafen = parent.deafen;
        this.isModule = true;
        this.moduleName = parent.moduleName;
        ((LoggerOutputStream) this.out).logger = this;
    }

    public static Class<?> getInvokingSubclass() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        List<StackWalker.StackFrame> frames = walker.walk(stream -> stream.limit(8).toList());

        Class<?> candidate = null;
        for (int i = 1; i < frames.size(); i++) {
            Class<?> c = frames.get(i).getDeclaringClass();
            if (candidate == null) {
                candidate = c;
                continue;
            }

            // if current frame is subclass of previous, that's likely the "real" one
            if (candidate.isAssignableFrom(c)) {
                return c;
            }
        }
        return candidate;
    }

    // Generate a unique session ID based on the current timestamp and a random UUID
    private static String generateSessionId() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8); // Get first 8 characters of UUID
        return timestamp + "_" + uniqueId; // Combine timestamp and UUID for uniqueness
    }

    /**
     * Logs a message to the plugin logger with an optional error flag.
     * The context tag is formatted in red if the message is an error, or in cyan otherwise.
     *
     * @param message The message to log
     * @param isError If true, the message is treated as an error (red formatting); otherwise, cyan is used.
     */
    public void print(String message, boolean isError) {
        if (message.isEmpty()) return;
        String contextTag =
                "[" + ANSIColor.colorize((isError ? "red" : "cyan") + "[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext =
                contextTag + (isError ? ANSIColor.colorize(message, ANSIColor.RED) : message);
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get() || isError) {
            printWithGlobalReader(finalContext);
        }
    }

    /**
     * Logs a message to the plugin logger using the default cyan context formatting.
     *
     * @param message The message to log.
     */
    public void print(String message) {
        if (message.isEmpty()) return;

        String contextTag =
                "[" + ANSIColor.colorize("cyan[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext = contextTag + message;
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get()) {
            printWithGlobalReader(finalContext);
        }
    }

    /**
     * Logs a message to the plugin logger with a specified log type.
     * The log type determines the color formatting for the context tag and the message.
     *
     * @param message The message to log.
     * @param type    The log type, which determines the color used for formatting.
     */
    public void print(String message, LogType type) {
        if (message.isEmpty()) return;

        String contextTag =
                "[" + ANSIColor.colorize(type.color + "[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext = contextTag + ANSIColor.colorize(type.color + "[" + message + "]");
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get() || type.priority <= 1) {
            printWithGlobalReader(finalContext);
        }
    }

    /**
     * Logs a message to the plugin logger with a specified log type and a post-formatting effect.
     * The post effect is applied to the message.
     *
     * @param message The message to log.
     * @param type    The log type which determines the base color for formatting.
     * @param effect  The post-formatting effect to apply (e.g., bold, italic, underline).
     */
    public void print(String message, LogType type, LogPostType effect) {
        if (message.isEmpty()) return;

        String contextTag =
                "[" + ANSIColor.colorize(type.color + "[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext =
                contextTag + ANSIColor.colorize(effect.effect + "[" + type.color + "[" + message + "]" + "]");
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get() || type.priority <= 1) {
            printWithGlobalReader(finalContext);
        }
    }

    /**
     * Logs a message to the plugin logger with a specified log type.
     * Optionally, the message itself can be affected by the log type's color formatting.
     *
     * @param message             The message to log.
     * @param type                The log type which determines the color formatting of the context tag.
     * @param shouldAffectMessage If true, the message is formatted with the log type's color; otherwise, it is not.
     */
    public void print(String message, LogType type, boolean shouldAffectMessage) {
        if (message.isEmpty()) return;

        String contextTag =
                "[" + ANSIColor.colorize(type.color + "[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext;
        if (shouldAffectMessage) {
            finalContext = contextTag + ANSIColor.colorize(type.color + "[" + message + "]");
        } else {
            finalContext = contextTag + message;
        }
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get() || type.priority <= 1) {
            printWithGlobalReader(finalContext);
        }
    }

    /**
     * Logs a message to the plugin logger with a specified log type and post-formatting effect,
     * with an option to affect the message formatting.
     *
     * @param message             The message to log.
     * @param type                The log type which determines the base color for formatting the context tag.
     * @param effect              The post-formatting effect to apply.
     * @param shouldAffectMessage If true, the message is additionally formatted with the log type's color; otherwise, only the effect is applied.
     */
    public void print(String message, LogType type, LogPostType effect, boolean shouldAffectMessage) {
        if (message.isEmpty()) return;
        String contextTag =
                "[" + ANSIColor.colorize(type.color + "[" + context + "-" + contextName + " -- " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy : hh:mm a")) + "]") + "] ";
        String finalContext;
        if (shouldAffectMessage) {
            finalContext =
                    contextTag + ANSIColor.colorize(effect.effect + "[" + type.color + "[" + message + "]" + "]");
        } else {
            finalContext = contextTag + ANSIColor.colorize(effect.effect + "[" + message + "]");
        }
        if (shouldSave) {
            writeLogToFile(finalContext);
        }
        if (!deafen.get() || type.priority <= 1) {
            printWithGlobalReader(finalContext);
        }
    }

    private static void printWithGlobalReader(String finalContext) {
        Startup.Companion.getLogHolder().add(new TextElement(finalContext, false));
    }

    private void writeLogToFile(String logMessage) {
        try {
            // Use the session ID in the log file name (e.g., log-2024-11-25_16-25-10_abc12345.log)
            File logFile = getModulableFile(callingClass);

            // Write the log message to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                logMessage = removeAnsiCodes(logMessage); // Remove ANSI codes before writing
                writer.write(logMessage);
                writer.newLine(); // Add a newline after each log entry
            }
        } catch (IOException e) {
            System.err.println("Failed to write log to file: " + e.getMessage());
        }
    }

    @NotNull
    private File getModulableFile(Class<?> callerClass) throws IOException {
        logFile = new File(logFilePath + "log-" + sessionId + ".log");
        if (callerClass != null) {
            var classLoader = callerClass.getClassLoader();
            if (classLoader instanceof ModuleClassLoader) {
                var path = logFilePath + ((ModuleClassLoader) classLoader).getModuleName() + "/" + "log-" + sessionId + ".log";
                // System.out.printf("%nI was %s asked to save by module: %s, path: %s", LOG_JOKE, ((ModuleClassLoader) classLoader).getModuleName(), path);
                logFile = new File(path);
            }
        }
        else if (isModule) {
            var path = logFilePath + moduleName + "/" + "log-" + sessionId + ".log";
            // System.out.printf("%nI was %s asked to save by module: %s, path: %s%n", LOG_JOKE, moduleName, path);
            logFile = new File(path);
        }

        // Check if the log file exists, create if it doesn't
        if (!logFile.exists()) {
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // ✅ ensures arc_server/ subfolder exists
            }
            logFile.createNewFile();
        }
        return logFile;
    }

    public static String removeAnsiCodes(String input) {
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    public void saveToFile(Boolean enableLoggerSaving) {
        this.shouldSave = enableLoggerSaving;
    }

    public void toast(String message) {
        printWithGlobalReader(
                message
        );
    }

    public void info(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.BASIC, effect, shouldAffectMessage);}
    public void info(String message, boolean shouldAffectMessage) {print(message, LogType.BASIC, shouldAffectMessage);}
    public void info(String message, LogPostType effect) {print(message, LogType.BASIC, effect);}
    public void info(String message) {print(message, LogType.BASIC);}
    public void warn(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.WARNING, effect, shouldAffectMessage);}
    public void warn(String message, boolean shouldAffectMessage) {print(message, LogType.WARNING, shouldAffectMessage);}
    public void warn(String message, LogPostType effect) {print(message, LogType.WARNING, effect);}
    public void warn(String message) {print(message, LogType.WARNING);}
    public void severe(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.ERROR, effect, shouldAffectMessage);}
    public void severe(String message, boolean shouldAffectMessage) {print(message, LogType.ERROR, shouldAffectMessage);}
    public void severe(String message, LogPostType effect) {print(message, LogType.ERROR, effect);}
    public void severe(String message) {print(message, LogType.ERROR);}
    public void pending(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.PENDING, effect, shouldAffectMessage);}
    public void pending(String message, boolean shouldAffectMessage) {print(message, LogType.PENDING, shouldAffectMessage);}
    public void pending(String message, LogPostType effect) {print(message, LogType.PENDING, effect);}
    public void pending(String message) {print(message, LogType.PENDING);}
    public void success(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.SUCCESS, effect, shouldAffectMessage);}
    public void success(String message, boolean shouldAffectMessage) {print(message, LogType.SUCCESS, shouldAffectMessage);}
    public void success(String message, LogPostType effect) {print(message, LogType.SUCCESS, effect);}
    public void success(String message) {print(message, LogType.SUCCESS);}
    public void debug(String message, LogPostType effect, boolean shouldAffectMessage) {print(message, LogType.AMBIENCE, effect, shouldAffectMessage);}
    public void debug(String message, boolean shouldAffectMessage) {print(message, LogType.AMBIENCE, shouldAffectMessage);}
    public void debug(String message, LogPostType effect) {print(message, LogType.AMBIENCE, effect);}
    public void debug(String message) {print(message, LogType.AMBIENCE);}


    /**
     * Enumeration defining different logging contexts.
     */
    public enum ContextType {
        /**
         * System-level logging context.
         */
        SYSTEM,
        /**
         * Sub-system-level logging context.
         * Usually used in cases of logs for a class of a system like:
         * <pre>
         *     class SystemA extends SystemB {
         *
         *     }
         * </pre>
         */
        SUB_SYSTEM,
        /**
         * Feature-level logging context.
         */
        FEATURE,
        /**
         * A sub feature level logging context.
         * Usually used in cases of logs for a class of a feature like:
         * <pre>
         *     class FeatureA extends FeatureB {
         *
         *     }
         * </pre>
         */
        MINI_FEATURE,
        /**
         * Hibernate-related logging context.
         */
        HIBERNATE,

        /**
         * Player or Connection based logging context
         */
        CONN,

        /**
         * Server command logging context
         */
        COMMAND
    }

    /**
     * Enumeration defining different log message types with associated ANSI color codes.
     */
    public enum LogType {
        /**
         * Represents error messages (red).
         */
        ERROR("red", 0),
        /**
         * Represents warning messages (yellow).
         */
        WARNING("yellow", 1),
        /**
         * Represents success messages (green).
         */
        SUCCESS("green", 2),
        /**
         * Represents pending messages (orange).
         */
        PENDING("orange", 4),
        /**
         * Represents basic messages (cyan).
         */
        BASIC("cyan", 4),
        /**
         * Represents plain messages (white).
         */
        PLAIN("white", 5),
        /**
         * Represents ambient messages (purple). basically debug
         */
        AMBIENCE("purple", 6);

        /**
         * The ANSI color code for the log type.
         */
        public final String color;
        public final int priority;

        LogType(String color, int priority) {
            this.color = color;
            this.priority = priority;
        }
    }

    /**
     * Enumeration defining post-formatting effects for log messages.
     */
    public enum LogPostType {
        /**
         * Underlines the log message.
         */
        UNDERLINE("underline"),
        /**
         * Strikes through the log message.
         */
        STRIKETHROUGH("strikethrough"),
        /**
         * Applies both bold and italic formatting to the log message.
         */
        BOLD_ITALIC("bold_italic"),
        /**
         * Applies italic formatting to the log message.
         */
        ITALIC("italic"),
        /**
         * Applies bold formatting to the log message.
         */
        BOLD("bold");

        /**
         * The effect code used for formatting the log message.
         */
        public final String effect;

        LogPostType(String effect) {
            this.effect = effect;
        }
    }

    private static class LoggerOutputStream extends OutputStream {
        private ContextLogger logger;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        LoggerOutputStream(ContextLogger logger) {
            this.logger = logger;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                logger.print(buffer.toString());
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ContextLogger)) return false;
        return ((ContextLogger) obj).context == this.context &&
                Objects.equals(((ContextLogger) obj).contextName, this.contextName);
    }
}
