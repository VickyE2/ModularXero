package com.vicky.modularxero.common

import com.vicky.modularxero.common.Logger.ANSIColor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

enum class LoadingMode { ROUND, LINE }

enum class ProgressTextMode { OUTSIDE, INSIDE }

class LoadingIndicatorElement(
    label: String = "Loading",
    val mode: LoadingMode = LoadingMode.ROUND,
    private val intervalMillis: Long = 100L,
    private val framesRound: List<String> = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"),
    private val framesLine: List<String> = listOf("-", "\\", "|", "/"),
) : TerminalElement() {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "terminal-ui-loading").apply { isDaemon = true }
    }
    private var future: ScheduledFuture<*>? = null
    private var index = 0
    private val originalText = label
    private var text = label
    private var running = AtomicBoolean(false)
    private var completed = false

    init {
        require(intervalMillis >= 20)
        start()
    }

    @Synchronized
    fun update(label: (String, String) -> String = { _, _ -> text }): LoadingIndicatorElement {
        check(!completed) { "Loading indicator is complete" }
        text = label.invoke(originalText, text)
        changed()
        return this
    }

    fun tick(label: String? = null): LoadingIndicatorElement {
        synchronized(this) {
            check(!completed)
            if (label != null) text = label
            index++
            changed()
        }
        return this
    }

    fun start(): LoadingIndicatorElement {
        synchronized(this) {
            check(!completed)
            if (running.getAndSet(true)) return this
            future = executor.scheduleAtFixedRate({
                synchronized(this) {
                    if (!completed) {
                        index = (index + 1) % currentFrames().size
                        changed()
                    }
                }
            }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
        }
        return this
    }

    /** Stop animation, freeze the final state and make the element unupdateable. */
    @Synchronized
    fun complete(finalLabel: String? = null): LoadingIndicatorElement {
        if (completed) return this
        completed = true
        if (finalLabel != null) text = finalLabel
        future?.cancel(false)
        future = null
        running.set(false)
        changed()
        executor.shutdownNow()
        return this
    }

    override fun render(): List<String> {
        val frame = if (!completed) currentFrames()[index % currentFrames().size] else "✓"
        return listOf("$frame $text")
    }

    private fun currentFrames() = if (mode == LoadingMode.ROUND) framesRound else framesLine
}

class ProgressBarElementBuilder {

    private var progress: Double = 0.0
    private var width: Int = 30
    private var fillMaxWidth: Boolean = false
    private var textMode: ProgressTextMode = ProgressTextMode.OUTSIDE
    private var text: String = ""

    private var borderColor: String = ANSIColor.DARK_GRAY
    private var filledColor: ColorProvider =
        StaticColorProvider(ANSIColor.GREEN)
    private var unFilledColor: ColorProvider =
        StaticColorProvider(ANSIColor.DARK_GRAY)

    private var textColor: ColorProvider =
        StaticColorProvider(ANSIColor.WHITE)

    private var borderThickness: Int = 1
    private var insidePadding: Int = 1

    private var inProgressCharacter: Char = '█'
    private var outOfProgressCharacter: Char = ' '


    fun progress(progress: Double): ProgressBarElementBuilder = apply {
        this.progress = progress
    }

    fun width(width: Int): ProgressBarElementBuilder = apply {
        this.width = width
    }

    fun fillMaxWidth(): ProgressBarElementBuilder = apply {
        this.fillMaxWidth = true
    }

    fun textMode(mode: ProgressTextMode): ProgressBarElementBuilder = apply {
        this.textMode = mode
    }

    fun text(text: String): ProgressBarElementBuilder = apply {
        this.text = text
    }

    fun borderColor(color: String): ProgressBarElementBuilder = apply {
        this.borderColor = color
    }

    fun filledColor(color: ColorProvider): ProgressBarElementBuilder = apply {
        this.filledColor = color
    }

    fun unFilledColor(color: ColorProvider): ProgressBarElementBuilder = apply {
        this.unFilledColor = color
    }

    fun textColor(color: ColorProvider): ProgressBarElementBuilder = apply {
        this.textColor = color
    }

    fun borderThickness(thickness: Int): ProgressBarElementBuilder = apply {
        this.borderThickness = thickness
    }

    fun insidePadding(padding: Int): ProgressBarElementBuilder = apply {
        this.insidePadding = padding
    }

    fun inProgressCharacter(character: Char): ProgressBarElementBuilder = apply {
        this.inProgressCharacter = character
    }

    fun outOfProgressCharacter(character: Char): ProgressBarElementBuilder = apply {
        this.outOfProgressCharacter = character
    }


    fun build(): ProgressBarElement {
        require(progress in 0.0..1.0) {
            "progress must be between 0.0 and 1.0"
        }

        require(width >= 1) {
            "width must be at least 1"
        }

        require(borderThickness >= 1) {
            "borderThickness must be at least 1"
        }

        require(insidePadding >= 0) {
            "insidePadding cannot be negative"
        }

        return ProgressBarElement(
            progress = progress,
            width = width,
            fillMaxWidth = fillMaxWidth,
            textMode = textMode,
            text = text,
            borderColor = borderColor,
            filledColor = filledColor,
            unFilledColor = unFilledColor,
            textColor = textColor,
            borderThickness = borderThickness,
            insidePadding = insidePadding,
            inProgressCharacter = inProgressCharacter,
            outOfProgressCharacter = outOfProgressCharacter
        )
    }
}

