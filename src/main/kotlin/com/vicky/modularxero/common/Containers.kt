package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor
import com.vicky.modularxero.common.TerminalMetrics.width
import org.jline.terminal.Terminal
import kotlin.collections.plusAssign
import kotlin.math.round
import kotlin.math.roundToInt

class RowElement @JvmOverloads constructor(
    children: List<WeightedElement> = emptyList(),
    private val gap: Int = 0,
) : TerminalElement(), ElementContainer {
    private data class Child(val element: TerminalElement, var weight: Double)

    private val children = children.mapTo(mutableListOf()) { Child(it.element, it.weight) }

    init {
        require(gap >= 0)
        children.forEach {
            require(it.weight > 0.0) { "weight must be > 0" }
            check(it.element.parent() == null) { "Element already has a parent" }
            it.element.attach(this)
        }
    }

    fun <T : TerminalElement> add(element: T, weight: Double = 1.0): T {
        require(weight > 0.0) { "weight must be > 0" }
        check(element.parent() == null) { "Element already has a parent" }
        element.attach(this)
        children += Child(element, weight)
        changed()
        return element
    }

    fun weight(element: TerminalElement, weight: Double): RowElement = apply {
        require(weight > 0.0)
        children.first { it.element === element }.weight = weight
        changed()
    }

    override fun render(): List<String> =
        children.flatMap { if (it.element.isVisible) it.element.render() else emptyList() }

    override fun render(context: RenderContext): List<String> {
        val visible = children.filter { it.element.isVisible }
        if (visible.isEmpty() || context.availableWidth <= 0) return emptyList()

        val totalGap = gap * (visible.size - 1)
        val usable = (context.availableWidth - totalGap).coerceAtLeast(0)
        val widths = allocateWeights(visible.map { it.weight }, usable)
        val childLines = visible.mapIndexed { index, child ->
            child.element.render(context.child(widths[index], context.availableHeight))
                .ifEmpty { listOf("") }
        }
        val height = childLines.maxOfOrNull { it.size } ?: 0
        return List(height) { row ->
            val line = StringBuilder()
            childLines.forEachIndexed { index, lines ->
                if (index > 0) line.append(" ".repeat(gap))
                line.append(
                    TerminalMetrics.align(
                        context.terminal,
                        lines.getOrElse(row) { "" },
                        widths[index],
                        HorizontalAlignment.LEFT
                    )
                )
            }
            line.toString()
        }
    }

    override fun remove(element: TerminalElement) {
        if (children.removeIf { it.element === element }) {
            element.detach(); changed()
        }
    }

    override fun requestOrder(element: TerminalElement, index: Int) {
        val old = children.indexOfFirst { it.element === element }
        require(old >= 0)
        require(index in 0..children.lastIndex)
        val child = children.removeAt(old)
        children.add(index.coerceIn(0, children.size), child)
        changed()
    }

    override fun invalidate() = changed()

    private fun allocateWeights(weights: List<Double>, total: Int): IntArray {
        if (weights.isEmpty()) return IntArray(0)
        if (total <= 0) return IntArray(weights.size)
        val sum = weights.sum()
        val exact = weights.map { total.toDouble() * it / sum }
        val result = exact.map { it.toInt() }.toIntArray()
        var remaining = total - result.sum()
        val order = exact.indices.sortedByDescending { exact[it] - result[it] }
        var i = 0
        while (remaining > 0) {
            result[order[i++ % order.size]]++; remaining--
        }
        return result
    }
}

data class WeightedElement @JvmOverloads constructor(val element: TerminalElement, val weight: Double = 1.0)

interface ArrangeableContainer {
    fun arrange(
        terminal: Terminal,
        lines: List<String>,
        availableWidth: Int,
        arrangement: HorizontalArrangement
    ): List<String> {
        return lines.map { line ->
            val width = width(terminal, line)
            val missing = (availableWidth - width).coerceAtLeast(0)

            when (arrangement) {
                HorizontalArrangement.START ->
                    line + " ".repeat(missing)

                HorizontalArrangement.CENTER -> {
                    val left = missing / 2
                    " ".repeat(left) + line + " ".repeat(missing - left)
                }

                HorizontalArrangement.END ->
                    " ".repeat(missing) + line
            }
        }
    }

