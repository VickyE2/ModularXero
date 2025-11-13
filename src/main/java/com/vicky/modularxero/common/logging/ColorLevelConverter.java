package com.vicky.modularxero.common.logging;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public class ColorLevelConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        Level level = event.getLevel();
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "91";  // Light Red
            case Level.WARN_INT  -> "93";  // Yellow
            case Level.INFO_INT  -> "96";  // Cyan
            case Level.TRACE_INT -> "95";  // Purple
            case Level.DEBUG_INT -> "0";   // Black/Disabled (still required for fallback)
            default              -> ANSIConstants.DEFAULT_FG;
        };
    }
}