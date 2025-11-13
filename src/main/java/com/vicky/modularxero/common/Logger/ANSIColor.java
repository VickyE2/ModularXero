/* Licensed under Apache-2.0 2024. */
package com.vicky.modularxero.common.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is a helper class for making ansi enriched strings in console.
 */
public class ANSIColor {
    // Reset
    public static final String RESET = "\033[0m"; // Text Reset

    // Regular Colors
    public static final String BLACK = "\033[0;30m"; // BLACK
    public static final String RED = "\033[0;31m"; // RED
    public static final String ORANGE = "\033[38;5;208m"; // ORANGE
    public static final String PINK = "\033[38;5;205m"; // PINK
    public static final String GREEN = "\033[0;32m"; // GREEN
    public static final String YELLOW = "\033[0;33m"; // YELLOW
    public static final String BLUE = "\033[0;34m"; // BLUE
    public static final String PURPLE = "\033[0;35m"; // PURPLE
    public static final String CYAN = "\033[0;36m"; // CYAN
    public static final String WHITE = "\033[0;37m"; // WHITE

    // Bold Colors
    public static final String BLACK_BOLD = "\033[1;30m"; // BLACK
    public static final String RED_BOLD = "\033[1;31m"; // RED
    public static final String GREEN_BOLD = "\033[1;32m"; // GREEN
    public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
    public static final String BLUE_BOLD = "\033[1;34m"; // BLUE
    public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
    public static final String CYAN_BOLD = "\033[1;36m"; // CYAN
    public static final String WHITE_BOLD = "\033[1;37m"; // WHITE
    public static final String ORANGE_BOLD = "\033[1;38;5;208m"; // ORANGE
    public static final String PINK_BOLD = "\033[1;38;5;205m"; // PINK

    // High Intensity Colors (Bright)
    public static final String DARK_GRAY = "\033[0;90m"; // DARK GRAY
    public static final String LIGHT_RED = "\033[0;91m"; // LIGHT RED
    public static final String LIGHT_GREEN = "\033[0;92m"; // LIGHT GREEN
    public static final String LIGHT_YELLOW = "\033[0;93m"; // LIGHT YELLOW
    public static final String LIGHT_BLUE = "\033[0;94m"; // LIGHT BLUE
    public static final String LIGHT_PURPLE = "\033[0;95m"; // LIGHT PURPLE
    public static final String LIGHT_CYAN = "\033[0;96m"; // LIGHT CYAN
    public static final String LIGHT_GRAY = "\033[0;97m"; // LIGHT GRAY

    // STYLES
    public static final String BOLD = "\033[1m";
    public static final String ITALIC = "\033[3m";
    public static final String BOLD_ITALIC = "\033[1m\033[3m";
    public static final String UNDERLINE = "\033[4m";
    public static final String STRIKETHROUGH = "\033[9m";

