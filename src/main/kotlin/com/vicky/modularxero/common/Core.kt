package com.vicky.modularxero.common

import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.Display
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Horizontal/vertical insets in terminal columns/rows. */
data class Insets(
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
) {
    init { require(top >= 0 && right >= 0 && bottom >= 0 && left >= 0) }
}

enum class HorizontalAlignment { LEFT, CENTER, RIGHT }
enum class HorizontalArrangement { START, CENTER, END }
enum class VerticalAlignment { TOP, CENTER, BOTTOM }

/** Rendering information supplied by the UI/layout engine. */
data class RenderContext(
    val terminal: Terminal,
    val terminalWidth: Int,
    val terminalHeight: Int,
    val availableWidth: Int = terminalWidth,
    val availableHeight: Int = terminalHeight,
    val currentWidth: Int = terminalWidth,
    val composeWidth: Boolean = true
) {
    fun child(width: Int = availableWidth, height: Int = availableHeight, currentWidth: Int = availableWidth, composeWidth: Boolean = true): RenderContext =
        copy(availableWidth = width.coerceAtLeast(-1), composeWidth = composeWidth,
            currentWidth = currentWidth.coerceAtLeast(-1), availableHeight = height.coerceAtLeast(0))
}

data class ElementSnapshot(
    val element: TerminalElement,
    val startRow: Int,
    val height: Int,
    val width: Int,
)

/**
 * Base class for every terminal element.
 *
 * The class owns lifecycle/state and parent relationships; subclasses normally only need
 * to implement [render]. They must never write directly to the terminal.
 */
abstract class TerminalElement {
    private var owner: ElementContainer? = null
    private var visibleState: Boolean = true
    private var clearedState: Boolean = false

    /** Whether this element contributes output/layout right now. */
    val isVisible: Boolean get() = visibleState && !clearedState

    /** Whether this element has been removed from its parent. */
    val isCleared: Boolean get() = clearedState

    internal fun attach(parent: ElementContainer) {
        check(owner == null || owner === parent) { "Element is already attached to another container" }
        check(!clearedState) { "A cleared element cannot be re-attached" }
        owner = parent
    }

    internal fun detach() { owner = null }

    /** Render without width-specific logic. Custom elements should normally override this. */
    open fun render(): List<String> = emptyList()

    /** Render using the current layout context. Most elements can inherit the default. */
    open fun render(context: RenderContext): List<String> = render()

    /** Hide without changing the element's logical position. */
    fun hide(): TerminalElement = apply { visibleState = false; requestRender() }

    /** Show at the element's current logical position. */
    fun show(): TerminalElement = apply {
        check(!clearedState) { "Cannot show a cleared element" }
        visibleState = true
        requestRender()
    }

    /** Remove from the current parent and invalidate this element permanently. */
    fun clear() {
        if (clearedState) return
        clearedState = true
        owner?.remove(this)
        owner = null
    }

    /** Ask the parent to place this element at an explicit list index. */
    fun requestOrder(index: Int) {
        owner?.requestOrder(this, index) ?: throw IllegalStateException("Element is not attached")
    }

    /** Trigger a redraw after changing element state. */
    protected fun requestRender() { owner?.invalidate() }

    /** Notify the parent that a mutable element has changed. */
    protected fun changed() { requestRender() }

    internal fun parent(): ElementContainer? = owner
}

/** Internal container contract shared by TerminalUI and composite elements such as Row/Block. */
interface ElementContainer {
    fun remove(element: TerminalElement)
    fun requestOrder(element: TerminalElement, index: Int)
    fun invalidate()
}

