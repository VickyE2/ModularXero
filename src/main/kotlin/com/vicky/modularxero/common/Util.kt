package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor
import kotlin.math.min

fun String.isHex(): Boolean {
    return this.matches("#[A-Fa-f0-9]{6}".toRegex())
}

fun <T: TerminalElement> T.parentTo(parent: BlockElement): T {
    parent.add(this)
    return this
}



fun colorAtStop(colors: List<String>, ratio: Double): String {
    val segments = colors.size - 1
    val step = 1.0 / segments
    val idx = min((ratio / step).toInt(), segments - 1)
    val local = (ratio - idx * step) / step
    return ansiFromHex(interpolateColor(colors[idx], colors[idx + 1], local))
}

fun interpolateColor(startColor: String, endColor: String, ratio: Double): String {
    val startR = startColor.substring(1, 3).toInt(16)
    val startG = startColor.substring(3, 5).toInt(16)
    val startB = startColor.substring(5, 7).toInt(16)

    val endR = endColor.substring(1, 3).toInt(16)
    val endG = endColor.substring(3, 5).toInt(16)
    val endB = endColor.substring(5, 7).toInt(16)

    val newR = (startR + ratio * (endR - startR)).toInt()
    val newG = (startG + ratio * (endG - startG)).toInt()
    val newB = (startB + ratio * (endB - startB)).toInt()

    return String.format("#%02X%02X%02X", newR, newG, newB)
}

fun ansiFromHex(hex: String): String {
    // Ensure the hex code starts with '#' and is 7 characters long
    var hex = hex
    if (hex.startsWith("#")) {
        hex = hex.substring(1)
    }
    if (hex.length != 6) {
        return ANSIColor.RESET // Return default color if invalid
    }

    // Parse RGB values from HEX
    val r = hex.substring(0, 2).toInt(16)
    val g = hex.substring(2, 4).toInt(16)
    val b = hex.substring(4, 6).toInt(16)

    // ANSI 24-bit (True Color) escape sequence
    return String.format("\u001B[38;2;%d;%d;%dm", r, g, b)
}

private val ANSI_REGEX =
    Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*(?:\u0007|\u001B\\))""")
fun String.visibleLength(): Int =
    ANSI_REGEX.replace(this, "").length