    private static final Map<String, String> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put("black", BLACK);
        COLOR_MAP.put("red", RED);
        COLOR_MAP.put("orange", ORANGE);
        COLOR_MAP.put("pink", PINK);
        COLOR_MAP.put("green", GREEN);
        COLOR_MAP.put("yellow", YELLOW);
        COLOR_MAP.put("blue", BLUE);
        COLOR_MAP.put("purple", PURPLE);
        COLOR_MAP.put("cyan", CYAN);
        COLOR_MAP.put("white", WHITE);
        COLOR_MAP.put("black_bold", BLACK_BOLD);
        COLOR_MAP.put("red_bold", RED_BOLD);
        COLOR_MAP.put("orange_bold", ORANGE_BOLD);
        COLOR_MAP.put("pink_bold", PINK_BOLD);
        COLOR_MAP.put("green_bold", GREEN_BOLD);
        COLOR_MAP.put("yellow_bold", YELLOW_BOLD);
        COLOR_MAP.put("blue_bold", BLUE_BOLD);
        COLOR_MAP.put("purple_bold", PURPLE_BOLD);
        COLOR_MAP.put("cyan_bold", CYAN_BOLD);
        COLOR_MAP.put("white_bold", WHITE_BOLD);
        COLOR_MAP.put("bright_gray", LIGHT_GRAY);
        COLOR_MAP.put("bright_red", LIGHT_RED);
        COLOR_MAP.put("bright_green", LIGHT_GREEN);
        COLOR_MAP.put("bright_yellow", LIGHT_YELLOW);
        COLOR_MAP.put("bright_blue", LIGHT_BLUE);
        COLOR_MAP.put("bright_purple", LIGHT_PURPLE);
        COLOR_MAP.put("bright_cyan", LIGHT_CYAN);
        COLOR_MAP.put("dark_gray", DARK_GRAY);
        COLOR_MAP.put("bold", BOLD);
        COLOR_MAP.put("bold_italic", BOLD_ITALIC);
        COLOR_MAP.put("italic", ITALIC);
        COLOR_MAP.put("underline", UNDERLINE);
        COLOR_MAP.put("strikethrough", STRIKETHROUGH);
    }

    // Regex pattern to match color markers like red[...] or purple[...]
    private static final Pattern COLOR_PATTERN = Pattern.compile("([a-zA-Z_]+)\\[([^\\]]+)\\]");

    // Updated pattern: optional segment length after "rainbow"
    // Format: rainbow[-<length>]-color1-color2-...-colorN[Text]
    // Examples:
    // "rainbow-5-red-blue[HelloWorld]" => segments of 5 characters.
    // "rainbow-red-blue[Hello World]" => defaults to processing word by word.
    private static final Pattern RAINBOW_PATTERN =
        Pattern.compile("^rainbow(?:-(\\d+))?((?:-[a-zA-Z0-9_]+)+)\\[([^\\]]+)\\]$");

    // Combined regex: matches either "rainbow-red-blue-..."
    // or a simple marker like "red" (letters and underscores)
    private static final Pattern GRADIENT_ANGLE_PATTERN =
        Pattern.compile("gradient-(\\d+)deg-((?:#\\p{XDigit}{6}-?)+)\\[(?s)(.*)]");
    private static final Pattern GRADIENT_SIMPLE_PATTERN =
        Pattern.compile("^gradient(?:-([a-z]+))?-(#[A-Fa-f0-9]{6})-(#[A-Fa-f0-9]{6})\\[([^\\]]*)\\]$");
    private static final Pattern MIXED_COLOR_PATTERN =
        Pattern.compile(
            "((?:rainbow(?:-[a-zA-Z0-9_]+)+)|"
                + "(?:gradient(?:-(?:[a-zA-Z]+))?-(?:#[A-Fa-f0-9]{6})-(?:#[A-Fa-f0-9]{6})|"
                + "gradient-\\d+deg-[a-zA-Z]+-(?:#[A-Fa-f0-9]{6}(?:-#[A-Fa-f0-9]{6})+))|"
                + "(?:[a-zA-Z_]+|#[A-Fa-f0-9]{6}))\\[([^\\]]+)\\]");

    /**
     * This is the list of default colors available:
     * <ul>
     * <li><b>Basic Colors:</b> black, red, green, yellow, blur, purple, cyan, white</li>
     * <li><b>Bold Colors:</b> add _bold to the end of the basic colors</li>
     * <li><b>Light Colors:</b> add bright_ to the start of the basic colors except black and white which become bright_grey</li>
     * <li><b>Dark Colors:</b> add dark_ to the start of the basic colors excluding white and black which become dark_ grey</li>
     * <li><b>Styles:</b> bold, bold_italic, italic, strikethrough, underline</li>
     * </ul>
     * <br>
     * @deprecated
     * This method is rather too rigid and dosent support multi colors on a single string without splitting it. I might remove this method later.....might...
     * <p>Use {@link #colorize(String)}</p>
     * @param message This is the string the ansi color is to be applied to
     * @param color The specific ansi color to apply
     * @return Returns an ansi enriched text
     */
    @Deprecated
    public static String colorize(String message, String color) {
        return color + message + RESET;
    }

    /**
     * This is the list of default colors available:
     * <ul>
     * <li><b>Basic Colors:</b> black, red, green, yellow, blur, purple, cyan, white</li>
     * <li><b>Bold Colors:</b> add _bold to the end of the basic colors</li>
     * <li><b>Light Colors:</b> add bright_ to the start of the basic colors except black and white which become bright_grey</li>
     * <li><b>Dark Colors:</b> add dark_ to the start of the basic colors excluding white and black which become dark_ grey</li>
     * <li><b>Styles:</b> bold, bold_italic, italic, strikethrough, underline</li>
     * </ul>
     * To use this you could go:
     * <p style="padding-left: 20px;"><em><b>{@code ('color[style[...message...]] color[...message...] ...')}</b></em></p>
     * <p>styles should come inside the color.</p>
     * @param message This is the string containing all the regex-color-message
     * @return Returns an ansi enriched text
     */
    public static String colorize(String message) {
        while (true) {
            Matcher matcher = COLOR_PATTERN.matcher(message);

            if (!matcher.find()) {
                // Break the loop if no matches are found
                break;
            }

            String color = matcher.group(1).toLowerCase(); // Extract color/style name
            String text = matcher.group(2); // Extract text within the brackets

            // Recursively process the inner text
            String processedText = colorize(text);

            // Look up the ANSI code for the color/style, or default to RESET if unknown
            String colorCode = COLOR_MAP.getOrDefault(color, RESET);

            // Replace the matched segment with the processed content
            message = matcher.replaceFirst(Matcher.quoteReplacement(colorCode + processedText + RESET));
        }

        return message;
    }

    /**
     * Applies a rainbow effect to the text inside a marker.
     * If a segment length is provided (e.g., rainbow-5-red-blue[...]), then the text is divided into segments
     * of that length and each segment is colored. Otherwise, the text is split by whitespace, and each word is colored.
     *
     * @param message the input string in rainbow format
     * @return ANSI enriched string with rainbow effect
     */
    public static String rainbowColorize(String message) {
        Matcher matcher = RAINBOW_PATTERN.matcher(message);
        if (!matcher.matches()) {
            // Not a rainbow format; return original or process normally.
            return message;
        }
        // Group 1: Optional segment length.
        String lengthStr = matcher.group(1);
        // Group 2: Colors part, e.g. "-red-blue-green"
        String colorPart = matcher.group(2);
        // Group 3: Text to be rainbow-fied.
        String text = matcher.group(3);

        // Remove the leading dash and split colors.
        String[] colorNames = colorPart.substring(1).split("-");
        List<String> colorCodes = new ArrayList<>();
        for (String name : colorNames) {
            String code = COLOR_MAP.getOrDefault(name.toLowerCase(), RESET);
            colorCodes.add(code);
        }

        int segLength;
        if (lengthStr != null) {
            segLength = Integer.parseInt(lengthStr);
        } else {
            // Default: Divide the total text length evenly among the colors.
            segLength = (int) Math.ceil((double) text.length() / colorCodes.size());
        }

        StringBuilder sb = new StringBuilder();
        // Process the text in segments of segLength characters.
        for (int i = 0; i < text.length(); i += segLength) {
            int end = Math.min(i + segLength, text.length());
            String segment = text.substring(i, end);
            // Cycle through the colors.
            String code = colorCodes.get((i / segLength) % colorCodes.size());
            sb.append(code).append(segment);
        }
        sb.append(RESET);
        return sb.toString();
    }

    /**
     * Processes a string that may contain standard color markers, rainbow markers, or gradient markers.
     *
     * Supports:
     *   - "red[Hello]"
     *   - "rainbow-blue-green[World]"
     *   - "gradient-#FF0000-#00FF00[Gradient Text]" or "gradient-center-#FF0000-#00FF00[Gradient Text]"
     *
     * @param message The input string with markers.
     * @return The ANSI-enriched string.
     */
    /**
     * Processes a string that may contain simple color markers, rainbow markers, or gradient markers,
     * and returns the string with ANSI color codes applied.
     *
     * Supported formats:
     *   - Simple color: "red[Hello]" or "#AA0000[Hello]"
     *   - Rainbow: "rainbow-5-red-blue[World]"
     *   - Gradient:
     *       * Basic: "gradient-#FF0000-#00FF00[Gradient Text]" (default alignment "left")
     *       * With alignment: "gradient-center-#FF0000-#00FF00[Gradient Text]"
     *       * Manual multi-line: "gradient-45deg-top-#FF0000-#00FF00-#0000FF[Line1\nLine2\nLine3]"
     *
     * @param message The input string with markers.
     * @return The string with ANSI color codes applied.
     */
    public static String colorizeMixed(String message) {
        // Use our combined pattern (which should capture the marker in group(1) and text in group(2))
        Matcher matcher = MIXED_COLOR_PATTERN.matcher(message);
        while (matcher.find()) {
            String fullMarker = matcher.group(0);
            String marker = matcher.group(1);
            String text = matcher.group(2);
            String replacement;
            marker = marker.toLowerCase();
            if (marker.startsWith("rainbow")) {
                replacement = rainbowColorize(fullMarker);
            } else if (marker.startsWith("gradient")) {
                // Use our updated gradientColorize method which handles both basic and manual (angle-based)
                // markers.
                replacement = gradientColorize(fullMarker);
            } else {
                String colorCode = COLOR_MAP.getOrDefault(marker, RESET);
                replacement = colorCode + colorize(text) + RESET;
            }
            message = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            matcher = MIXED_COLOR_PATTERN.matcher(message);
        }
        return message;
    }

    /**
     * This removes all ansi codes from a text. Usually useful for log saving.
     * @param input The string to be cleaned of ansi codes
     * @return This returns the inputted string with no ansi codes for log-saving functionalities
     */
    public static String removeAnsiCodes(String input) {
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    /**
     * Adds a new color to the color map with the corresponding ANSI code.
     *
     * @param colorName The name of the new color (e.g., "orange").
     * @param ansiCode  The ANSI escape sequence for the color (e.g., "\u001B[38;5;214m").
     */
    public void addCustomColor(String colorName, String ansiCode) {
        COLOR_MAP.put(colorName.toLowerCase(), ansiCode);
    }

    /**
     * Returns an ANSI escape sequence corresponding to the given hex color.
     * <p>
     * Note: ANSI does not support true hex colors. This method is a placeholder and may
     * need to map the hex code to one of the supported ANSI colors.
     * </p>
     *
     * @param hex the hex color string (e.g. "#FF0000")
     * @return an ANSI escape code (or fallback) for that color
     */
    private static String ansiFromHex(String hex) {
        // Ensure the hex code starts with '#' and is 7 characters long
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return RESET; // Return default color if invalid
        }

        // Parse RGB values from HEX
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        // ANSI 24-bit (True Color) escape sequence
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    /**
     * Interpolates between two hex colors based on the given ratio.
     *
     * @param startColor the start hex color (e.g. "#FF0000")
     * @param endColor the end hex color (e.g. "#0000FF")
     * @param ratio a value between 0.0 (startColor) and 1.0 (endColor)
     * @return the interpolated hex color string
     */
    private static String interpolateColor(String startColor, String endColor, double ratio) {
        int startR = Integer.parseInt(startColor.substring(1, 3), 16);
        int startG = Integer.parseInt(startColor.substring(3, 5), 16);
        int startB = Integer.parseInt(startColor.substring(5, 7), 16);

        int endR = Integer.parseInt(endColor.substring(1, 3), 16);
        int endG = Integer.parseInt(endColor.substring(3, 5), 16);
        int endB = Integer.parseInt(endColor.substring(5, 7), 16);

        int newR = (int) (startR + ratio * (endR - startR));
        int newG = (int) (startG + ratio * (endG - startG));
        int newB = (int) (startB + ratio * (endB - startB));

        return String.format("#%02X%02X%02X", newR, newG, newB);
    }

    /**
     * Applies a gradient effect to text based on a gradient marker.
     * Supported formats:
     *   gradient-#FF0000-#00FF00[Text] (default alignment "left")
     *   gradient-center-#FF0000-#00FF00[Text]
     *   gradient-45deg-top-#FF0000-#00FF00-#0000FF[Line1\nLine2\nLine3]
     *
     * @param marker The full gradient marker string.
     * @return The string with a gradient applied.
     */
    public static String gradientColorize(String marker) {
        if (!marker.contains("gradient-")) {
            return colorize(marker);
        }
        if (marker.contains("deg")) {
            return gradientAngle(marker);
        }
        return gradientSimple(marker);
    }

    /**
     * Angle‑based multi‑line gradient: gradient-45deg-#FF0000-#00FF00-#0000FF[Line1\nLine2]
     */
    private static String gradientAngle(String marker) {
        marker = marker.replaceFirst("^\\n", "");
        Matcher m = GRADIENT_ANGLE_PATTERN.matcher(marker);
        if (!m.matches()) {
            return marker;
        };

        int angleDeg   = Integer.parseInt(m.group(1));
        List<String> stops = Arrays.asList(m.group(2).split("-"));
        String group3 = m.group(3);
        if (group3.startsWith("\n")) {
            group3 = group3.substring(1); // ONLY removes the literal \n
        }
        String[] lines = group3.split("\n", -1);

        if (stops.size() < 2 || lines.length == 0) return marker;

        double theta = Math.toRadians(angleDeg);
        double cos = Math.cos(theta), sin = Math.sin(theta);

        int rows = lines.length;
        int cols = Arrays.stream(lines).mapToInt(String::length).max().orElse(1);
        double maxProj = cols*cos + (rows - 1)*sin;

        StringBuilder out = new StringBuilder();
        for (int y = 0; y < rows; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                double proj = (x * cos + y * sin) / maxProj;
                proj = Math.max(0, Math.min(proj, 1));
                out.append(colorAtStop(stops, proj))
                    .append(line.charAt(x));
            }
            out.append(RESET);
            if (y < rows - 1) out.append("\n");
        }
        return out.toString();
    }

    /**
     * Simple gradient: gradient[-alignment]-#start-#end[Text]
     * alignment can be left|right|center|top|bottom
     */
    private static String gradientSimple(String marker) {
        Matcher m = GRADIENT_SIMPLE_PATTERN.matcher(marker);
        if (!m.matches()) return marker;

        String align   = Optional.ofNullable(m.group(1)).orElse("left").toLowerCase();
        String start   = m.group(2);
        String end     = m.group(3);
        String content = m.group(4);

        boolean vertical = align.equals("top") || align.equals("bottom");
        String[] lines   = content.split("\n", -1);

        return vertical
            ? verticalGradient(lines, align, start, end)
            : horizontalGradient(lines, align, start, end);
    }

    private static String verticalGradient(String[] lines, String align, String c1, String c2) {
        StringBuilder sb = new StringBuilder();
        int n = lines.length;
        for (int i = 0; i < n; i++) {
            double t = (double)i / (n - 1);
            if (align.equals("bottom")) t = 1 - t;
            sb.append(ansiFromHex(interpolateColor(c1, c2, t)))
                .append(lines[i])
                .append(RESET);
            if (i < n - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private static String horizontalGradient(String[] lines, String align, String c1, String c2) {
        return Arrays.stream(lines)
            .map(line -> {
                int len = line.length();
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    double t = (double)i / (len - 1);
                    switch (align) {
                        case "right":  t = 1 - t; break;
                        case "center": /* same as left */; break;
                    }
                    row.append(ansiFromHex(interpolateColor(c1, c2, t)))
                        .append(line.charAt(i));
                }
                return row.append(RESET).toString();
            })
            .collect(Collectors.joining("\n"));
    }

    /**
     * Given a list of color‑stops and a ratio [0..1], pick the correct segment
     * and interpolate between its two endpoints.
     */
    private static String colorAtStop(List<String> stops, double ratio) {
        int segments = stops.size() - 1;
        double step = 1.0 / segments;
        int idx = Math.min((int)(ratio / step), segments - 1);
        double local = (ratio - idx * step) / step;
        return ansiFromHex(interpolateColor(stops.get(idx), stops.get(idx + 1), local));
    }
}