    fun withMargins(margin: Insets, lines: List<String>): List<String> {
        val output = ArrayList<String>(lines.size + margin.top + margin.bottom)
        repeat(margin.top) { output += "" }
        output += lines.map { " ".repeat(margin.left) + it + " ".repeat(margin.right) }
        repeat(margin.bottom) { output += "" }
        return output
    }
}

class BlockElement private constructor(
    private val rounded: Boolean,
    var title: TerminalElement? = null,
    var borderColor: ColorProvider = StaticColorProvider(ANSIColor.DARK_GRAY),
    var borderThickness: Int = 1,
    var padding: Insets = Insets(0, 1, 0, 1),
    var margin: Insets = Insets(),
    var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    var horizontalArrangement: HorizontalArrangement = HorizontalArrangement.START,
) : TerminalElement(), ElementContainer, ArrangeableContainer {
    private val children = mutableListOf<TerminalElement>()
    var fillMaxWidth: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        title?.attach(this)
    }

    fun title(value: String): BlockElement = apply {
        title = TextElement(value, fillMaxWidth = false)
        changed()
    }

    fun title(element: TerminalElement): BlockElement = apply {
        check(element.parent() == null) { "Element already has a parent" }
        element.attach(this)
        title = element
        changed()
    }

    fun clearTitle(): BlockElement = apply {
        title?.detach()
        title = null
        changed()
    }

    companion object {
        fun rounded(title: TerminalElement? = null) = BlockElement(true, title = title)
        fun square(title: TerminalElement? = null) = BlockElement(false, title = title)
    }

    fun add(element: TerminalElement): BlockElement = apply {
        check(element.parent() == null) { "Element already has a parent" }
        element.attach(this)
        children += element
        changed()
    }

    fun clearChildren(): BlockElement = apply {
        children.forEach(TerminalElement::detach)
        children.clear()
        changed()
    }

    override fun render(): List<String> = listOf("")

    override fun render(context: RenderContext): List<String> {
        val thickness = borderThickness.coerceIn(1, 20)
        val outerWidth = context.availableWidth
        val innerWidth = (
                outerWidth - margin.left - margin.right - thickness * 2 - padding.left - padding.right
                ).coerceAtLeast(1)

        val contentMaxLength = if (!fillMaxWidth) children.filter { it.isVisible }
            .flatMap { it.render(context.child(innerWidth, context.availableHeight, composeWidth = false)) }
            .maxOfOrNull {
                // println(
                //     "[$it] " + "visibleLength=${it.visibleLength()} " + "width=${width(context.terminal, it)}"
                // )
                width(context.terminal, it)
            } ?: 0 else context.availableWidth
        // println("Content max length is: $contentMaxLength")
        val childContext = context.child(innerWidth, context.availableHeight, contentMaxLength, true)
        val contentLines = children
            .filter { it.isVisible }
            .flatMap { it.render(childContext) }
            .ifEmpty { listOf("") }

        return arrange(
            context.terminal,
            withMargins(
                margin,
                buildBordered(
                    terminal = context.terminal,
                    lines = contentLines,
                    maxContentWidth = contentMaxLength,
                    thickness = thickness,
                    context = context
                )
            ),
            context.availableWidth,
            horizontalArrangement
        )
    }

    private fun buildBordered(
        terminal: Terminal,
        lines: List<String>,
        maxContentWidth: Int,
        thickness: Int,
        context: RenderContext
    ): List<String> {
        val chars = if (rounded) {
            BorderChars('╭', '╮', '╰', '╯', '─', '│')
        } else {
            BorderChars('┌', '┐', '└', '┘', '─', '│')
        }

        // 1. Calculate natural content requirements
        val titleLines = title
            ?.render(
                context.child(
                    maxContentWidth,
                    1,
                    composeWidth = false
                )
            )
            ?.takeIf { it.isNotEmpty() }

        val titleLine = titleLines?.firstOrNull()?.let { " $it " }
        val titleWidth = titleLine?.let {
            width(terminal, it)
        } ?: 0

        // 2. Resolve target innerWidth based on mode
        val borderOverhead = thickness * 2
        val paddingOverhead = padding.left + padding.right
        val termWidth = terminal.columns

        val resolvedInnerWidth = if (fillMaxWidth) {
            // Stretch to fill terminal width (minus borders and padding)
            (termWidth - borderOverhead - paddingOverhead).coerceAtLeast(maxContentWidth)
        } else {
            // Shrink-wrap to fit the content or title (min width)
            maxOf(maxContentWidth, titleWidth - paddingOverhead)
        }

        val contentWidth = resolvedInnerWidth + padding.right + padding.left
        val horizontal = chars.horizontal.toString().repeat(contentWidth)
        val leftVertical = chars.vertical.toString().repeat(thickness)
        val rightVertical = leftVertical
        val leftTop = chars.topLeft.toString().repeat(thickness)
        val rightTop = chars.topRight.toString().repeat(thickness)
        val leftBottom = chars.bottomLeft.toString().repeat(thickness)
        val rightBottom = chars.bottomRight.toString().repeat(thickness)

        fun borderLine(left: String, right: String, isTop: Boolean): String =
            borderColor.applyToString(if (isTop) 0 else lines.size + 1, left + horizontal + right)

        // Build top border, embedding the title snugly if present
        val top = if (titleLine != null) {
            val leftPadCount = 1
            val maxTitleWidth = (contentWidth - leftPadCount).coerceAtLeast(0)

            if (titleWidth <= maxTitleWidth) {
                val rightPadCount = (contentWidth - leftPadCount - titleWidth).coerceAtLeast(0)
                val lineContent = chars.horizontal.toString().repeat(leftPadCount) +
                        "∯" +
                        titleLine +
                        "∯" +
                        chars.horizontal.toString().repeat(rightPadCount)
                borderColor.applyToString(0, leftTop + lineContent + rightTop)
            } else {
                borderLine(leftTop, rightTop, true)
            }
        } else {
            borderLine(leftTop, rightTop, true)
        }

        val bottom = borderLine(leftBottom, rightBottom, false)

        val body = lines.flatMap { TerminalMetrics.wrap(terminal, it, resolvedInnerWidth) }.mapIndexed { index, line ->
            val centered = TerminalMetrics.align(terminal, line, resolvedInnerWidth, horizontalAlignment)
            val padded = " ".repeat(padding.left) + centered + " ".repeat(padding.right)

            // 1. Calculate the total visual width to compute accurate normalized positions
            val totalWidth = (thickness * 2) + contentWidth
            val widthF = (totalWidth - 1).coerceAtLeast(1).toFloat()
            val lineNumber = index + 1

            // 2. Render left border at its true relative position (starts at 0)
            val leftColored = buildString {
                leftVertical.forEachIndexed { i, char ->
                    val normalizedX = i / widthF
                    append(borderColor.get(normalizedX, lineNumber))
                    append(char)
                }
                append("\u001B[0m") // Reset ANSI
            }

            // 3. Render right border using its absolute X coordinate
            val rightStartX = thickness + contentWidth
            val rightColored = buildString {
                rightVertical.forEachIndexed { i, char ->
                    val normalizedX = (rightStartX + i) / widthF
                    append(borderColor.get(normalizedX, lineNumber))
                    append(char)
                }
                append("\u001B[0m") // Reset ANSI
            }

            leftColored + padded + rightColored
        }

        return buildList {
            repeat(thickness) { i ->
                if (i == 0) add(top) else add(borderLine(leftTop, rightTop, true))
            }
            addAll(body)
            repeat(thickness) { add(bottom) }
        }
    }

    override fun remove(element: TerminalElement) {
        if (children.remove(element)) {
            element.detach()
            changed()
        }
    }

    override fun requestOrder(element: TerminalElement, index: Int) {
        val old = children.indexOf(element)
        require(old >= 0)
        require(index in 0..children.lastIndex)
        val child = children.removeAt(old)
        children.add(index.coerceIn(0, children.size), child)
        changed()
    }

    override fun invalidate() = changed()

    private data class BorderChars(
        val topLeft: Char,
        val topRight: Char,
        val bottomLeft: Char,
        val bottomRight: Char,
        val horizontal: Char,
        val vertical: Char,
    )
}

