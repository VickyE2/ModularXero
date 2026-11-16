package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

interface ColorProvider {
    /**
     * Should return an ansi color
     */
    fun get(position: Float, lineNumber: Int): String
}

fun ColorProvider.applyToString(
    lineNumber: Int,
    data: String
): String {
    if (data.isEmpty()) return data

    val ansiRegex = Regex("""\u001B\[[0-9;]*[a-zA-Z]""")

    // Calculate visible character count.
    val visibleLength = ansiRegex
        .replace(data, "")
        .count { it != '∯' }

    val maxVisiblePosition = (visibleLength - 1).coerceAtLeast(1)

    val result = StringBuilder(data.length * 10)

    var visibleIndex = 0
    var isSkipping = false
    var i = 0

    while (i < data.length) {

        // ANSI escape sequence
        val match = ansiRegex.matchAt(data, i)

        if (match != null) {
            // Copy ANSI directly.
            // It has zero visual width and does not affect the gradient.
            result.append(match.value)

            i = match.range.last + 1
            continue
        }

        val char = data[i]

        // Toggle raw/uncolored region
        if (char == '∯') {
            isSkipping = !isSkipping
            result.append(ANSIColor.RESET)
            i++
            continue
        }

        // Raw/uncolored character
        if (isSkipping) {
            result.append(char)
            visibleIndex++
            i++
            continue
        }

        // Colored visible character
        val position =
            visibleIndex.toFloat() / maxVisiblePosition

        result.append(get(position, lineNumber))
        result.append(char)

        visibleIndex++
        i++
    }

    return result.toString()
}

/**
 * A static color provider that take the [color] parameter as an ansi color
 */
class StaticColorProvider(
    val color: String
) : ColorProvider {
    companion object {
        fun of(color: String): StaticColorProvider =
            StaticColorProvider(color)
    }
    override fun get(position: Float, lineNumber: Int): String = if (color.isHex()) ansiFromHex(color) else color
}

/**
 * This takes the [colors] parameters as hex codes #XXXXXX and not ansi codes
 */
class GradientColorProvider(
    val colors: List<String>,
    val angle: Int,
    val verticalScale: Double = 0.1,
    val interpolation: (Double) -> Double = { x -> x } // for the position given in get
) : ColorProvider {
    override fun get(position: Float, lineNumber: Int): String {
        val x = position.toDouble()
        val y = lineNumber * verticalScale

        val theta = Math.toRadians(angle.toDouble())

        val cos = cos(theta)
        val sin = sin(theta)

        var ratio = x * cos + y * sin

        ratio = ratio.coerceIn(0.0, 1.0)
        ratio = interpolation(ratio).coerceIn(0.0, 1.0)

        return colorAtStop(colors, ratio)
    }
}