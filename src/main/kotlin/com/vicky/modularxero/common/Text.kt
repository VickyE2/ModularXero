package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private fun splitPreserveTrailingText(value: String): List<String> {
    val parts = value.split('\n').toMutableList()
    if (value.endsWith('\n')) parts += ""
    return parts
}

/**
 * Small builder that deliberately delegates final ANSI generation to the existing ANSIColor class.
 * This lets the terminal UI add composition without replacing the project's colour system.
 */
class AnsiTextBuilder {

    private data class Run(
        val text: String,
        val styles: List<String>
    )

    private val runs = mutableListOf<Run>()
    private val styles = java.util.ArrayDeque<String>()

    fun append(text: String): AnsiTextBuilder {
        if (text.isNotEmpty()) {
            runs += Run(text, styles.toList())
        }
        return this
    }

    fun space(count: Int = 1): AnsiTextBuilder =
        append(" ".repeat(count.coerceAtLeast(0)))

    /**
     * Could be a named or hex color
     */
    fun color(name: String): AnsiTextBuilder =
        apply { styles.addLast(name) }

    fun gradient(
        hex: List<String>,
        angle: Int
    ): AnsiTextBuilder =
        apply {
            styles.addLast(
                "gradient-${angle}deg-${hex.joinToString("-")}"
            )
        }

    fun style(name: String): AnsiTextBuilder = color(name)

    fun bold(): AnsiTextBuilder = color("bold")
    fun italic(): AnsiTextBuilder = color("italic")
    fun underline(): AnsiTextBuilder = color("underline")
    fun strikethrough(): AnsiTextBuilder = color("strikethrough")

    fun endStyle(): AnsiTextBuilder =
        apply {
            if (styles.isNotEmpty()) {
                styles.removeLast()
            }
        }

    fun reset(): AnsiTextBuilder =
        apply {
            styles.clear()
        }

    fun build(): String =
        runs.joinToString("") { run ->
            var decorated = run.text

            for (style in run.styles.asReversed()) {
                decorated = "$style[$decorated]"
            }

            if (decorated.contains("gradient"))
                ANSIColor.colorizeMixed(decorated)
            else ANSIColor.colorize(decorated)
        }
}

fun ansiText(block: AnsiTextBuilder.() -> Unit): String = AnsiTextBuilder().apply(block).build()

/** Base mutable text element. */
open class TextElement @JvmOverloads constructor(text: String = "", private val fillMaxWidth: Boolean = true) : TerminalElement() {
    private var value = text
    private var alignment = HorizontalAlignment.LEFT

    constructor(text: String = "", fillMaxWidth: Boolean = true, alignment: HorizontalAlignment): this(text, fillMaxWidth) {
        this.alignment = alignment
    }

    fun text(value: String) = apply { this.value = value; changed() }
    fun alignment(value: HorizontalAlignment) = apply { alignment = value; changed() }
    fun update(value: String) = text(value)

    override fun render(context: RenderContext): List<String> =
        splitPreserveTrailingText(value).map { TerminalMetrics.align(context.terminal, it,
            if(fillMaxWidth && context.composeWidth) context.currentWidth else 0, alignment) }
}

/** Base supplier based constantly checking text element. */
open class UpdatingTextElement @JvmOverloads constructor(
    val supplier: () -> String = { "" },
    private val fillMaxWidth: Boolean = true,
    private val intervalMillis: Long = 100L,
) : TerminalElement() {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "terminal-text-updating").apply { isDaemon = true }
    }
    private var future: ScheduledFuture<*>? = null
    private var value = supplier()
    private var alignment = HorizontalAlignment.LEFT

    init {
        require(intervalMillis > 20)
        start()
    }

    constructor(text: () -> String = { "" }, fillMaxWidth: Boolean = true, alignment: HorizontalAlignment): this(text, fillMaxWidth) {
        this.alignment = alignment
    }

    fun start(): UpdatingTextElement {
        synchronized(this) {
            future = executor.scheduleAtFixedRate({
                synchronized(this) {
                    val gotten = supplier()
                    if (gotten != value) {
                        value = gotten
                        changed()
                    }
                }
            }, 20, intervalMillis, TimeUnit.MILLISECONDS)
        }
        return this
    }

    fun alignment(value: HorizontalAlignment) = apply { alignment = value; changed() }

    override fun render(context: RenderContext): List<String> =
        splitPreserveTrailingText(value).map { TerminalMetrics.align(context.terminal, it,
            if(fillMaxWidth && context.composeWidth) context.currentWidth else 0, alignment) }
}

class StaticElement(private val lines: List<String>) : TerminalElement() {
    constructor(text: String) : this(splitPreserveTrailingText(text))

    override fun render(): List<String> = lines.toList()

    /** Static elements intentionally expose no update API. */
}