open class SizedElement<E : TerminalElement>(
    var padding: Insets = Insets(0, 1, 0, 1),
    var margin: Insets = Insets(),
    var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    var horizontalArrangement: HorizontalArrangement = HorizontalArrangement.START,
    var widthPercentage: Float = 1.0f,
) : TerminalElement(), ElementContainer, ArrangeableContainer
{
    protected var children = mutableListOf<TerminalElement>()

    open fun add(element: E): SizedElement<E> = apply {
        check(element.parent() == null) { "Element already has a parent" }
        element.attach(this)
        children += element
        changed()
    }

    override fun remove(element: TerminalElement) {
        if (children.remove(element)) {
            element.detach()
            changed()
        }
    }

    fun clearChildren(): SizedElement<E> = apply {
        children.forEach(TerminalElement::detach)
        children.clear()
        changed()
    }

    override fun render(): List<String> = listOf("")

    override fun render(context: RenderContext): List<String> {
        val outerWidth = context.availableWidth
        val innerWidth = (
                outerWidth - margin.left - margin.right - padding.left - padding.right
                ).coerceAtLeast(1)

        val childContext = context.child(round(innerWidth * widthPercentage).toInt(), context.availableHeight)
        val contentLines = children
            .filter { it.isVisible }
            .flatMap { it.render(childContext) }
            .ifEmpty { listOf("") }

        return arrange(
            context.terminal,
            withMargins(
                margin,
                finalle(
                    terminal = context.terminal,
                    lines = contentLines,
                    innerWidth = round(innerWidth * widthPercentage).toInt()
                )
            ),
            context.availableWidth,
            horizontalArrangement
        )
    }

    open protected fun finalle(
        terminal: Terminal,
        lines: List<String>,
        innerWidth: Int
    ): List<String> {
        val body = lines.flatMap { TerminalMetrics.wrap(terminal, it.trim(), innerWidth) }.map { line ->
            val centered = TerminalMetrics.align(terminal, line, innerWidth, horizontalAlignment)
            val padded = " ".repeat(padding.left) + centered + " ".repeat(padding.right)
            padded
        }

        return buildList {
            addAll(body)
        }
    }

    override fun requestOrder(element: TerminalElement, index: Int) {
        val old = children.indexOf(element)
        require(old >= 0)
        require(index in 0..children.lastIndex)
        val child = children.removeAt(old)
        children.add(index.coerceIn(0, children.size), child)
        changed()
    }

    override fun invalidate() = changed()
}

