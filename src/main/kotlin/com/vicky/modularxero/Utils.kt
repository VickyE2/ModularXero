package com.vicky.modularxero

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.vicky.modularxero.Startup.Companion.commandLogHolder
import com.vicky.modularxero.Startup.Companion.logHolder
import com.vicky.modularxero.common.ListContainmentElement
import com.vicky.modularxero.common.TextElement
import com.vicky.modularxero.common.ansiText
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jline.terminal.Terminal
import java.io.PrintStream
import java.io.Writer
import java.math.BigDecimal
import java.time.LocalDateTime


class TerminalUIAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        val level = event.level.toString()
        val logger = event.loggerName
        val message = event.formattedMessage

        logHolder.add(
            TextElement(
                ansiText {
                    when (event.level) {
                        Level.ERROR -> {
                            color("red")
                            append("ERROR")
                            endStyle()
                        }

                        Level.WARN -> {
                            color("yellow")
                            append("WARN ")
                            endStyle()
                        }

                        Level.INFO -> {
                            color("cyan")
                            append("INFO ")
                            endStyle()
                        }

                        else -> append(level)
                    }

                    append(" [$logger] ")
                    append(message)
                }
            )
        )
    }
}

val stream = createListOutputStream { logHolder }
val commandStream = createListOutputStream { commandLogHolder }

fun createListOutputStream(list: () -> ListContainmentElement): java.io.OutputStream =
    object : java.io.OutputStream() {

        private val buffer = StringBuilder()

        override fun write(b: Int) {
            appendBytes(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            appendBytes(b.copyOfRange(off, off + len))
        }

        private fun appendBytes(bytes: ByteArray) {
            val text = bytes.toString(Charsets.UTF_8)

            buffer.append(text)

            while (true) {
                val newline = buffer.indexOf("\n")
                if (newline < 0) break

                val line = buffer
                    .substring(0, newline)
                    .removeSuffix("\r")

                buffer.delete(0, newline + 1)

                list.invoke().add(
                    TextElement(line, fillMaxWidth = false)
                )
            }
        }

        override fun flush() {
            flushBuffer()
        }

        private fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                list.invoke().add(
                    TextElement(buffer.toString())
                )
                buffer.clear()
            }
        }
    }

fun initInterceptor() {
    System.setOut(
        PrintStream(stream, true, Charsets.UTF_8)
    )
    System.setErr(
        PrintStream(stream, true, Charsets.UTF_8)
    )
}
val commandPrintStream: PrintStream = PrintStream(commandStream, true, Charsets.UTF_8)

class UIOutputWriter(
    private val logHolder: ListContainmentElement
) : Writer() {

    private val buffer = StringBuilder()

    override fun write(
        cbuf: CharArray,
        off: Int,
        len: Int
    ) {
        buffer.appendRange(cbuf, off, off + len)

        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) break

            val line = buffer.substring(0, newline)
                .removeSuffix("\r")

            buffer.delete(0, newline + 1)

            if (line.isNotEmpty()) {
                logHolder.add(
                    TextElement(line, fillMaxWidth = false)
                )
            } else {
                logHolder.add(
                    TextElement("")
                )
            }
        }
    }

    override fun flush() {
        flushBuffer()
    }

    override fun close() {
        flushBuffer()
    }

    private fun flushBuffer() {
        if (buffer.isNotEmpty()) {
            logHolder.add(
                TextElement(buffer.toString())
            )
            buffer.clear()
        }
    }
}

fun eitherIsNull(vararg items: Any?) =
    items.any { it == null }

fun whichAreNull(vararg items: Pair<String, Any?>): List<String> =
    items.filter { it.second == null }
        .map { it.first }

enum class DefaultServices(val id: String) {
    EMAIL_SERVICE("system_email_service")
}

fun BigDecimal.toKmp(): com.ionspin.kotlin.bignum.decimal.BigDecimal =
    com.ionspin.kotlin.bignum.decimal.BigDecimal.fromByte(this.toByte())

fun LocalDateTime.toKmp(): kotlinx.datetime.LocalDateTime =
    kotlinx.datetime.LocalDateTime(this.year, this.monthValue, this.dayOfMonth, this.hour, this.minute, this.second, this.nano)

fun kotlinx.datetime.LocalDateTime.Companion.now(): kotlinx.datetime.LocalDateTime {
    val currentInstant = Clock.System.now()
    return currentInstant.toLocalDateTime(TimeZone.currentSystemDefault())
}

fun String.ellipsize(maxLength: Int): String {
    // If it's already short enough, return it as-is
    if (this.length <= maxLength) return this

    // Safety check in case a very small maxLength (like 1 or 2) is passed
    if (maxLength <= 3) return this.take(maxLength)

    // Take the allowed characters minus 3 (for the "...") and append the ellipses
    return this.take(maxLength - 3) + "..."
}