package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor

/** Applies either a raw ANSI sequence or one of ANSIColor's named styles. */
internal fun ansiColorize(color: String, content: String): String =
    if (color.startsWith("\u001B[")) color + content + ANSIColor.RESET
    else ANSIColor.colorize("$color[$content]")