class ListContainmentElement @JvmOverloads constructor(
    val maxChildrenSize: Int,
    padding: Insets = Insets(0, 1, 0, 1),
    margin: Insets = Insets(),
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    horizontalArrangement: HorizontalArrangement = HorizontalArrangement.START,
): SizedElement<TextElement>(padding, margin, horizontalAlignment, horizontalArrangement, 1.0f) {

    override fun add(element: TextElement): ListContainmentElement = apply {
        check(element.parent() == null) { "Element already has a parent" }
        element.attach(this)
        if (children.size == maxChildrenSize) {
            children.removeFirst().detach()
        }
        children += element
        changed()
    }

    override fun remove(element: TerminalElement) {
        throw UnsupportedOperationException("Cannot remove from a list containment element")
    }

    override fun finalle(
        terminal: Terminal,
        lines: List<String>,
        innerWidth: Int
    ): List<String> {
        // 1. Wrap and process all lines, then take only up to maxChildrenSize lines to enforce the limit
        val body = lines.flatMap { TerminalMetrics.wrap(terminal, it.trim(), innerWidth) }
            .take(maxChildrenSize)
            .map { line ->
                val centered = TerminalMetrics.align(terminal, line, innerWidth, horizontalAlignment)
                val padded = " ".repeat(padding.left) + centered + " ".repeat(padding.right)
                padded
            }
        // $cp = (./gradlew -q cp).Trim(); java --enable-native-access=ALL-UNNAMED -cp "$cp" com.vicky.modularxero.Startup
        // 2. Fill the remaining space with empty lines if body size is less than maxChildrenSize
        val missingLines = maxChildrenSize - body.size
        return buildList {
            if (missingLines > 0) {
                repeat(missingLines) {
                    add("")
                }
            }
            addAll(body)
        }
    }
}