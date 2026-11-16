package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor

class TableElement(
    headers: List<String>,
    rows: List<List<String>> = emptyList(),
    weights: List<Double>? = null,
    var dividerColor: String = ANSIColor.DARK_GRAY,
    var headerColor: String = ANSIColor.WHITE_BOLD,
    private val cellPadding: Int = 1,
) : TerminalElement() {
    private val headerData = headers.toList()
    private val rowData = rows.map { it.toList() }.toMutableList()
    private var columnWeights = weights?.toList()

    init {
        require(headerData.isNotEmpty()) { "Table needs at least one column" }
        require(cellPadding >= 0)
        columnWeights?.let {
            require(it.size == headerData.size)
            require(it.all { weight -> weight > 0.0 })
        }
    }

    fun addRow(vararg cells: String): TableElement = apply {
        require(cells.size == headerData.size) { "Expected ${headerData.size} cells" }
        rowData += cells.toList(); changed()
    }

    fun setWeights(weights: List<Double>): TableElement = apply {
        require(weights.size == headerData.size)
        require(weights.all { it > 0.0 })
        columnWeights = weights
        changed()
    }

    data class TableBoxSet(
        val topLeft: Char = '╭', val topMid: Char = '┬', val topRight: Char = '╮',
        val midLeft: Char = '├', val midMid: Char = '┼', val midRight: Char = '┤',
        val bottomLeft: Char = '╰', val bottomMid: Char = '┴', val bottomRight: Char = '╯',
        val horizontal: Char = '─', val vertical: Char = '│'
    )

    override fun render(context: RenderContext): List<String> {
        val columns = headerData.size
        if (columns == 0) return emptyList()

        val box = TableBoxSet()
        val totalPadding = columns * (cellPadding * 2)
        val dividerWidth = columns + 1
        val maxAvailable = (context.availableWidth - totalPadding - dividerWidth).coerceAtLeast(columns)

        // Calculate smart widths (shrink-wrap small columns, scale large ones)
        val widths = allocateSmartWidths(context, maxAvailable, columns)

        return buildList {
            // 1. Top border
            add(buildRule(box.topLeft, box.topMid, box.topRight, box.horizontal, widths))

            // 2. Header row
            val wrappedHeader = headerData.mapIndexed { col, cell ->
                TerminalMetrics.wrap(context.terminal, cell, widths[col])
            }
            val headerHeight = wrappedHeader.maxOfOrNull { it.size } ?: 1
            repeat(headerHeight) { lineIdx ->
                add(buildDataLine(context, wrappedHeader, lineIdx, widths, isHeader = true, box = box))
            }

            // 3. Header separator
            add(buildRule(box.midLeft, box.midMid, box.midRight, box.horizontal, widths))

            // 4. Data rows
            rowData.forEachIndexed { rowIndex, row ->
                val wrappedRow = (0 until columns).map { col ->
                    val cell = row.getOrElse(col) { "" }
                    TerminalMetrics.wrap(context.terminal, cell, widths[col])
                }
                val rowHeight = wrappedRow.maxOfOrNull { it.size } ?: 1
                repeat(rowHeight) { lineIdx ->
                    add(buildDataLine(context, wrappedRow, lineIdx, widths, isHeader = false, box = box))
                }

                // Optional subtle row separator (uncomment if you prefer distinct row borders):
                // if (rowIndex < rowData.lastIndex) {
                //     add(buildRule(box.midLeft, box.midMid, box.midRight, box.horizontal, widths))
                // }
            }

            // 5. Bottom border
            add(buildRule(box.bottomLeft, box.bottomMid, box.bottomRight, box.horizontal, widths))
        }
    }

    private fun buildDataLine(
        context: RenderContext,
        wrappedCells: List<List<String>>,
        lineIndex: Int,
        widths: IntArray,
        isHeader: Boolean,
        box: TableBoxSet
    ): String = buildString {
        append(ansiColorize(dividerColor, box.vertical.toString()))
        for (col in wrappedCells.indices) {
            append(" ".repeat(cellPadding))
            val rawText = wrappedCells[col].getOrElse(lineIndex) { "" }
            val styledText = if (isHeader) ansiColorize(headerColor, rawText) else rawText

            append(TerminalMetrics.padRight(context.terminal, styledText, widths[col]))
            append(" ".repeat(cellPadding))
            append(ansiColorize(dividerColor, box.vertical.toString()))
        }
    }

    private fun buildRule(
        left: Char,
        mid: Char,
        right: Char,
        horizontal: Char,
        widths: IntArray
    ): String = buildString {
        append(ansiColorize(dividerColor, left.toString()))
        widths.forEachIndexed { i, w ->
            val bar = horizontal.toString().repeat(w + cellPadding * 2)
            append(ansiColorize(dividerColor, bar))
            val sep = if (i == widths.lastIndex) right else mid
            append(ansiColorize(dividerColor, sep.toString()))
        }
    }

    private fun allocateSmartWidths(context: RenderContext, available: Int, count: Int): IntArray {
        val allRows = listOf(headerData) + rowData

        // Find the maximum raw width required by content in each column
        val contentWidths = IntArray(count) { col ->
            allRows.maxOfOrNull { row ->
                val text = row.getOrElse(col) { "" }
                TerminalMetrics.width(context.terminal, text)
            }?.coerceAtLeast(1) ?: 1
        }

        val totalNeeded = contentWidths.sum()

        // If all content fits comfortably without wrapping, shrink-wrap to content width
        if (totalNeeded <= available && columnWeights == null) {
            return contentWidths
        }

        // Otherwise, fall back to proportional distribution based on weights or required sizes
        val weights = columnWeights ?: contentWidths.map { it.toDouble() }
        val weightSum = weights.sum()
        val widths = IntArray(count) { (available * weights[it] / weightSum).toInt().coerceAtLeast(1) }

        var remaining = available - widths.sum()
        var cursor = 0
        while (remaining > 0) {
            widths[cursor++ % count]++
            remaining--
        }
        return widths
    }
}