class ProgressBarElement(
    var progress: Double = 0.0,
    var width: Int = 30,
    var fillMaxWidth: Boolean = false,
    var textMode: ProgressTextMode = ProgressTextMode.OUTSIDE,
    text: String = "",
    var borderColor: String = ANSIColor.DARK_GRAY,
    var filledColor: ColorProvider = StaticColorProvider(ANSIColor.GREEN),
    var unFilledColor: ColorProvider = StaticColorProvider(ANSIColor.DARK_GRAY),
    var textColor: ColorProvider = StaticColorProvider(ANSIColor.WHITE),
    var borderThickness: Int = 1,
    var insidePadding: Int = 1,
    val inProgressCharacter: Char = '█',
    val outOfProgressCharacter: Char = ' ',
) : TerminalElement()
{
    private var suffix = text

    init {
        require(width >= 1)
        require(borderThickness >= 1)
        require(insidePadding >= 0)
    }

    fun update(progress: Double, text: String = suffix): ProgressBarElement = apply {
        this.progress = progress.coerceIn(0.0, 1.0)
        suffix = text
        changed()
    }

    fun progress(progress: Double): ProgressBarElement = apply {
        this.progress = progress.coerceIn(0.0, 1.0)
        changed()
    }

    fun text(text: String): ProgressBarElement = apply {
        suffix = text
        changed()
    }

    override fun render(context: RenderContext): List<String> {
        val pct = kotlin.math.round(progress * 100.0).toInt().coerceIn(0, 100)
        val percentage = "$pct%"
        val inside = textMode == ProgressTextMode.INSIDE
        val desiredLabel = if (suffix.isBlank()) percentage else "$percentage - $suffix"
        val side = borderThickness.coerceIn(1, 20)
        val minimumInsideWidth = desiredLabel.length + insidePadding * 2
        val totalWidth = if (fillMaxWidth) context.availableWidth.coerceAtLeast(1)
            else (width + minimumInsideWidth + side * 2).coerceIn(1, context.availableWidth.coerceAtLeast(1))
        val innerWidth = (totalWidth - (if (inside) 0 else desiredLabel.length) - side * 2).coerceAtLeast(1)
        val fillLength = kotlin.math.round(innerWidth * progress).toInt().coerceIn(0, innerWidth)

        val renderedInner = buildString {
            val labelStart = if (inside) {
                ((innerWidth - desiredLabel.length) / 2)
                    .coerceAtLeast(insidePadding)
            } else {
                -1
            }

            for (cell in 0 until innerWidth) {
                val inProgress = cell < fillLength

                val labelIndex =
                    if (inside) cell - labelStart else -1

                val character = when {
                    inside && labelIndex in desiredLabel.indices ->
                        desiredLabel[labelIndex]

                    inProgress ->
                        inProgressCharacter

                    else ->
                        outOfProgressCharacter
                }

                val color = if (inProgress) {
                    if (character == inProgressCharacter)
                        filledColor.get(cell.toFloat() / innerWidth, 0)
                    else
                        getContrastingFg(
                            fgToBg(
                                filledColor
                                    .get(cell.toFloat() / innerWidth, 0))
                        ) + fgToBg(filledColor.get(cell.toFloat() / innerWidth, 0))
                } else {
                    if (character == outOfProgressCharacter || character == inProgressCharacter)
                        unFilledColor.get(cell.toFloat() / innerWidth, 0)
                    else
                        filledColor.get(cell.toFloat() / innerWidth, 0)
                }

                append(
                    ansiColorize(color, character.toString())
                )
            }
        }

        val bordered = buildString {
            repeat(side) { append(ansiColorize(borderColor, "|")) }
            append(renderedInner)
            repeat(side) { append(ansiColorize(borderColor, "|")) }
        }

        val line = if (inside) {
            // The visible label is included in the inner cells; the extra variable documents the mode.
            bordered
        } else {
            "$bordered " + textColor.applyToString(0,
                percentage + if (suffix.isBlank()) "" else " - $suffix")
        }
        return listOf(line)
    }

    fun fgToBg(ansiCode: String): String {
        return ansiCode.replace(Regex("""(3[0-7]|9[0-7])(?=m)""")) { match ->
            (match.value.toInt() + 10).toString()
        }
    }

    fun getContrastingFg(bgAnsiCode: String): String {
        // Extract the background number (e.g., 42 or 103)
        val match = Regex("""(4[0-7]|10[0-7])""").find(bgAnsiCode)
        val code = match?.value?.toInt() ?: 40

        // Light backgrounds that require dark/black text for legibility:
        // Yellow (43), White (47), Bright Yellow (103), Bright White (107)
        val isLightBackground = code == 43 || code == 47 || code == 103 || code == 107

        return if (isLightBackground) {
            "\u001B[30m"   // Standard Black text
        } else {
            "\u001B[97m"   // Bright White text
        }
    }
}