class TerminalUI private constructor(
    val terminal: Terminal,
    private val display: Display,
) : ElementContainer, AutoCloseable
{
    private val elements = mutableListOf<TerminalElement>()
    private val lock = Any()
    private var closed = false
    private var lastSnapshots: List<ElementSnapshot> = emptyList()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "tui-render-loop").apply { isDaemon = true }
    }

    fun start() {
        // Run at 20 FPS (every 50ms)
        scheduler.scheduleAtFixedRate({
            render()
        }, 0, 50, TimeUnit.MILLISECONDS)
    }

    init {
        terminal.handle(Terminal.Signal.WINCH) { _ ->
            invalidate()
        }
        // start()
    }

    companion object {
        @JvmStatic
        fun open(): TerminalUI {
            val terminal = TerminalBuilder.builder()
                // .provider("jni")
                .dumb(false)
                .system(true)
                .build()

            return TerminalUI(terminal, Display(terminal, true))
        }

        fun attach(terminal: Terminal): TerminalUI = TerminalUI(terminal, Display(terminal, true))
    }

    /** Add an element at the end of the logical render stack. */
    /** Add a normal mutable text element and return its live reference. */
    fun text(value: String): TextElement = add(TextElement(value))

    /** Add an immutable element backed by one or more already-rendered lines. */
    fun static(value: String): StaticElement = add(StaticElement(value))

    fun <T : TerminalElement> add(element: T): T = synchronized(lock) {
        ensureOpen()
        check(element.parent() == null) { "Element already has a parent" }
        check(!element.isCleared) { "A cleared element cannot be added" }
        elements += element
        element.attach(this)
        renderLocked()
        element
    }

    /** Immutable view of the logical stack, including hidden elements. */
    fun elements(): List<TerminalElement> = synchronized(lock) { elements.toList() }

    /** Current snapshots of only visible elements after layout. */
    fun snapshots(): List<ElementSnapshot> = synchronized(lock) { lastSnapshots.toList() }

    /** Remove every element from the logical stack and render the now-empty UI. */
    fun clearAll() = synchronized(lock) {
        ensureOpen()
        elements.forEach(TerminalElement::detach)
        elements.clear()
        lastSnapshots = emptyList()
        renderLocked()
    }

    /**
     * Finalize the current inline display phase.
     *
     * This is useful immediately before handing the terminal to another component (for example
     * JLine's LineReader). The current display is rendered, then JLine's diff model is reset so
     * subsequent output is treated as a new terminal phase.
     */
    fun commit() = synchronized(lock) {
        ensureOpen()
        renderLocked()
        display.reset()
    }

    /** Force layout/render without changing state. */
    fun render() = synchronized(lock) { ensureOpen(); renderLocked() }

    /** Emit the current UI and leave JLine's display model coherent. */
    fun flush() = synchronized(lock) { ensureOpen(); terminal.flush() }

    override fun remove(element: TerminalElement) = synchronized(lock) {
        if (!elements.remove(element)) return
        element.detach()
        renderLocked()
    }

    override fun requestOrder(element: TerminalElement, index: Int) = synchronized(lock) {
        val oldIndex = elements.indexOf(element)
        require(oldIndex >= 0) { "Element is not attached to this TerminalUI" }
        require(index in 0..elements.lastIndex) { "index=$index outside 0..${elements.lastIndex}" }
        elements.removeAt(oldIndex)
        val insertion = index.coerceIn(0, elements.size)
        elements.add(insertion, element)
        renderLocked()
    }

    override fun invalidate() = synchronized(lock) {
        if (!closed) renderLocked()
    }

    private fun renderLocked() {
        ensureOpen()
        val width = terminal.width.coerceAtLeast(1)
        val height = terminal.height.coerceAtLeast(1)
        val context = RenderContext(terminal, width, height, width, height)
        val newLines = ArrayList<String>()
        val snapshots = ArrayList<ElementSnapshot>()

        for (element in elements) {
            if (!element.isVisible) continue

            val start = newLines.size

            val rendered = safeRender(
                element,
                context.child(width, height - newLines.size)
            )

            newLines.addAll(rendered)

            val renderedWidth =
                rendered.maxOfOrNull {
                    TerminalMetrics.width(terminal, it)
                } ?: 0

            snapshots += ElementSnapshot(
                element,
                start,
                rendered.size,
                renderedWidth
            )
        }

        // Display performs ANSI-aware diffing and terminal cursor management.
        display.clear()
        display.resize(height, width)
        display.updateAnsi(newLines, -1)
        // terminal.writer().print("\b\u001b[K")
        lastSnapshots = snapshots
    }

    private fun safeRender(element: TerminalElement, context: RenderContext): List<String> =
        element.render(context).map { it.replace("\r", "") }

    private fun ensureOpen() { check(!closed) { "TerminalUI is closed" } }

    override fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        display.reset()
        terminal.flush()
        terminal.close()
    }
}

private fun splitPreserveTrailing(value: String): List<String> {
    val parts = value.split('\n').toMutableList()
    if (value.endsWith('\n')) parts += ""
    return parts
}

object TerminalMetrics {
    fun width(terminal: Terminal, ansi: String): Int =
        AttributedString.fromAnsi(ansi, terminal).columnLength(terminal)

    fun width(lines: List<String>, terminal: Terminal): Int = lines.maxOfOrNull { width(terminal, it) } ?: 0

    fun padRight(terminal: Terminal, ansi: String, targetWidth: Int): String {
        val missing = targetWidth - width(terminal, ansi)
        return if (missing > 0) ansi + " ".repeat(missing) else ansi
    }

    fun align(terminal: Terminal, ansi: String, targetWidth: Int, alignment: HorizontalAlignment): String {
        val missing = (targetWidth - width(terminal, ansi)).coerceAtLeast(0)
        return when (alignment) {
            HorizontalAlignment.LEFT -> ansi + " ".repeat(missing)
            HorizontalAlignment.RIGHT -> " ".repeat(missing) + ansi
            HorizontalAlignment.CENTER -> {
                val left = missing / 2
                " ".repeat(left) + ansi + " ".repeat(missing - left)
            }
        }
    }

    fun wrap(terminal: Terminal, ansi: String, maxWidth: Int): List<String> {
        require(maxWidth >= 1)
        val source = splitPreserveTrailing(ansi)
        return source.flatMap { line ->
            val attributed = AttributedString.fromAnsi(line, terminal)
            if (attributed.columnLength(terminal) <= maxWidth) {
                listOf(line)
            } else {
                attributed.columnSplitLength(terminal, maxWidth, false, true).map { it.toAnsi(terminal) }
            }
        }
    }
}
